package common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Membership {
    private final Map<ProcessId, Address> members;

    public Membership(Collection<NodeConfig> nodes) {
        this.members = new HashMap<>();

        for (NodeConfig node : nodes) {
            members.put(node.getId(), node.getAddress());
        }
    }

    public Address getAddress(ProcessId processId) {
        Address address = members.get(processId);

        if (address == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
        }

        return address;
    }

}
