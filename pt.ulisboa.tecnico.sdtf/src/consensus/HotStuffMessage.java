package consensus;

import messages.MessageType;
import messages.ProtocolMessage;
import java.io.Serializable;


public final class HotStuffMessage implements ProtocolMessage, Serializable {

    private final MessageType type;
    private final long viewNumber;
    private final Block block;           // null for vote/new-view messages
    private final QuorumCertificate qc;  // justify QC (may be null for first round)
    private final int senderId;

    public HotStuffMessage(MessageType type, long viewNumber, Block block, QuorumCertificate qc, int senderId) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.block = block;
        this.qc = qc;
        this.senderId = senderId;
    }

    public MessageType getType() { return type; }
    public long getViewNumber() { return viewNumber; }
    public Block getBlock() { return block; }
    public QuorumCertificate getQc() { return qc; }
    public int getSenderId() { return senderId; }

    @Override
    public String toString() {
        return "HotStuffMessage{type=" + type + ", view=" + viewNumber
                + ", sender=" + senderId
                + (block != null ? ", block=" + block : "")
                + "}";
    }
}
