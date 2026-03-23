package consensus;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import crypto.CryptoService;

public class QuorumCertificate implements Serializable {

    private final String blockHash;
    private final long view;
    private final Phase phase;
    private final List<Vote> votes;         
    private int hotStuffVoteCount = 0;       
    private transient CryptoService cryptoService;
    private transient Map<Integer, PublicKey> publicKeys;

    public QuorumCertificate(String blockHash, long view, Phase phase, CryptoService cryptoService, Map<Integer, PublicKey> publicKeys) {
        this.blockHash = blockHash;
        this.view = view;
        this.phase = phase;
        this.cryptoService = cryptoService;
        this.publicKeys = publicKeys;
        this.votes = new ArrayList<>();
    }

    public void addVote(Vote vote) {

        PublicKey key = publicKeys.get(vote.getVoterId());

        boolean valid = cryptoService.verify(
                key,
                vote.toBytes(),
                vote.getSignature()
        );

        if (valid) {
            votes.add(vote);
        }
    }

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
