package co.rsk.scoring;

import co.rsk.net.NodeID;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class NodeScoringManager {
    private Map<NodeID, NodeScoring> nodesById = new HashMap<>();
    private Map<InetAddress, NodeScoring> nodesByAddress = new HashMap<>();

    public void recordEvent(NodeID id, InetAddress address, EventType event) {
        if (id != null) {
            if (!nodesById.containsKey(id))
                nodesById.put(id, new NodeScoring());

            nodesById.get(id).recordEvent(event);
        }

        if (address != null) {
            if (!nodesByAddress.containsKey(address))
                nodesByAddress.put(address, new NodeScoring());

            nodesByAddress.get(address).recordEvent(event);
        }
    }

    public NodeScoring getNodeScoring(NodeID id) {
        if (nodesById.containsKey(id))
            return nodesById.get(id);

        return new NodeScoring();
    }

    public NodeScoring getNodeScoring(InetAddress address) {
        if (nodesByAddress.containsKey(address))
            return nodesByAddress.get(address);

        return new NodeScoring();
    }
}
