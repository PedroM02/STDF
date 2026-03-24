package consensus;

import messages.ProtocolMessage;

import java.io.Serializable;

public final class ThresholdPartialSignature implements ProtocolMessage, Serializable {

    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final long configVersion;
    private final int senderId;
    private final byte[] partialSignature;

    public ThresholdPartialSignature(
            String blockHash,
            long view,
            Phase phase,
            long configVersion,
            int senderId,
            byte[] partialSignature
    ) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.configVersion = configVersion;
        this.senderId = senderId;
        this.partialSignature = partialSignature.clone();
    }

    public String getBlockHash() { return blockHash; }
    public long getView() { return view; }
    public Phase getPhase() { return phase; }
    public long getConfigVersion() { return configVersion; }
    public int getSenderId() { return senderId; }
    public byte[] getPartialSignature() { return partialSignature.clone(); }
}
