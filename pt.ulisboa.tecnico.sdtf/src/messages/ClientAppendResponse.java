package messages;

public final class ClientAppendResponse implements ProtocolMessage {
    private final String requestId;
    private final String clientId;
    private final boolean success;
    private final int index;
    private final String errorMessage;


    public ClientAppendResponse(String requestId, String clientId, boolean success, int index, String errorMessage) {
        this.requestId = requestId;
        this.clientId = clientId;
        this.success = success;
        this.index = index;
        this.errorMessage = errorMessage;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClientId() {
        return clientId;
    }

    public int getIndex() {
        return index;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
