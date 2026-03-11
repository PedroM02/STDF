package consensus;

import crypto.CryptoService;
import java.security.PrivateKey;


public class ReplicaState {

    private final int id;
    private final CryptoService cryptoService;
    private final PrivateKey privateKey;

    private Block lockedBlock = null;
    private QuorumCertificate prepareQC = null;

    public ReplicaState(int id, CryptoService cryptoService, PrivateKey privateKey) {
        this.id = id;
        this.cryptoService = cryptoService;
        this.privateKey = privateKey;
    }

    public int getId() {
        return id;
    }

    public Vote onReceiveProposal(Proposal proposal, int quorumSize) {
        Block block = proposal.getBlock();
        QuorumCertificate justifyQC = proposal.getJustifyQC();

        long lockedView = (lockedBlock != null) ? lockedBlock.getView() : -1;
        long justifyView = (justifyQC != null) ? justifyQC.getView() : -1;

        if (lockedBlock == null
                || block.getParentHash().equals(lockedBlock.getHash())
                || justifyView > lockedView) {

            Vote unsignedVote = new Vote(block.getHash(), block.getView(), Phase.PREPARE, id, null);

            byte[] signature = cryptoService.sign(privateKey, unsignedVote.toBytes());

            return new Vote(block.getHash(), block.getView(), Phase.PREPARE, id, signature);
        }

        return null;
    }

    public void updateLock(QuorumCertificate qc, Block block) {
        if (lockedBlock == null || qc.getView() > lockedBlock.getView()) {
            lockedBlock = block;
            prepareQC = qc;
        }
    }

    public Block getLockedBlock() { return lockedBlock; }
    public QuorumCertificate getPrepareQC() { return prepareQC; }
}