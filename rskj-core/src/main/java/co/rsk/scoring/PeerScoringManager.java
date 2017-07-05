package co.rsk.scoring;

import co.rsk.net.NodeID;

import javax.annotation.concurrent.GuardedBy;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class PeerScoringManager {
    private ScoringCalculator calculator = new ScoringCalculator();
    private final Object accessLock = new Object();

    @GuardedBy("accessLock")
    private Map<NodeID, PeerScoring> peersByNodeID = new HashMap<>();

    @GuardedBy("accessLock")
    private Map<InetAddress, PeerScoring> peersByAddress = new HashMap<>();

    public void recordEvent(NodeID id, InetAddress address, EventType event) {
        synchronized (accessLock) {
            if (id != null) {
                if (!peersByNodeID.containsKey(id))
                    peersByNodeID.put(id, new PeerScoring());

                PeerScoring scoring = peersByNodeID.get(id);
                scoring.recordEvent(event);
                reviewReputation(scoring);
            }

            if (address != null) {
                if (!peersByAddress.containsKey(address))
                    peersByAddress.put(address, new PeerScoring());

                PeerScoring scoring = peersByAddress.get(address);
                scoring.recordEvent(event);
                reviewReputation(scoring);
            }
        }
    }

    public PeerScoring getPeerScoring(NodeID id) {
        synchronized (accessLock) {
            if (peersByNodeID.containsKey(id))
                return peersByNodeID.get(id);

            return new PeerScoring();
        }
    }

    public PeerScoring getPeerScoring(InetAddress address) {
        synchronized (accessLock) {
            if (peersByAddress.containsKey(address))
                return peersByAddress.get(address);

            return new PeerScoring();
        }
    }

    public boolean hasGoodReputation(NodeID id) {
        return this.getPeerScoring(id).hasGoodReputation();
    }

    public boolean hasGoodReputation(InetAddress address) {
        return this.getPeerScoring(address).hasGoodReputation();
    }

    public boolean isEmpty() {
        return this.peersByAddress.isEmpty() && this.peersByNodeID.isEmpty();
    }

    private void reviewReputation(PeerScoring scoring) {
        boolean reputation = calculator.hasGoodReputation(scoring);

        if (reputation != scoring.hasGoodReputation())
            scoring.setGoodReputation(reputation);
    }
}
