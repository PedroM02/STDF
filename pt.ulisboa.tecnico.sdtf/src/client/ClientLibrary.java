package client;

import gateway.AppendGateway;
import messages.ClientAppendRequest;
import messages.ClientAppendResponse;

import java.util.Objects;
import java.util.UUID;

public class ClientLibrary {
    private final String clientId;
    private final AppendGateway gateway;

    public ClientLibrary(String clientId, AppendGateway gateway) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public ClientAppendResponse append(String value) {
        if (value == null || value.isBlank()) {
            return new ClientAppendResponse(
                    UUID.randomUUID().toString(),
                    clientId,
                    false,
                    -1,
                    "INVALID_VALUE"
            );
        }

        ClientAppendRequest request = new ClientAppendRequest(clientId, value);
        return gateway.submitAppend(request);
    }
}
