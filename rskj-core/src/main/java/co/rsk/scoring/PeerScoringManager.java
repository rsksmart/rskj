package co.rsk.scoring;

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;

import javax.annotation.concurrent.GuardedBy;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class PeerScoringManager {
    private ScoringCalculator scoringCalculator = new ScoringCalculator();
    private PunishmentCalculator punishmentCalculator = new PunishmentCalculator();

    private final Object accessLock = new Object();
    private long expirationTime = 0L;

    @GuardedBy("accessLock")
    private LinkedHashMap<NodeID, PeerScoring> peersByNodeID;

    @GuardedBy("accessLock")
    private Map<InetAddress, PeerScoring> peersByAddress;

    public PeerScoringManager() {
        this(20);
    }

    public PeerScoringManager(int nodePeersSize) {
        this.peersByNodeID = new LinkedHashMap<NodeID, PeerScoring>(nodePeersSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<NodeID, PeerScoring> eldest) {
                return size() > nodePeersSize;
            }
        };

        this.peersByAddress = new HashMap<>();
    }
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

    public boolean hasGoodReputation(NodeID id) {
        return this.getPeerScoring(id).hasGoodReputation();
    }

    public boolean hasGoodReputation(InetAddress address)
    {
        return this.getPeerScoring(address).hasGoodReputation();
    }

    public void setExpirationTime(long time) {
        this.expirationTime = time;
    }

    @VisibleForTesting
    public boolean isEmpty() {
        return this.peersByAddress.isEmpty() && this.peersByNodeID.isEmpty();
    }

    @VisibleForTesting
    public PeerScoring getPeerScoring(NodeID id) {
        synchronized (accessLock) {
            if (peersByNodeID.containsKey(id))
                return peersByNodeID.get(id);

            return new PeerScoring();
        }
    }

    @VisibleForTesting
    public PeerScoring getPeerScoring(InetAddress address) {
        synchronized (accessLock) {
            if (peersByAddress.containsKey(address))
                return peersByAddress.get(address);

            return new PeerScoring();
        }
    }

    private void reviewReputation(PeerScoring scoring) {
        boolean reputation = scoringCalculator.hasGoodReputation(scoring);

        if (!reputation && scoring.hasGoodReputation())
            scoring.startPunishment(this.punishmentCalculator.calculate(expirationTime, expirationTime * 10, 10, scoring.getPunishmentCounter()));
    }
}
