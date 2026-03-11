package common;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ProcessId implements Serializable {
    private final String value;

    // auto generate a random unique ID
    public ProcessId() {
        this.value = UUID.randomUUID().toString();
    }

    // create a ProcessId with a known stable value (ex: "node-0")
    public ProcessId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
