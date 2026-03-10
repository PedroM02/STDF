package consensus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QuorumCertificate implements Serializable {

    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final List<Vote> votes;          // used by Simulator
    private int hotStuffVoteCount = 0;       // used by HotStuffNode

    public QuorumCertificate(String blockHash, long view, Phase phase) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.votes = new ArrayList<>();
    }

    /** For Simulator / classic path. */
    public void addVote(Vote vote) {
        votes.add(vote);
    }

    /** For HotStuffNode path (votes stored externally, QC just tracks count). */
    public void addVote(HotStuffMessage msg) {
        hotStuffVoteCount++;
    }

    public boolean hasQuorum(int quorumSize) {
        return (votes.size() + hotStuffVoteCount) >= quorumSize;
    }

    public int voteCount() {
        return votes.size() + hotStuffVoteCount;
    }

    public String getBlockHash() { return blockHash; }
    public long getView() { return view; }
    public Phase getPhase() { return phase; }
    public List<Vote> getVotes() { return votes; }
}
