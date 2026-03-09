package gateway;

import messages.ClientAppendRequest;
import messages.ClientAppendResponse;

public interface AppendGateway {
    ClientAppendResponse submitAppend(ClientAppendRequest request);
}
