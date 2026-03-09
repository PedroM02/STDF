package messages;

import java.io.Serializable;

public final class AckMessage implements Serializable {
    private final MessageId ackedMessageId;

    public AckMessage(MessageId messageId) {
        this.ackedMessageId = messageId;
    }

    public MessageId getAckedMessageId() {
        return ackedMessageId;
    }

}