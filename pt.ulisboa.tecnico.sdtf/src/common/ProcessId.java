package common;

import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;



public final class ProcessId implements Serializable {
    private final String value;

    public ProcessId() {
        this.value = UUID.randomUUID().toString();
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessId that)) return false;
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
