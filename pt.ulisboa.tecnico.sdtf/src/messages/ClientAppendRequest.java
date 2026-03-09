package messages;

import java.util.UUID;

public final class ClientAppendRequest implements ProtocolMessage {
    private final String requestId;
    private final String clientId;
    private final String value;


    public ClientAppendRequest(String clientId, String value) {
        this.requestId = UUID.randomUUID().toString();
        this.clientId = clientId;
        this.value = value;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getValue() {
        return value;
    }
}
