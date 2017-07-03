package co.rsk.scoring;

import co.rsk.net.NodeID;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class PeerScoringManager {
    private Map<NodeID, PeerScoring> peersByNodeID = new HashMap<>();
    private Map<InetAddress, PeerScoring> peersByAddress = new HashMap<>();

    public void recordEvent(NodeID id, InetAddress address, EventType event) {
        if (id != null) {
            if (!peersByNodeID.containsKey(id))
                peersByNodeID.put(id, new PeerScoring());

            peersByNodeID.get(id).recordEvent(event);
        }

        if (address != null) {
            if (!peersByAddress.containsKey(address))
                peersByAddress.put(address, new PeerScoring());

            peersByAddress.get(address).recordEvent(event);
        }
    }

    public PeerScoring getPeerScoring(NodeID id) {
        if (peersByNodeID.containsKey(id))
            return peersByNodeID.get(id);

        return new PeerScoring();
    }

    public PeerScoring getPeerScoring(InetAddress address) {
        if (peersByAddress.containsKey(address))
            return peersByAddress.get(address);

        return new PeerScoring();
    }

    public boolean hasGoodReputation(NodeID id) {
        return this.getPeerScoring(id).hasGoodReputation();
    }

    public boolean isEmpty() {
        return this.peersByAddress.isEmpty() && this.peersByNodeID.isEmpty();
    }
}
