package consensus;

import common.Membership;
import common.ProcessId;
import links.AuthenticatedPerfectLink;
import links.LinkReceiver;
import messages.MessageId;
import messages.ProtocolMessage;

/**
 * ReplicaState wraps the per-replica mutable state used by the HotStuff protocol.
 * In the networked version this delegates to HotStuffNode.
 * Kept here as a lightweight in-memory object for the Simulator.
 */
public class ReplicaState {

    private final int id;
    private Block lockedBlock = null;
    private QuorumCertificate prepareQC = null;

    public ReplicaState(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Called when a PREPARE proposal arrives.
     * Returns a vote if the replica accepts the proposal, null otherwise.
     */
    public Vote onReceiveProposal(Proposal proposal, int quorumSize) {
        Block block = proposal.getBlock();
        QuorumCertificate justifyQC = proposal.getJustifyQC();

        // Safety rule: vote only if block extends locked block, or justify QC is higher
        long lockedView = (lockedBlock != null) ? lockedBlock.getView() : -1;
        long justifyView = (justifyQC != null) ? justifyQC.getView() : -1;

        if (lockedBlock == null
                || (block.getParentHash().equals(lockedBlock.getHash()))
                || justifyView > lockedView) {
            return new Vote(block.getHash(), block.getView(), Phase.PREPARE, id);
        }
        return null;
    }

    /** Update the locked block when a PRE_COMMIT or COMMIT QC is received. */
    public void updateLock(QuorumCertificate qc, Block block) {
        if (lockedBlock == null || qc.getView() > lockedBlock.getView()) {
            lockedBlock = block;
            prepareQC = qc;
        }
    }

    public Block getLockedBlock() { return lockedBlock; }
    public QuorumCertificate getPrepareQC() { return prepareQC; }
}
