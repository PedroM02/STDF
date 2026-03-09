package client;

import gateway.AppendGateway;
import messages.ClientAppendRequest;
import messages.ClientAppendResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class ClientLibraryTests {

    @Test
    void validAppendDelegatesToGatewayWithGeneratedRequestId() {
        RecordingGateway gateway = new RecordingGateway(new ClientAppendResponse("req-x", "client-1", true, 7, null));
        ClientLibrary library = new ClientLibrary("client-1", gateway);

        ClientAppendResponse actual = library.append("value-1");

        assertEquals("req-x", actual.getRequestId());
        assertEquals("client-1", actual.getClientId());
        assertEquals(7, actual.getIndex());

        ClientAppendRequest captured = gateway.lastRequest;
        assertNotNull(captured);
        assertEquals("client-1", captured.getClientId());
        assertEquals("value-1", captured.getValue());
        assertNotNull(captured.getRequestId());
        assertFalse(captured.getRequestId().isBlank());
    }

    @Test
    void blankAppendReturnsInvalidValueWithoutCallingGateway() {
        RecordingGateway gateway = new RecordingGateway(new ClientAppendResponse("ignored", "client-1", true, 1, null));
        ClientLibrary library = new ClientLibrary("client-1", gateway);

        ClientAppendResponse response = library.append("   ");

        assertFalse(response.isSuccess());
        assertEquals(-1, response.getIndex());
        assertEquals("INVALID_VALUE", response.getErrorMessage());
        assertEquals("client-1", response.getClientId());
        assertNotNull(response.getRequestId());
        assertFalse(response.getRequestId().isBlank());
        assertNull(gateway.lastRequest);
    }

    @Test
    void appendGeneratesDifferentRequestIdPerRequest() {
        RecordingGateway gateway = new RecordingGateway(new ClientAppendResponse("req-y", "client-1", true, 8, null));
        ClientLibrary library = new ClientLibrary("client-1", gateway);

        library.append("v1");
        String firstRequestId = gateway.lastRequest.getRequestId();
        library.append("v2");
        String secondRequestId = gateway.lastRequest.getRequestId();

        assertNotEquals(firstRequestId, secondRequestId);
    }

    private static final class RecordingGateway implements AppendGateway {
        private final ClientAppendResponse fixedResponse;
        private ClientAppendRequest lastRequest;

        private RecordingGateway(ClientAppendResponse fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override
        public ClientAppendResponse submitAppend(ClientAppendRequest request) {
            this.lastRequest = request;
            return fixedResponse;
        }
    }
}
