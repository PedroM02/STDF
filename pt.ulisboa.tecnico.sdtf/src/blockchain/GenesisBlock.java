package blockchain;

import transaction.Transaction;
import consensus.Block;
import java.util.List;

public class GenesisBlock {

    public static Block create() {
        return new Block("0000000000000000", List.of(), 0, -1);
    }

    public static WorldState createWorldState() {
        WorldState state = new WorldState();
        state.addAccount(new Account("alice", 100_000L));
        state.addAccount(new Account("bob",   100_000L));
        state.addAccount(new Account("carol", 100_000L));
        return state;
    }
}