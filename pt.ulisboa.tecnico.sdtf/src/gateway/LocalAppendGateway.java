package gateway;

import blockchain.BlockchainService;
import messages.ClientAppendRequest;
import messages.ClientAppendResponse;

import java.util.Objects;

public final class LocalAppendGateway implements AppendGateway {
    private static final int FAILED_INDEX = -1;

    private final BlockchainService blockchainService;

    public LocalAppendGateway(BlockchainService blockchainService) {
        this.blockchainService = Objects.requireNonNull(blockchainService, "blockchainService");
    }

    @Override
    public ClientAppendResponse submitAppend(ClientAppendRequest request) {
        if (request == null) {
            return new ClientAppendResponse(null, null, false, FAILED_INDEX, "INVALID_REQUEST");
        }
        String requestId = request.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            return new ClientAppendResponse(null, null, false, FAILED_INDEX, "INVALID_REQUEST_ID");
        }
        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            return new ClientAppendResponse(null, null, false, FAILED_INDEX, "INVALID_CLIENT_ID");
        }

        String value = request.getValue();
        if (value == null || value.isBlank()) {
            return new ClientAppendResponse(requestId, clientId, false, FAILED_INDEX, "INVALID_VALUE");
        }

        try {
            int index = blockchainService.append(value);
            return new ClientAppendResponse(requestId, clientId, true, index, null);
        } catch (RuntimeException e) {
            return new ClientAppendResponse(requestId, clientId, false, FAILED_INDEX, "INTERNAL_ERROR");
        }
    }
}