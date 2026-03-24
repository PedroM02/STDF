package consensus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class QuorumCertificate implements Serializable {

    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final long configVersion;
    private final int threshold;
    private final byte[] aggregatedSignature;
    private final List<Integer> participantsID;

    public QuorumCertificate(String blockHash, long view, Phase phase, long configVersion, int threshold, byte[] aggregatedSignature, List<Integer> participantsID) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.configVersion = configVersion;
        this.threshold = threshold;
        this.aggregatedSignature = aggregatedSignature.clone();
        this.participantsID = new ArrayList<>(participantsID);
    }

    public String getBlockHash() { return blockHash; }
    public long getView() { return view; }
    public Phase getPhase() { return phase; }
    public long getConfigVersion() { return configVersion; }
    public int getThreshold() { return threshold; }
    public byte[] getAggregatedSignature() { return aggregatedSignature.clone(); }
    public List<Integer> getParticipantsID() { return new ArrayList<>(participantsID); }

    public byte[] toSigningBytes() {
        return QcSigningData.buildSigningBytes(blockHash, view, phase, configVersion);
    }
}
