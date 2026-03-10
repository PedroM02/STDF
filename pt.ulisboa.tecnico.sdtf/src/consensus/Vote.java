package consensus;

import messages.ProtocolMessage;
import java.io.Serializable;

public class Vote implements ProtocolMessage, Serializable {
    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final int voterId;

    public Vote(String blockHash, long view, Phase phase, int voterId) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.voterId = voterId;
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