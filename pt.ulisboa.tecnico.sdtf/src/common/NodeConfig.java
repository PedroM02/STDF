package common;

import java.io.Serializable;
import java.util.Objects;

public final class NodeConfig implements Serializable {

    private final int intId;
    private final ProcessId id;
    private final Address address;
    private Membership membership; // set later to break circular dependency

    public NodeConfig(int intId, ProcessId id, Address address) {
        this.intId = intId;
        this.id = id;
        this.address = address;
        this.membership = null;
    }

    /** Convenience constructor used in tests (no intId needed). */
    public NodeConfig(ProcessId id, Address address, Membership membership) {
        this.intId = 0;
        this.id = id;
        this.address = address;
        this.membership = membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public int getIntId() {
        return intId;
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
        return Objects.hash(intId, id, address);
    }

    @Override
    public String toString() {
        return intId + ":" + id.toString() + "@" + address.toString();
    }
}