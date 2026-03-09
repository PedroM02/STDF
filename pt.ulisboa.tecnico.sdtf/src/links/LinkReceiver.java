package links;

import common.ProcessId;
import messages.MessageId;
import messages.ProtocolMessage;

public interface LinkReceiver {
    void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId);
}
