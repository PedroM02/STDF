package messages;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class MessageId implements Serializable {
    private final String value;

    public MessageId () {
        this.value = UUID.randomUUID().toString();
    }

    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if(!(o instanceof MessageId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }
}
