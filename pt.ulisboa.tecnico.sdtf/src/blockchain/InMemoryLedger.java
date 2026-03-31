package blockchain;

import consensus.Block;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryLedger implements BlockchainService {

    private final List<Block> blocks = new CopyOnWriteArrayList<>();
    private final WorldState worldState;
    private final BlockPersistence persistence;

    public InMemoryLedger(WorldState worldState, Block genesisBlock, BlockPersistence persistence) {
        this.worldState = worldState;
        this.persistence = persistence;
        this.blocks.add(genesisBlock);
        persistence.save(genesisBlock, 0);
    }

    @Override
    public void appendBlock(Block block) {
        for (var tx : block.getTransactions()) {
            TransactionResult result = worldState.execute(tx);
            if (!result.isSuccess()) {
                System.out.println("  TX failed: " + result.getErrorMessage() + " — " + tx);
            } else {
                System.out.println("  TX ok: " + tx + " gasUsed=" + result.getGasUsed());
            }
        }
        blocks.add(block);
        persistence.save(block, blocks.size() - 1);
    }

    @Override
    public Block getBlock(int index)  { return blocks.get(index); }

    @Override
    public Block getLatestBlock()     { return blocks.get(blocks.size() - 1); }

    @Override
    public int size()                 { return blocks.size(); }

    @Override
    public WorldState getWorldState() { return worldState; }
}