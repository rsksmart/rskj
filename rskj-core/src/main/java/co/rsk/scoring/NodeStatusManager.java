package co.rsk.scoring;

import co.rsk.net.NodeID;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class NodeStatusManager {
    private Map<NodeID, NodeStatus> nodesById = new HashMap<>();
    private Map<InetAddress, NodeStatus> nodesByAddress = new HashMap<>();

    public void recordEvent(NodeID id, InetAddress address, EventType event) {
        if (id != null) {
            if (!nodesById.containsKey(id))
                nodesById.put(id, new NodeStatus());

            nodesById.get(id).recordEvent(event);
        }

        if (address != null) {
            if (!nodesByAddress.containsKey(address))
                nodesByAddress.put(address, new NodeStatus());

            nodesByAddress.get(address).recordEvent(event);
        }
    }

    public NodeStatus getNodeStatus(NodeID id) {
        if (nodesById.containsKey(id))
            return nodesById.get(id);

        return new NodeStatus();
    }

    public NodeStatus getNodeStatus(InetAddress address) {
        if (nodesByAddress.containsKey(address))
            return nodesByAddress.get(address);

        return new NodeStatus();
    }
}
