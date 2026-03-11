package consensus;

import messages.ProtocolMessage;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class Vote implements ProtocolMessage, Serializable {
    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final int voterId;
    private final byte[] signature;


    public Vote(String blockHash, long view, Phase phase, int voterId, byte[] signature) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.voterId = voterId;
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] toBytes() {
        String data = blockHash + "|" + view + "|" + phase + "|" + voterId;
        return data.getBytes(StandardCharsets.UTF_8);
    }

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