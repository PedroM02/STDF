package consensus;

import messages.MessageType;

import java.util.*;
import java.util.concurrent.*;

public class ViewChangeSimulator {

    static class MessageBus {
        private final List<InMemoryReplica> replicas = new ArrayList<>();
        private final Set<Integer> crashedNodes = ConcurrentHashMap.newKeySet();
        private final ExecutorService deliveryPool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "msg-delivery");
            t.setDaemon(true);
            return t;
        });

        void register(InMemoryReplica r) { replicas.add(r); }

        void crash(int nodeId) {
            crashedNodes.add(nodeId);
            System.out.println("  *** Node " + nodeId + " CRASHED ***");
        }

        void send(int fromId, int toId, HotStuffMessage msg) {
            if (crashedNodes.contains(fromId)) return;
            if (crashedNodes.contains(toId)) return;
            for (InMemoryReplica r : replicas) {
                if (r.id == toId) {
                    deliveryPool.submit(() -> r.deliver(msg));
                    return;
                }
            }
        }

        void broadcast(int fromId, HotStuffMessage msg) {
            for (InMemoryReplica r : replicas) send(fromId, r.id, msg);
        }

        void shutdown() { deliveryPool.shutdownNow(); }
    }

    static class InMemoryReplica {
        final int id;
        final int n;
        final int quorumSize;
        final MessageBus bus;
        final long timeoutMs;

        long currentView = 1;
        Block lockedBlock = null;
        QuorumCertificate prepareQC = null;
        final Map<String, Map<String, List<Integer>>> votes = new HashMap<>();
        final Map<Long, List<HotStuffMessage>> newViews = new HashMap<>();
        final List<String> decided = new CopyOnWriteArrayList<>();
        final Queue<String> pending = new LinkedList<>();

        final ScheduledExecutorService timerPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer");
            t.setDaemon(true);
            return t;
        });
        ScheduledFuture<?> viewTimer;

        InMemoryReplica(int id, int n, long timeoutMs, MessageBus bus) {
            this.id = id;
            this.n = n;
            this.quorumSize = 2 * ((n - 1) / 3) + 1;
            this.timeoutMs = timeoutMs;
            this.bus = bus;
        }

        void start() {
            resetTimer();
            if (isLeader(currentView)) {
                String value;
                synchronized (this) { value = pending.poll(); }
                if (value != null) {
                    String parentHash = prepareQC != null ? prepareQC.getBlockHash() : "GENESIS";
                    Block block = new Block(parentHash, value, currentView, id);
                    log("PREPARE view=" + currentView + " cmd=" + value);
                    bus.broadcast(id, new HotStuffMessage(
                            MessageType.HOTSTUFF_PREPARE, currentView, block, prepareQC, id));
                }
            }
        }

        void stop() { timerPool.shutdownNow(); }

        synchronized void submitValue(String value) { pending.add(value); }

        synchronized void deliver(HotStuffMessage msg) {
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

        private void tryPropose() {
            String value = pending.poll();
            if (value == null) return;
            String parentHash = prepareQC != null ? prepareQC.getBlockHash() : "GENESIS";
            Block block = new Block(parentHash, value, currentView, id);
            log("PREPARE view=" + currentView + " cmd=" + value);
            HotStuffMessage msg = new HotStuffMessage(
                    MessageType.HOTSTUFF_PREPARE, currentView, block, prepareQC, id);
            scheduleOutside(() -> bus.broadcast(id, msg));
        }

        private void scheduleOutside(Runnable action) {
            bus.deliveryPool.submit(action);
        }

        private void onPrepareVote(HotStuffMessage msg) {
            if (!isLeader(currentView)) return;
            if (!addVote("PREPARE", msg.getBlock().getHash(), msg.getSenderId())) return;
            if (voteCount("PREPARE", msg.getBlock().getHash()) >= quorumSize) {
                prepareQC = new QuorumCertificate(msg.getBlock().getHash(), currentView, Phase.PREPARE);
                log("PRE_COMMIT view=" + currentView);
                HotStuffMessage out = new HotStuffMessage(
                        MessageType.HOTSTUFF_PRE_COMMIT, currentView, msg.getBlock(), prepareQC, id);
                resetTimer();
                scheduleOutside(() -> bus.broadcast(id, out));
            }
        }

        private void onPreCommitVote(HotStuffMessage msg) {
            if (!isLeader(currentView)) return;
            if (!addVote("PRE_COMMIT", msg.getBlock().getHash(), msg.getSenderId())) return;
            if (voteCount("PRE_COMMIT", msg.getBlock().getHash()) >= quorumSize) {
                QuorumCertificate qc = new QuorumCertificate(
                        msg.getBlock().getHash(), currentView, Phase.PRE_COMMIT);
                log("COMMIT view=" + currentView);
                HotStuffMessage out = new HotStuffMessage(
                        MessageType.HOTSTUFF_COMMIT, currentView, msg.getBlock(), qc, id);
                resetTimer();
                scheduleOutside(() -> bus.broadcast(id, out));
            }
        }

        private void onCommitVote(HotStuffMessage msg) {
            if (!isLeader(currentView)) return;
            if (!addVote("COMMIT", msg.getBlock().getHash(), msg.getSenderId())) return;
            if (voteCount("COMMIT", msg.getBlock().getHash()) >= quorumSize) {
                QuorumCertificate qc = new QuorumCertificate(
                        msg.getBlock().getHash(), currentView, Phase.COMMIT);
                log("DECIDE view=" + currentView + " cmd=" + msg.getBlock().getCommand());
                HotStuffMessage out = new HotStuffMessage(
                        MessageType.HOTSTUFF_DECIDE, currentView, msg.getBlock(), qc, id);
                scheduleOutside(() -> bus.broadcast(id, out));
            }
        }

        private void onPrepare(HotStuffMessage msg) {
            if (!isLeaderMsg(msg)) return;
            Block block = msg.getBlock();
            long justifyView = msg.getQc() != null ? msg.getQc().getView() : -1;
            boolean safe = lockedBlock == null
                    || block.getParentHash().equals(lockedBlock.getHash())
                    || justifyView > getLockedView();
            if (safe) {
                log("PREPARE_VOTE view=" + currentView);
                HotStuffMessage vote = new HotStuffMessage(
                        MessageType.HOTSTUFF_PREPARE_VOTE, currentView, block, null, id);
                resetTimer();
                final long v = currentView;
                scheduleOutside(() -> bus.send(id, (int) Math.floorMod(v, n), vote));
            }
        }

        private void onPreCommit(HotStuffMessage msg) {
            if (!isLeaderMsg(msg)) return;
            if (msg.getQc() != null) prepareQC = msg.getQc();
            log("PRE_COMMIT_VOTE view=" + currentView);
            HotStuffMessage vote = new HotStuffMessage(
                    MessageType.HOTSTUFF_PRE_COMMIT_VOTE, currentView, msg.getBlock(), null, id);
            resetTimer();
            final long v = currentView;
            scheduleOutside(() -> bus.send(id, (int) Math.floorMod(v, n), vote));
        }

        private void onCommit(HotStuffMessage msg) {
            if (!isLeaderMsg(msg)) return;
            lockedBlock = msg.getBlock();
            log("COMMIT_VOTE view=" + currentView);
            HotStuffMessage vote = new HotStuffMessage(
                    MessageType.HOTSTUFF_COMMIT_VOTE, currentView, msg.getBlock(), null, id);
            resetTimer();
            final long v = currentView;
            scheduleOutside(() -> bus.send(id, (int) Math.floorMod(v, n), vote));
        }

        private void onDecideMsg(HotStuffMessage msg) {
            if (!isLeaderMsg(msg)) return;
            String cmd = msg.getBlock().getCommand();
            log("DECIDED cmd=" + cmd);
            decided.add(cmd);
            advanceView();
        }

        private void viewTimeout() {
            synchronized (this) {
                long nextView = currentView + 1;
                log("TIMEOUT view=" + currentView + " -> next view=" + nextView);
                HotStuffMessage nv = new HotStuffMessage(
                        MessageType.HOTSTUFF_NEW_VIEW, nextView, null, prepareQC, id);
                currentView = nextView;
                votes.clear();
                if (isLeader(currentView)) {
                    newViews.computeIfAbsent(currentView, k -> new ArrayList<>()).add(nv);
                    tryFormNewViewQuorum(currentView);
                }
                resetTimer();
                scheduleOutside(() -> bus.send(id, (int) Math.floorMod(nextView, n), nv));
            }
        }

        private void onNewView(HotStuffMessage msg) {
            long targetView = msg.getViewNumber();
            if (!isLeader(targetView)) return;
            List<HotStuffMessage> msgs = newViews.computeIfAbsent(targetView, k -> new ArrayList<>());
            if (msgs.stream().anyMatch(m -> m.getSenderId() == msg.getSenderId())) return;
            msgs.add(msg);
            log("NEW_VIEW from=" + msg.getSenderId() + " for view=" + targetView
                    + " (" + msgs.size() + "/" + quorumSize + ")");
            tryFormNewViewQuorum(targetView);
        }

        private void tryFormNewViewQuorum(long targetView) {
            if (!isLeader(targetView)) return;
            List<HotStuffMessage> msgs = newViews.getOrDefault(targetView, List.of());
            if (msgs.size() < quorumSize) return;
            QuorumCertificate highQC = msgs.stream()
                    .map(HotStuffMessage::getQc)
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingLong(QuorumCertificate::getView))
                    .orElse(null);
            if (highQC != null) prepareQC = highQC;
            if (currentView < targetView) { currentView = targetView; votes.clear(); }
            log("NEW_VIEW quorum for view=" + currentView + ", starting round");
            newViews.remove(targetView);
            tryPropose();
        }

        private void advanceView() {
            currentView++;
            votes.clear();
            resetTimer();
            if (isLeader(currentView)) {
                log("Now leader for view=" + currentView);
                tryPropose();
            }
        }

        private void resetTimer() {
            if (viewTimer != null) viewTimer.cancel(false);
            viewTimer = timerPool.schedule(this::viewTimeout, timeoutMs, TimeUnit.MILLISECONDS);
        }

        private boolean isLeader(long view) { return Math.floorMod(view, n) == id; }

        private boolean isLeaderMsg(HotStuffMessage msg) {
            return Math.floorMod(msg.getViewNumber(), n) == msg.getSenderId()
                    && msg.getViewNumber() == currentView;
        }

        private long getLockedView() { return lockedBlock != null ? lockedBlock.getView() : -1; }

        private boolean addVote(String phase, String blockHash, int voterId) {
            List<Integer> voters = votes
                    .computeIfAbsent(phase, k -> new HashMap<>())
                    .computeIfAbsent(blockHash, k -> new ArrayList<>());
            if (voters.contains(voterId)) return false;
            voters.add(voterId);
            return true;
        }

        private int voteCount(String phase, String blockHash) {
            return votes.getOrDefault(phase, Map.of())
                    .getOrDefault(blockHash, List.of()).size();
        }

        private void log(String msg) { System.out.println("  [Node " + id + "] " + msg); }
    }

    static List<InMemoryReplica> buildCluster(int n, long timeoutMs) {
        MessageBus bus = new MessageBus();
        List<InMemoryReplica> replicas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            InMemoryReplica r = new InMemoryReplica(i, n, timeoutMs, bus);
            replicas.add(r);
            bus.register(r);
        }
        return replicas;
    }

    static boolean waitForDecision(List<InMemoryReplica> replicas, String value,
                                    int minNodes, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (replicas.stream().filter(r -> r.decided.contains(value)).count() >= minNodes)
                return true;
            Thread.sleep(50);
        }
        return false;
    }

    static void stopAll(List<InMemoryReplica> replicas) {
        replicas.forEach(InMemoryReplica::stop);
        replicas.get(0).bus.shutdown();
    }

    static void testNormalRound() throws InterruptedException {
        System.out.println("\n=== TEST 1: Normal round (no crashes) ===");
        List<InMemoryReplica> replicas = buildCluster(4, 10_000);
        replicas.get(1).submitValue("hello");
        replicas.forEach(InMemoryReplica::start);
        boolean decided = waitForDecision(replicas, "hello", 4, 5000);
        stopAll(replicas);
        if (decided) System.out.println("PASS: All nodes decided 'hello'");
        else { System.out.println("FAIL: Not all nodes decided in time"); System.exit(1); }
    }

    static void testLeaderCrash() throws InterruptedException {
        System.out.println("\n=== TEST 2: Leader crash (node 0 crashes, node 1 takes over) ===");
        List<InMemoryReplica> replicas = buildCluster(4, 800);
        replicas.get(0).bus.crash(0);
        replicas.get(1).submitValue("after-crash");
        for (int i = 1; i < 4; i++) replicas.get(i).start();
        boolean decided = waitForDecision(replicas, "after-crash", 3, 10_000);
        stopAll(replicas);
        if (decided) System.out.println("PASS: Surviving nodes decided 'after-crash' after view change");
        else { System.out.println("FAIL: Nodes did not decide after leader crash"); System.exit(1); }
    }

    static void testTwoConsecutiveCrashes() throws InterruptedException {
        System.out.println("\n=== TEST 3: Two crashes exceed f=1, system stalls ===");
        List<InMemoryReplica> replicas = buildCluster(4, 800);
        replicas.get(0).bus.crash(0);
        replicas.get(0).bus.crash(1);
        replicas.get(2).submitValue("should-not-decide");
        for (int i = 2; i < 4; i++) replicas.get(i).start();
        boolean decided = waitForDecision(replicas, "should-not-decide", 2, 4000);
        stopAll(replicas);
        if (!decided) System.out.println("PASS: System correctly stalled (2 crashes exceed f=1)");
        else { System.out.println("FAIL: Decided with only 2 nodes alive (safety violation!)"); System.exit(1); }
    }

    static void testNonLeaderCrash() throws InterruptedException {
        System.out.println("\n=== TEST 4: Non-leader crash, 3 survivors still decide ===");
        List<InMemoryReplica> replicas = buildCluster(4, 10_000);
        replicas.get(0).bus.crash(3);
        replicas.get(1).submitValue("tolerates-one-crash");
        for (int i = 0; i < 3; i++) replicas.get(i).start();
        boolean decided = waitForDecision(replicas, "tolerates-one-crash", 3, 5000);
        stopAll(replicas);
        if (decided) System.out.println("PASS: 3 nodes decided despite 1 non-leader crash");
        else { System.out.println("FAIL: Did not decide with 3/4 nodes alive"); System.exit(1); }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 4: View Change / Crash Fault Tolerance Tests ===");
        testNormalRound();
        testLeaderCrash();
        testTwoConsecutiveCrashes();
        testNonLeaderCrash();
        System.out.println("\n=== All tests passed ===");
    }
}