package common;

import java.io.Serializable;
import java.util.Objects;

public final class Address implements Serializable {
    private final String host;
    private final int port;

    public Address(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Address that)) return false;
        return Objects.equals(this.host, that.host) && this.port == that.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
