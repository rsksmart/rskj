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
    private long expirationTime = 0L;

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
        synchronized (accessLock) {
            PeerScoring scoring = this.getPeerScoring(id);
            boolean goodReputation = scoring.hasGoodReputation();

            if (goodReputation == false && this.expirationTime > 0 && scoring.getTimeLostGoodReputation() + expirationTime <= System.currentTimeMillis()) {
                this.peersByNodeID.put(id, new PeerScoring());
                return true;
            }

            return goodReputation;
        }
    }

    public boolean hasGoodReputation(InetAddress address)
    {
        synchronized (accessLock) {
            PeerScoring scoring = this.getPeerScoring(address);
            boolean goodReputation = scoring.hasGoodReputation();

            if (goodReputation == false && this.expirationTime > 0 && scoring.getTimeLostGoodReputation() + expirationTime <= System.currentTimeMillis()) {
                this.peersByAddress.put(address, new PeerScoring());
                return true;
            }

            return goodReputation;
        }
    }

    public void setExpirationTime(long time) {
        this.expirationTime = time;
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
