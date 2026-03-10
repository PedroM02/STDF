package consensus;

import messages.ProtocolMessage;
import java.io.Serializable;

public class Proposal implements ProtocolMessage, Serializable {
    private final Block block;
    private final QuorumCertificate justifyQC;

    public Proposal(Block block, QuorumCertificate justifyQC) {
        this.block = block;
        this.justifyQC = justifyQC;
    }

    public Block getBlock() {
        return block;
    }

    public QuorumCertificate getJustifyQC() {
        return justifyQC;
    }
}