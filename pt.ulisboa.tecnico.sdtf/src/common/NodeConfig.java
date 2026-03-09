package common;

import java.io.Serializable;
import java.util.Objects;

public final class NodeConfig implements Serializable {
    private final ProcessId id;
    private final Address address;
    private final Membership membership;

    public NodeConfig(ProcessId id, Address address, Membership membership) {
        this.id = id;
        this.address = address;
        this.membership = membership;
    }

    public ProcessId getId() {
        return id;
    }

    public Address getAddress() {
        return address;
    }

    public Membership getMembership() {
        return membership;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, address, membership);
    }

    @Override
    public String toString() {
        return id.toString() + address.toString() + membership.toString();
    }
}
