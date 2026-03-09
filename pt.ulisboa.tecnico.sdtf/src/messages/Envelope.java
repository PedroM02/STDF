package messages;

import common.ProcessId;
import java.io.Serializable;

public class Envelope implements Serializable {
    private final MessageType type;
    private final MessageId messageId;
    private final ProcessId sender;
    private final ProcessId receiver;
    private final Object payload;

    public Envelope(
            MessageType type,
            MessageId messageId,
            ProcessId sender,
            ProcessId receiver,
            Object payload
    ) {
        this.type = type;
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public ProcessId getSender() {
        return sender;
    }

    public ProcessId getReceiver() {
        return receiver;
    }

    public Object getPayload() {
        return payload;
    }
}
