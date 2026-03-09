package client;

// NAO USADA!!!
public final class ClientRequest {
    private final String requestId;
    private final String clientId;
    private final String value;


    public ClientRequest(String requestId, String clientId, String value) {
        this.requestId = requestId;
        this.clientId = clientId;
        this.value = value;
    }
}
