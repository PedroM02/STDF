package consensus;

import blockchain.BlockchainService;
import common.Membership;
import common.ProcessId;
import links.AuthenticatedPerfectLink;
import links.LinkReceiver;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;

import crypto.CryptoService;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


public final class HotStuffNode implements LinkReceiver {

    // ---- configuration ----
    private final int intId;
    private final int n;
    private final int quorumSize;
    private final Membership membership;
    private final AuthenticatedPerfectLink link;
    private final BlockchainService ledger;
    private final Consumer<String> onDecide;
    private final long viewTimeoutMs;

    private final CryptoService cryptoService;
    private final Map<Integer, PublicKey> publicKeys;

    private long currentView = 1;
    private Block lockedBlock = null;
    private QuorumCertificate prepareQC = null;

    private final Map<MessageType, Map<String, List<HotStuffMessage>>> votesByPhase
            = new HashMap<>();
    private final Map<Long, List<HotStuffMessage>> newViewByView = new HashMap<>();

    private final BlockingQueue<String> pendingValues = new LinkedBlockingQueue<>();

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
            CryptoService cryptoService,
            Map<Integer, PublicKey> publicKeys
    ) {
        this.intId = intId;
        this.n = n;
        this.quorumSize = 2 * ((n - 1) / 3) + 1;
        this.membership = membership;
        this.link = link;
        this.ledger = ledger;
        this.onDecide = onDecide;
        this.viewTimeoutMs = viewTimeoutMs;

        this.cryptoService = cryptoService;
        this.publicKeys = publicKeys;

        link.setReceiver(this);

        for (MessageType t : List.of(
                MessageType.HOTSTUFF_PREPARE_VOTE,
                MessageType.HOTSTUFF_PRE_COMMIT_VOTE,
                MessageType.HOTSTUFF_COMMIT_VOTE)) {
            votesByPhase.put(t, new HashMap<>());
        }
    }

    public HotStuffNode(int intId, int n, Membership membership,
                        AuthenticatedPerfectLink link, BlockchainService ledger,
                        Consumer<String> onDecide,
                        CryptoService cryptoService,
                        Map<Integer, PublicKey> publicKeys) {
        this(intId, n, membership, link, ledger, onDecide,
             5000, cryptoService, publicKeys);
    }

    public synchronized void start() {
        if (isLeader(currentView)) {
            tryStartRound();
        }
        resetViewTimer();
    }

    public void stop() {
        timerPool.shutdownNow();
        link.stop();
    }

    public synchronized void submitValue(String value) {
        pendingValues.add(value);
        if (isLeader(currentView)) {
            tryStartRound();
        }
    }

    @Override
    public synchronized void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId) {
        if (!(payload instanceof HotStuffMessage msg)) return;

        if (msg.getType() != MessageType.HOTSTUFF_NEW_VIEW
                && msg.getViewNumber() < currentView) return;

        switch (msg.getType()) {
            case HOTSTUFF_PREPARE         -> onPrepare(msg);
            case HOTSTUFF_PREPARE_VOTE    -> onPrepareVote(msg);
            case HOTSTUFF_PRE_COMMIT      -> onPreCommit(msg);
            case HOTSTUFF_PRE_COMMIT_VOTE -> onPreCommitVote(msg);
            case HOTSTUFF_COMMIT          -> onCommit(msg);
            case HOTSTUFF_COMMIT_VOTE     -> onCommitVote(msg);
            case HOTSTUFF_DECIDE          -> onDecideMsg(msg);
            case HOTSTUFF_NEW_VIEW        -> onNewView(msg);
            default -> {}
        }
    }

    private void tryStartRound() {
        String value = pendingValues.poll();
        if (value == null) return;
        String parentHash = (prepareQC != null) ? prepareQC.getBlockHash() : "GENESIS";
        Block block = new Block(parentHash, value, currentView, intId);
        log("PREPARE view=" + currentView + " cmd=" + value);
        broadcast(new HotStuffMessage(
                MessageType.HOTSTUFF_PREPARE, currentView, block, prepareQC, intId));
    }

    private void onPrepareVote(HotStuffMessage vote) {
        if (!isLeader(currentView)) return;
        if (!collectVote(MessageType.HOTSTUFF_PREPARE_VOTE, vote)) return;
        if (voteCount(MessageType.HOTSTUFF_PREPARE_VOTE, vote.getBlock().getHash()) >= quorumSize) {
            prepareQC = new QuorumCertificate(vote.getBlock().getHash(), currentView, Phase.PREPARE, cryptoService, publicKeys);
            log("PRE_COMMIT view=" + currentView);
            broadcast(new HotStuffMessage(
                    MessageType.HOTSTUFF_PRE_COMMIT, currentView, vote.getBlock(), prepareQC, intId));
            resetViewTimer();
        }
    }

    private void onPreCommitVote(HotStuffMessage vote) {
        if (!isLeader(currentView)) return;
        if (!collectVote(MessageType.HOTSTUFF_PRE_COMMIT_VOTE, vote)) return;
        if (voteCount(MessageType.HOTSTUFF_PRE_COMMIT_VOTE, vote.getBlock().getHash()) >= quorumSize) {
            QuorumCertificate qc = new QuorumCertificate(
                    vote.getBlock().getHash(), currentView, Phase.PRE_COMMIT, cryptoService, publicKeys);
            log("COMMIT view=" + currentView);
            broadcast(new HotStuffMessage(
                    MessageType.HOTSTUFF_COMMIT, currentView, vote.getBlock(), qc, intId));
            resetViewTimer();
        }
    }

    private void onCommitVote(HotStuffMessage vote) {
        if (!isLeader(currentView)) return;
        if (!collectVote(MessageType.HOTSTUFF_COMMIT_VOTE, vote)) return;
        if (voteCount(MessageType.HOTSTUFF_COMMIT_VOTE, vote.getBlock().getHash()) >= quorumSize) {
            QuorumCertificate qc = new QuorumCertificate(
                    vote.getBlock().getHash(), currentView, Phase.COMMIT, cryptoService, publicKeys);
            log("DECIDE view=" + currentView + " cmd=" + vote.getBlock().getCommand());
            broadcast(new HotStuffMessage(
                    MessageType.HOTSTUFF_DECIDE, currentView, vote.getBlock(), qc, intId));
        }
    }

    private void onPrepare(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        Block block = msg.getBlock();
        long justifyView = (msg.getQc() != null) ? msg.getQc().getView() : -1;
        boolean safe = lockedBlock == null
                || block.getParentHash().equals(lockedBlock.getHash())
                || justifyView > getLockedView();
        if (safe) {
            log("PREPARE_VOTE view=" + currentView);
            sendToLeader(new HotStuffMessage(
                    MessageType.HOTSTUFF_PREPARE_VOTE, currentView, block, null, intId),
                    currentView);
            resetViewTimer();
        }
    }

    private void onPreCommit(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        if (msg.getQc() != null) prepareQC = msg.getQc();
        log("PRE_COMMIT_VOTE view=" + currentView);
        sendToLeader(new HotStuffMessage(
                MessageType.HOTSTUFF_PRE_COMMIT_VOTE, currentView, msg.getBlock(), null, intId),
                currentView);
        resetViewTimer();
    }

    private void onCommit(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        lockedBlock = msg.getBlock();
        log("COMMIT_VOTE view=" + currentView);
        sendToLeader(new HotStuffMessage(
                MessageType.HOTSTUFF_COMMIT_VOTE, currentView, msg.getBlock(), null, intId),
                currentView);
        resetViewTimer();
    }

    private void onDecideMsg(HotStuffMessage msg) {
        if (!isLeaderOf(msg)) return;
        String command = msg.getBlock().getCommand();
        log("DECIDED cmd=" + command);
        ledger.append(command);
        if (onDecide != null) onDecide.accept(command);
        advanceView();
    }

    private void viewTimeout() {
        synchronized (this) {
            long nextView = currentView + 1;
            log("TIMEOUT view=" + currentView + " -> moving to view=" + nextView);
            HotStuffMessage newViewMsg = new HotStuffMessage(
                    MessageType.HOTSTUFF_NEW_VIEW, nextView, null, prepareQC, intId);
            currentView = nextView;
            clearVotes();
            if (isLeader(currentView)) {
                log("I am the new leader for view=" + currentView);
                newViewByView.computeIfAbsent(currentView, k -> new ArrayList<>())
                        .add(newViewMsg);
                tryFormNewViewQuorum(currentView);
            }
            sendToLeader(newViewMsg, nextView);
            resetViewTimer();
        }
    }

    private void onNewView(HotStuffMessage msg) {
        long targetView = msg.getViewNumber();
        if (!isLeader(targetView, intId)) return;
        List<HotStuffMessage> msgs = newViewByView
                .computeIfAbsent(targetView, k -> new ArrayList<>());
        boolean alreadySeen = msgs.stream()
                .anyMatch(m -> m.getSenderId() == msg.getSenderId());
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
                .filter(Objects::nonNull)
                .max(Comparator.comparingLong(QuorumCertificate::getView))
                .orElse(null);
        if (highQC != null) prepareQC = highQC;
        if (currentView < targetView) {
            currentView = targetView;
            clearVotes();
        }
        log("NEW_VIEW quorum for view=" + currentView + ", starting round");
        newViewByView.remove(targetView);
        tryStartRound();
    }

    private void advanceView() {
        currentView++;
        clearVotes();
        resetViewTimer();
        if (isLeader(currentView)) {
            log("Now leader for view=" + currentView);
            tryStartRound();
        }
    }

    private void resetViewTimer() {
        if (viewTimer != null) viewTimer.cancel(false);
        viewTimer = timerPool.schedule(this::viewTimeout, viewTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private boolean isLeader(long view) { return isLeader(view, intId); }

    private boolean isLeader(long view, int nodeId) {
        return Math.floorMod(view, n) == nodeId;
    }

    private boolean isLeaderOf(HotStuffMessage msg) {
        return isLeader(msg.getViewNumber(), msg.getSenderId())
                && msg.getViewNumber() == currentView;
    }

    private int leaderFor(long view) { return (int) Math.floorMod(view, n); }

    private long getLockedView() { return lockedBlock != null ? lockedBlock.getView() : -1; }

    private void broadcast(HotStuffMessage msg) {
        for (ProcessId pid : membership.getProcessIds()) {
            link.send(pid, msg);
        }
    }

    private void sendToLeader(HotStuffMessage msg, long view) {
        int leaderId = leaderFor(view);
        try {
            ProcessId leaderPid = membership.getProcessIdByInt(leaderId);
            link.send(leaderPid, msg);
        } catch (IllegalArgumentException e) {
            System.err.println("[Node " + intId + "] Could not find leader " + leaderId);
        }
    }

    private boolean collectVote(MessageType phase, HotStuffMessage vote) {
        List<HotStuffMessage> votes = votesByPhase.get(phase)
                .computeIfAbsent(vote.getBlock().getHash(), k -> new ArrayList<>());
        boolean alreadyVoted = votes.stream()
                .anyMatch(v -> v.getSenderId() == vote.getSenderId());
        if (alreadyVoted) return false;
        votes.add(vote);
        return true;
    }

    private int voteCount(MessageType phase, String blockHash) {
        return votesByPhase.getOrDefault(phase, Map.of())
                .getOrDefault(blockHash, List.of()).size();
    }

    private void clearVotes() { votesByPhase.values().forEach(Map::clear); }

    private void log(String msg) { System.out.println("[Node " + intId + "] " + msg); }

    public long getCurrentView() { return currentView; }
    public int getQuorumSize() { return quorumSize; }
    public int getIntId() { return intId; }
}