package consensus;

import messages.ProtocolMessage;
import java.io.Serializable;

public class Vote implements ProtocolMessage, Serializable {
    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final long configVersion;
    private final int voterId;
    private final byte[] signature;


    public Vote(String blockHash, long view, Phase phase, long configVersion, int voterId, byte[] signature) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.configVersion = configVersion;
        this.voterId = voterId;
        this.signature = signature;
    }

    public byte[] toBytes() {
        return QcSigningData.buildSigningBytes(blockHash, view, phase, configVersion);
    }

    public long getConfigVersion() { return configVersion; }

    public byte[] getSignature() { return signature; }

    public String getBlockHash() {
        return blockHash;
    }

    public long getView() {
        return view;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getVoterId() {
        return voterId;
    }
}
