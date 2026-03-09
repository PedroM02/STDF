package gateway;

import blockchain.BlockchainService;
import blockchain.InMemoryLedger;
import messages.ClientAppendRequest;
import messages.ClientAppendResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class LocalAppendGatewayTests {

    @Test
    void validRequestAppendsToLedger() {
        BlockchainService ledger = new InMemoryLedger();
        LocalAppendGateway gateway = new LocalAppendGateway(ledger);

        ClientAppendRequest request = new ClientAppendRequest("client-1", "hello");
        ClientAppendResponse response = gateway.submitAppend(request);

        assertTrue(response.isSuccess());
        assertEquals(request.getRequestId(), response.getRequestId());
        assertEquals("client-1", response.getClientId());
        assertEquals(0, response.getIndex());
        assertNull(response.getErrorMessage());
        assertEquals(List.of("hello"), ledger.readAll());
    }

    @Test
    void invalidValueIsRejected() {
        BlockchainService ledger = new InMemoryLedger();
        LocalAppendGateway gateway = new LocalAppendGateway(ledger);

        ClientAppendRequest request = new ClientAppendRequest("client-1", "   ");
        ClientAppendResponse response = gateway.submitAppend(request);

        assertFalse(response.isSuccess());
        assertEquals("INVALID_VALUE", response.getErrorMessage());
        assertEquals(-1, response.getIndex());
        assertEquals(0, ledger.size());
    }

    @Test
    void nullRequestIsRejected() {
        BlockchainService ledger = new InMemoryLedger();
        LocalAppendGateway gateway = new LocalAppendGateway(ledger);

        ClientAppendResponse response = gateway.submitAppend(null);

        assertFalse(response.isSuccess());
        assertEquals("INVALID_REQUEST", response.getErrorMessage());
        assertEquals(-1, response.getIndex());
        assertEquals(0, ledger.size());
    }

    @Test
    void ledgerFailureReturnsInternalError() {
        BlockchainService failingService = new BlockchainService() {
            @Override
            public int append(String value) {
                throw new RuntimeException("boom");
            }

            @Override
            public List<String> readAll() {
                return List.of();
            }

            @Override
            public String readAt(int index) {
                throw new IndexOutOfBoundsException();
            }

            @Override
            public int size() {
                return 0;
            }
        };

        LocalAppendGateway gateway = new LocalAppendGateway(failingService);
        ClientAppendRequest request = new ClientAppendRequest("client-1", "boom");

        ClientAppendResponse response = gateway.submitAppend(request);

        assertFalse(response.isSuccess());
        assertEquals(request.getRequestId(), response.getRequestId());
        assertEquals("client-1", response.getClientId());
        assertEquals("INTERNAL_ERROR", response.getErrorMessage());
        assertEquals(-1, response.getIndex());
    }
}
