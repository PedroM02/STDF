package consensus;

import blockchain.BlockchainService;
import common.Membership;
import common.ProcessId;
import crypto.ThresholdSignatureService;
import links.AuthenticatedPerfectLink;
import links.LinkReceiver;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;
import transaction.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class HotStuffNode implements LinkReceiver {

    private record VoteKey(String blockHash, long view, Phase phase, long configVersion) {
    }

    private static final class ReplicaVoteState {
        private final byte[] nonceShare;
        private final byte[] commitment;
        private boolean partialSent;

        private ReplicaVoteState(byte[] nonceShare, byte[] commitment) {
            this.nonceShare = nonceShare;
            this.commitment = commitment;
        }
    }

    private static final class LeaderVoteSession {
        private final TreeMap<Integer, byte[]> commitments = new TreeMap<>();
        private List<Integer> participants;
        private byte[] aggregatedCommitment;
        private final TreeMap<Integer, byte[]> partialSignatures = new TreeMap<>();
        private boolean requestBroadcast;
        private boolean qcFormed;
    }

    private final int intId;
    private final int n;
    private final int quorumSize;
    private final Membership membership;
    private final AuthenticatedPerfectLink link;
    private final BlockchainService ledger;
    private final Consumer<String> onDecide;
    private final long viewTimeoutMs;
    private final long configVersion;
    private final ThresholdSignatureService thresholdSignatureService;

    private long currentView = 1;
    private Block lockedBlock = null;
    private QuorumCertificate prepareQC = null;

    private final Map<VoteKey, LeaderVoteSession> leaderVoteSessions = new HashMap<>();
    private final Map<VoteKey, ReplicaVoteState> replicaVoteStates = new HashMap<>();
    private final Map<String, Block> blocksByHash = new HashMap<>();
    private final Map<Long, List<HotStuffMessage>> newViewByView = new HashMap<>();

    private final BlockingQueue<Transaction> pendingValues = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService timerPool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hotstuff-timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> viewTimer;

    public HotStuffNode(
            int intId,
            int n,
            Membership membership,
            AuthenticatedPerfectLink link,
            BlockchainService ledger,
            Consumer<String> onDecide,
            long viewTimeoutMs,
            long configVersion,
            ThresholdSignatureService thresholdSignatureService
    ) {
        this.intId = intId;
        this.n = n;
        this.quorumSize = 2 * ((n - 1) / 3) + 1;
        this.membership = membership;
        this.link = link;
        this.ledger = ledger;
        this.onDecide = onDecide;
        this.viewTimeoutMs = viewTimeoutMs;
        this.configVersion = configVersion;
        this.thresholdSignatureService = thresholdSignatureService;

        link.setReceiver(this);
    }

    public HotStuffNode(
            int intId,
            int n,
            Membership membership,
            AuthenticatedPerfectLink link,
            BlockchainService ledger,
            Consumer<String> onDecide,
            ThresholdSignatureService thresholdSignatureService
    ) {
        this(intId, n, membership, link, ledger, onDecide, 5000, 1L, thresholdSignatureService);
    }

    public synchronized void start() {
        HotStuffMessage newViewMsg = new HotStuffMessage(
                MessageType.HOTSTUFF_NEW_VIEW, currentView, null, prepareQC, intId);
        if (isLeader(currentView)) {
            newViewByView.computeIfAbsent(currentView, k -> new ArrayList<>())
                    .add(newViewMsg);
            tryFormNewViewQuorum(currentView);
        }
        sendToLeader(newViewMsg, currentView);
        resetViewTimer();
    }

    public void stop() {
        timerPool.shutdownNow();
        link.stop();
    }

    public synchronized void submitValue(Transaction tx) {
        pendingValues.add(tx);
        if (isLeader(currentView)) {
            tryStartRound();
        }
    }

    @Override
    public synchronized void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId) {
        if (payload instanceof HotStuffMessage msg) {
            if (msg.getType() != MessageType.HOTSTUFF_NEW_VIEW && msg.getViewNumber() < currentView) return;
            switch (msg.getType()) {
                case HOTSTUFF_PREPARE -> onPrepare(msg);
                case HOTSTUFF_PRE_COMMIT -> onPreCommit(msg);
                case HOTSTUFF_COMMIT -> onCommit(msg);
                case HOTSTUFF_DECIDE -> onDecideMsg(msg);
                case HOTSTUFF_NEW_VIEW -> onNewView(msg);
                default -> {
                }
            }
            return;
        }

        if (payload instanceof ThresholdVoteCommitment commitment) {
            if (!matchesAuthenticatedSender(from, commitment.getSenderId())) return;
            onThresholdVoteCommitment(commitment);
            return;
        }

        if (payload instanceof ThresholdSignatureRequest request) {
            if (!matchesAuthenticatedSender(from, request.getLeaderId())) return;
            onThresholdSignatureRequest(request);
            return;
        }

        if (payload instanceof ThresholdPartialSignature partialSignature) {
            if (!matchesAuthenticatedSender(from, partialSignature.getSenderId())) return;
            onThresholdPartialSignature(partialSignature);
        }
    }

    private void tryStartRound() {
        if (pendingValues.isEmpty()) return;

        List<Transaction> txs = new ArrayList<>();
        pendingValues.drainTo(txs);

        String parentHash = prepareQC != null ? prepareQC.getBlockHash() : "GENESIS";
        Block block = new Block(parentHash, txs, currentView, intId);
        blocksByHash.put(block.getHash(), block);
        log("PREPARE view=" + currentView + " cmd=" + txs.size());
        broadcast(new HotStuffMessage(
                MessageType.HOTSTUFF_PREPARE,
                currentView,
                block,
                prepareQC,
                intId
        ));
    }

    private void onPrepare(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;

        Block block = msg.getBlock();
        blocksByHash.put(block.getHash(), block);
        if (msg.getQc() != null && !thresholdSignatureService.verifyQuorumCertificate(msg.getQc())) return;

        long justifyView = msg.getQc() != null ? msg.getQc().getView() : -1;
        boolean safe = lockedBlock == null
                || block.getParentHash().equals(lockedBlock.getHash())
                || justifyView > getLockedView();
        if (!safe) return;

        log("PREPARE_VOTE view=" + currentView);
        sendThresholdCommitment(block, Phase.PREPARE);
        resetViewTimer();
    }

    private void onPreCommit(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        if (!isPhaseCertificate(msg, Phase.PREPARE)) return;

        blocksByHash.put(msg.getBlock().getHash(), msg.getBlock());
        prepareQC = msg.getQc();
        log("PRE_COMMIT_VOTE view=" + currentView);
        sendThresholdCommitment(msg.getBlock(), Phase.PRE_COMMIT);
        resetViewTimer();
    }

    private void onCommit(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        if (!isPhaseCertificate(msg, Phase.PRE_COMMIT)) return;

        blocksByHash.put(msg.getBlock().getHash(), msg.getBlock());
        lockedBlock = msg.getBlock();
        log("COMMIT_VOTE view=" + currentView);
        sendThresholdCommitment(msg.getBlock(), Phase.COMMIT);
        resetViewTimer();
    }

    private void onDecideMsg(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        if (!isPhaseCertificate(msg, Phase.COMMIT)) return;

        List<Transaction> txs = msg.getBlock().getTransactions();
        log("DECIDED cmd=" + txs.size() + " txs, view=" + currentView);
        for (Transaction tx : txs) {
            ledger.append(tx.toString());
        }
        
        if (onDecide != null) {
            onDecide.accept(msg.getBlock().getHash());
        }
        advanceView();
    }

    private void onThresholdVoteCommitment(ThresholdVoteCommitment msg) {
        if (!isLeader(msg.getView())) return;
        if (msg.getView() != currentView || msg.getConfigVersion() != configVersion) return;

        VoteKey key = new VoteKey(msg.getBlockHash(), msg.getView(), msg.getPhase(), msg.getConfigVersion());
        LeaderVoteSession session = leaderVoteSessions.computeIfAbsent(key, ignored -> new LeaderVoteSession());
        session.commitments.putIfAbsent(msg.getSenderId(), msg.getCommitment());

        if (session.requestBroadcast || session.commitments.size() < quorumSize) {
            return;
        }

        List<Integer> participants = new ArrayList<>(session.commitments.keySet().stream().limit(quorumSize).toList());
        List<byte[]> selectedCommitments = new ArrayList<>();
        for (Integer participant : participants) {
            selectedCommitments.add(session.commitments.get(participant));
        }

        session.participants = List.copyOf(participants);
        session.aggregatedCommitment = thresholdSignatureService.combineCommitments(selectedCommitments);
        session.requestBroadcast = true;

        ThresholdSignatureRequest request = new ThresholdSignatureRequest(
                msg.getBlockHash(),
                msg.getView(),
                msg.getPhase(),
                msg.getConfigVersion(),
                intId,
                session.participants,
                session.aggregatedCommitment
        );
        for (Integer participant : session.participants) {
            sendToReplica(participant, request);
        }
    }

    private void onThresholdSignatureRequest(ThresholdSignatureRequest msg) {
        if (!isLeader(msg.getView(), msg.getLeaderId())) return;
        if (msg.getView() != currentView || msg.getConfigVersion() != configVersion) return;
        if (!msg.getParticipants().contains(intId)) return;

        VoteKey key = new VoteKey(msg.getBlockHash(), msg.getView(), msg.getPhase(), msg.getConfigVersion());
        ReplicaVoteState state = replicaVoteStates.get(key);
        if (state == null || state.partialSent) return;

        TreeSet<Integer> participants = new TreeSet<>(msg.getParticipants());
        byte[] partialSignature = thresholdSignatureService.createPartialSignature(
                intId,
                msg.getBlockHash(),
                msg.getView(),
                msg.getPhase(),
                msg.getConfigVersion(),
                state.nonceShare,
                msg.getAggregatedCommitment(),
                participants
        );
        state.partialSent = true;
        sendToReplica(msg.getLeaderId(), new ThresholdPartialSignature(
                msg.getBlockHash(),
                msg.getView(),
                msg.getPhase(),
                msg.getConfigVersion(),
                intId,
                partialSignature
        ));
    }

    private void onThresholdPartialSignature(ThresholdPartialSignature msg) {
        if (!isLeader(msg.getView())) return;
        if (msg.getView() != currentView || msg.getConfigVersion() != configVersion) return;

        VoteKey key = new VoteKey(msg.getBlockHash(), msg.getView(), msg.getPhase(), msg.getConfigVersion());
        LeaderVoteSession session = leaderVoteSessions.get(key);
        if (session == null || session.participants == null || session.qcFormed) return;
        if (!session.participants.contains(msg.getSenderId())) return;

        session.partialSignatures.putIfAbsent(msg.getSenderId(), msg.getPartialSignature());
        if (session.partialSignatures.size() < quorumSize) {
            return;
        }

        session.qcFormed = true;
        List<byte[]> selectedPartialSignatures = new ArrayList<>();
        for (Integer participant : session.participants) {
            byte[] partialSignature = session.partialSignatures.get(participant);
            if (partialSignature != null) {
                selectedPartialSignatures.add(partialSignature);
            }
            if (selectedPartialSignatures.size() == quorumSize) {
                break;
            }
        }
        if (selectedPartialSignatures.size() < quorumSize) {
            return;
        }

        byte[] aggregatedSignature = thresholdSignatureService.aggregatePartialSignatures(
                session.aggregatedCommitment,
                selectedPartialSignatures
        );
        QuorumCertificate qc = new QuorumCertificate(
                key.blockHash(),
                key.view(),
                key.phase(),
                key.configVersion(),
                quorumSize,
                aggregatedSignature,
                session.participants
        );
        advancePhaseWithQc(qc);
    }

    private void advancePhaseWithQc(QuorumCertificate qc) {
        Block block = blocksByHash.get(qc.getBlockHash());
        if (block == null) {
            return;
        }
        if (!thresholdSignatureService.verifyQuorumCertificate(qc)) return;

        switch (qc.getPhase()) {
            case PREPARE -> {
                prepareQC = qc;
                log("PRE_COMMIT view=" + currentView);
                broadcast(new HotStuffMessage(MessageType.HOTSTUFF_PRE_COMMIT, currentView, block, qc, intId));
                resetViewTimer();
            }
            case PRE_COMMIT -> {
                log("COMMIT view=" + currentView);
                broadcast(new HotStuffMessage(MessageType.HOTSTUFF_COMMIT, currentView, block, qc, intId));
                resetViewTimer();
            }
            case COMMIT -> {
                log("DECIDE view=" + currentView + " txs=" + block.getTransactions().size());
                broadcast(new HotStuffMessage(MessageType.HOTSTUFF_DECIDE, currentView, block, qc, intId));
            }
        }
    }

    private void sendThresholdCommitment(Block block, Phase phase) {
        VoteKey key = new VoteKey(block.getHash(), currentView, phase, configVersion);
        ReplicaVoteState state = replicaVoteStates.get(key);
        if (state == null) {
            ThresholdSignatureService.NonceCommitment commitment = thresholdSignatureService.createNonceCommitment(
                    block.getHash(),
                    currentView,
                    phase,
                    configVersion
            );
            state = new ReplicaVoteState(commitment.nonceShare(), commitment.commitment());
            replicaVoteStates.put(key, state);
        }

        sendToLeader(new ThresholdVoteCommitment(
                block.getHash(),
                currentView,
                phase,
                configVersion,
                intId,
                state.commitment
        ), currentView);
    }

    private void onNewView(HotStuffMessage msg) {
        long targetView = msg.getViewNumber();
        if (!isLeader(targetView, intId)) return;

        List<HotStuffMessage> msgs = newViewByView.computeIfAbsent(targetView, ignored -> new ArrayList<>());
        boolean alreadySeen = msgs.stream().anyMatch(m -> m.getSenderId() == msg.getSenderId());
        if (alreadySeen) return;

        msgs.add(msg);
        log("NEW_VIEW from=" + msg.getSenderId() + " for view=" + targetView
                + " (" + msgs.size() + "/" + quorumSize + ")");
        tryFormNewViewQuorum(targetView);
    }

    private void tryFormNewViewQuorum(long targetView) {
        if (!isLeader(targetView, intId)) return;

        List<HotStuffMessage> msgs = newViewByView.getOrDefault(targetView, List.of());
        if (msgs.size() < quorumSize) return;

        QuorumCertificate highQC = msgs.stream()
                .map(HotStuffMessage::getQc)
                .filter(thresholdSignatureService::verifyQuorumCertificate)
                .max(Comparator.comparingLong(QuorumCertificate::getView))
                .orElse(null);
        if (highQC != null) {
            prepareQC = highQC;
        }
        if (currentView < targetView) {
            currentView = targetView;
            clearRoundState();
        }
        log("NEW_VIEW quorum for view=" + currentView + ", starting round");
        newViewByView.remove(targetView);
        tryStartRound();
    }

    private void viewTimeout() {
        synchronized (this) {
            long nextView = currentView + 1;
            log("TIMEOUT view=" + currentView + " -> moving to view=" + nextView);
            HotStuffMessage newViewMsg = new HotStuffMessage(
                    MessageType.HOTSTUFF_NEW_VIEW,
                    nextView,
                    null,
                    prepareQC,
                    intId
            );
            currentView = nextView;
            clearRoundState();
            if (isLeader(currentView)) {
                log("I am the new leader for view=" + currentView);
                newViewByView.computeIfAbsent(currentView, ignored -> new ArrayList<>()).add(newViewMsg);
                tryFormNewViewQuorum(currentView);
            }
            sendToLeader(newViewMsg, nextView);
            resetViewTimer();
        }
    }

    private void advanceView() {
        currentView++;
        clearRoundState();
        resetViewTimer();
        if (isLeader(currentView)) {
            log("Now leader for view=" + currentView);
            tryStartRound();
        }
    }

    private void clearRoundState() {
        leaderVoteSessions.clear();
        replicaVoteStates.clear();
    }

    private void resetViewTimer() {
        if (viewTimer != null) {
            viewTimer.cancel(false);
        }
        viewTimer = timerPool.schedule(this::viewTimeout, viewTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private boolean isLeader(long view) {
        return isLeader(view, intId);
    }

    private boolean isLeader(long view, int nodeId) {
        return Math.floorMod(view, n) == nodeId;
    }

    private boolean isLeaderOf(HotStuffMessage msg) {
        return isLeader(msg.getViewNumber(), msg.getSenderId()) && msg.getViewNumber() == currentView;
    }

    private int leaderFor(long view) {
        return (int) Math.floorMod(view, n);
    }

    private long getLockedView() {
        return lockedBlock != null ? lockedBlock.getView() : -1;
    }

    private void broadcast(ProtocolMessage msg) {
        for (ProcessId pid : membership.getProcessIds()) {
            link.send(pid, msg);
        }
    }

    private void sendToLeader(ProtocolMessage msg, long view) {
        sendToReplica(leaderFor(view), msg);
    }

    private void sendToReplica(int replicaId, ProtocolMessage msg) {
        try {
            ProcessId pid = membership.getProcessIdByInt(replicaId);
            link.send(pid, msg);
        } catch (IllegalArgumentException e) {
            System.err.println("[Node " + intId + "] Could not find replica " + replicaId);
        }
    }

    private boolean matchesAuthenticatedSender(ProcessId from, int senderId) {
        try {
            return membership.getProcessIdByInt(senderId).equals(from);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isPhaseCertificate(HotStuffMessage msg, Phase expectedPhase) {
        QuorumCertificate qc = msg.getQc();
        return qc != null
                && qc.getPhase() == expectedPhase
                && qc.getBlockHash().equals(msg.getBlock().getHash())
                && qc.getView() == msg.getViewNumber()
                && qc.getConfigVersion() == configVersion
                && thresholdSignatureService.verifyQuorumCertificate(qc);
    }

    private void log(String msg) {
        System.out.println("[Node " + intId + "] " + msg);
    }

    public long getCurrentView() { return currentView; }
    public int getQuorumSize() { return quorumSize; }
    public int getIntId() { return intId; }
}
