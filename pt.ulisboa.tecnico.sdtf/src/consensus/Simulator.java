package consensus;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory simulation of the Basic HotStuff protocol (no networking).
 */
public class Simulator {

    public static void run() {
        int n = 4;
        int f = 1;
        int quorumSize = 2 * f + 1; // 3

        List<ReplicaState> replicas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            replicas.add(new ReplicaState(i));
        }

        int leaderId = 0;

        // Genesis block
        Block genesis = new Block("0", "GENESIS", 0, -1);

        // Leader proposes block for view 1
        Block block1 = new Block(genesis.getHash(), "append:hello", 1, leaderId);
        Proposal proposal = new Proposal(block1, null);

        System.out.println("HotStuff Simulation");
        System.out.println("Leader proposes: " + block1.getCommand());

        // --- PREPARE phase ---
        QuorumCertificate prepareQC = new QuorumCertificate(block1.getHash(), 1, Phase.PREPARE);
        for (ReplicaState r : replicas) {
            Vote vote = r.onReceiveProposal(proposal, quorumSize);
            if (vote != null) {
                prepareQC.addVote(vote);
                System.out.println("  Replica " + r.getId() + " votes PREPARE");
            }
        }

        if (!prepareQC.hasQuorum(quorumSize)) {
            System.out.println("FAILED: no PREPARE quorum");
            return;
        }
        System.out.println("PREPARE QC formed (" + prepareQC.voteCount() + " votes)");

        // --- PRE_COMMIT phase ---
        QuorumCertificate preCommitQC = new QuorumCertificate(block1.getHash(), 1, Phase.PRE_COMMIT);
        for (ReplicaState r : replicas) {
            r.updateLock(prepareQC, block1);
            Vote vote = new Vote(block1.getHash(), 1, Phase.PRE_COMMIT, r.getId());
            preCommitQC.addVote(vote);
            System.out.println("  Replica " + r.getId() + " votes PRE_COMMIT");
        }

        if (!preCommitQC.hasQuorum(quorumSize)) {
            System.out.println("FAILED: no PRE_COMMIT quorum");
            return;
        }
        System.out.println("PRE_COMMIT QC formed (" + preCommitQC.voteCount() + " votes)");

        // --- COMMIT phase ---
        QuorumCertificate commitQC = new QuorumCertificate(block1.getHash(), 1, Phase.COMMIT);
        for (ReplicaState r : replicas) {
            r.updateLock(preCommitQC, block1);
            Vote vote = new Vote(block1.getHash(), 1, Phase.COMMIT, r.getId());
            commitQC.addVote(vote);
            System.out.println("  Replica " + r.getId() + " votes COMMIT");
        }

        if (!commitQC.hasQuorum(quorumSize)) {
            System.out.println("FAILED: no COMMIT quorum");
            return;
        }
        System.out.println("COMMIT QC formed (" + commitQC.voteCount() + " votes)");

        // --- DECIDE ---
        System.out.println("DECIDED: " + block1.getCommand());
        System.out.println("=== Simulation complete ===");
    }

    public static void main(String[] args) {
        Simulator.run();
    }
}
