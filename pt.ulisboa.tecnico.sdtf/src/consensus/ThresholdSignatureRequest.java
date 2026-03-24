package consensus;

import messages.ProtocolMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class ThresholdSignatureRequest implements ProtocolMessage, Serializable {

    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final long configVersion;
    private final int leaderId;
    private final List<Integer> participants;
    private final byte[] aggregatedCommitment;

    public ThresholdSignatureRequest(
            String blockHash,
            long view,
            Phase phase,
            long configVersion,
            int leaderId,
            List<Integer> participants,
            byte[] aggregatedCommitment
    ) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.configVersion = configVersion;
        this.leaderId = leaderId;
        this.participants = new ArrayList<>(participants);
        this.aggregatedCommitment = aggregatedCommitment.clone();
    }

    public String getBlockHash() { return blockHash; }
    public long getView() { return view; }
    public Phase getPhase() { return phase; }
    public long getConfigVersion() { return configVersion; }
    public int getLeaderId() { return leaderId; }
    public List<Integer> getParticipants() { return new ArrayList<>(participants); }
    public byte[] getAggregatedCommitment() { return aggregatedCommitment.clone(); }
}
