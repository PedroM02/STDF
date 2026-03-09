package blockchain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class InMemoryLedgerTests {

    @Test
    void appendPreservesOrderAndReturnsIndexes() {
        InMemoryLedger ledger = new InMemoryLedger();

        int index0 = ledger.append("a");
        int index1 = ledger.append("b");
        int index2 = ledger.append("c");

        assertEquals(0, index0);
        assertEquals(1, index1);
        assertEquals(2, index2);
        assertEquals(List.of("a", "b", "c"), ledger.readAll());
    }

    @Test
    void readAllReturnsDefensiveCopy() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.append("x");

        List<String> snapshot = ledger.readAll();
        snapshot.add("tamper");

        assertEquals(1, ledger.size());
        assertEquals(List.of("x"), ledger.readAll());
    }

    @Test
    void readAtRejectsInvalidIndexes() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.append("x");

        assertThrows(IndexOutOfBoundsException.class, () -> ledger.readAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ledger.readAt(1));
    }
}
