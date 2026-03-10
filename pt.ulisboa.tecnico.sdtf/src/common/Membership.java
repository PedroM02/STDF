package common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Membership {

    private final Map<ProcessId, Address> members;
    private final Map<Integer, ProcessId> intIdToProcessIdMap;

    public Membership(Collection<NodeConfig> nodes) {
        this.members = new HashMap<>();
        this.intIdToProcessIdMap = new HashMap<>();

        for (NodeConfig node : nodes) {
            ProcessId pid = node.getId();
            members.put(pid, node.getAddress());
            intIdToProcessIdMap.put(node.getIntId(), pid);
        }
    }

    public Address getAddress(ProcessId processId) {
        Address address = members.get(processId);
        if (address == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
        }
        return address;
    }

    public ProcessId getProcessIdByInt(int intId) {
        ProcessId pid = intIdToProcessIdMap.get(intId);
        if (pid == null) {
            throw new IllegalArgumentException("Unknown process id: " + intId);
        }
        return pid;
    }

    public int size() {
        return members.size();
    }

    public Set<ProcessId> getProcessIds() {
        return members.keySet();
    }
}
