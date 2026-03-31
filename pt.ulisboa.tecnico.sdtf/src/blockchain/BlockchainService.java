package blockchain;

import consensus.Block;
import transaction.Transaction;
import java.util.List;

public interface BlockchainService {
    void appendBlock(Block block);
    Block getBlock(int index);
    Block getLatestBlock();
    int size();
    WorldState getWorldState();
}