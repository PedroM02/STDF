package consensus;

import java.util.ArrayList;
import java.util.List;
import crypto.SignatureUtils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.security.PublicKey;

/**
 * In-memory simulation of the Basic HotStuff protocol (no networking).
 */
public class Simulator {

    public static void run() {
        int n = 4;
        int f = 1;
        int quorumSize = 2 * f + 1; // 3

        SignatureUtils cryptoService = new SignatureUtils();

        Map<Integer, PrivateKey> privateKeys = new HashMap<>();
        Map<Integer, KeyPair> keyPairs = new HashMap<>();
        Map<Integer, PublicKey> publicKeys = new HashMap<>();

        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < n; i++) {
            KeyPair kp = keyGen.generateKeyPair();
            keyPairs.put(i, kp);
            privateKeys.put(i, kp.getPrivate());
            publicKeys.put(i, kp.getPublic());
        }

        List<ReplicaState> replicas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            replicas.add(new ReplicaState(i, cryptoService, privateKeys.get(i)));
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
        QuorumCertificate prepareQC = new QuorumCertificate(block1.getHash(), 1, Phase.PREPARE, cryptoService, publicKeys);
        for (ReplicaState r : replicas) {
            // Replica generates vote
            Vote unsignedVote = r.onReceiveProposal(proposal, quorumSize);
            if (unsignedVote != null) {
                // Sign the vote
                byte[] voteBytes = unsignedVote.toBytes();
                byte[] sig = cryptoService.sign(privateKeys.get(r.getId()), voteBytes);
                Vote signedVote = new Vote(unsignedVote.getBlockHash(), unsignedVote.getView(),
                                           unsignedVote.getPhase(), r.getId(), sig);
                prepareQC.addVote(signedVote);
                System.out.println("  Replica " + r.getId() + " votes PREPARE");
            }
        }

        if (!prepareQC.hasQuorum(quorumSize)) {
            System.out.println("FAILED: no PREPARE quorum");
            return;
        }
        System.out.println("PREPARE QC formed (" + prepareQC.voteCount() + " votes)");

        // --- PRE_COMMIT phase ---
        QuorumCertificate preCommitQC = new QuorumCertificate(block1.getHash(), 1, Phase.PRE_COMMIT, cryptoService, publicKeys);
        for (ReplicaState r : replicas) {
            r.updateLock(prepareQC, block1);
            Vote unsignedVote = new Vote(block1.getHash(), 1, Phase.PRE_COMMIT, r.getId(), null);
            byte[] voteBytes = unsignedVote.toBytes();
            byte[] sig = cryptoService.sign(privateKeys.get(r.getId()), voteBytes);
            Vote vote = new Vote(block1.getHash(), 1, Phase.PRE_COMMIT, r.getId(), sig);
            preCommitQC.addVote(vote);

            System.out.println("  Replica " + r.getId() + " votes PRE_COMMIT");
        }

        if (!preCommitQC.hasQuorum(quorumSize)) {
            System.out.println("FAILED: no PRE_COMMIT quorum");
            return;
        }
        System.out.println("PRE_COMMIT QC formed (" + preCommitQC.voteCount() + " votes)");

        // --- COMMIT phase ---
        QuorumCertificate commitQC = new QuorumCertificate(block1.getHash(), 1, Phase.COMMIT, cryptoService, publicKeys);
        for (ReplicaState r : replicas) {
            r.updateLock(preCommitQC, block1);
            Vote unsignedVote = new Vote(block1.getHash(), 1, Phase.COMMIT, r.getId(), null);
            byte[] voteBytes = unsignedVote.toBytes();
            byte[] sig = cryptoService.sign(privateKeys.get(r.getId()), voteBytes);
            Vote vote = new Vote(block1.getHash(), 1, Phase.COMMIT, r.getId(), sig);
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