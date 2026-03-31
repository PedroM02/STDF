package blockchain;

import consensus.Block;
import org.junit.jupiter.api.Test;
import transaction.Transaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class WorldStateByzantineTests {

    @Test
    void higherGasPriceFrontRunnerCannotDoubleSpendBecauseNonceAdvances() throws Exception {
        InMemoryLedger ledger = newLedger();

        Transaction lowPriority = new Transaction("alice", "carol", 100L, 1L, 21_000L, 0L, null);
        Transaction frontRunner = new Transaction("alice", "bob", 100L, 2L, 21_000L, 0L, null);
        Block block = new Block(ledger.getLatestBlock().getHash(), List.of(lowPriority, frontRunner), 1L, 0);

        assertEquals(List.of(frontRunner, lowPriority), block.getTransactions());

        ledger.appendBlock(block);

        WorldState state = ledger.getWorldState();
        assertEquals(57_900L, state.getAccount("alice").getBalance());
        assertEquals(1L, state.getAccount("alice").getNonce());
        assertEquals(100_100L, state.getAccount("bob").getBalance());
        assertEquals(100_000L, state.getAccount("carol").getBalance());
    }

    @Test
    void maliciousBlockCannotOverdrawAccountAcrossSequentialTransactions() throws Exception {
        InMemoryLedger ledger = newLedger();

        Transaction first = new Transaction("alice", "bob", 60_000L, 1L, 21_000L, 0L, null);
        Transaction second = new Transaction("alice", "carol", 30_000L, 1L, 21_000L, 1L, null);
        Block block = new Block(ledger.getLatestBlock().getHash(), List.of(first, second), 1L, 0);

        ledger.appendBlock(block);

        WorldState state = ledger.getWorldState();
        assertEquals(19_000L, state.getAccount("alice").getBalance());
        assertEquals(1L, state.getAccount("alice").getNonce());
        assertEquals(160_000L, state.getAccount("bob").getBalance());
        assertEquals(100_000L, state.getAccount("carol").getBalance());
    }

    private static InMemoryLedger newLedger() throws Exception {
        Path directory = Files.createTempDirectory("ledger-byzantine-");
        return new InMemoryLedger(
                GenesisBlock.createWorldState(),
                GenesisBlock.create(),
                new BlockPersistence(directory.toString())
        );
    }
}
