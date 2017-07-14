package co.rsk.scoring;

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.concurrent.GuardedBy;
import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 28/06/2017.
 */
public class PeerScoringManager {
    private ScoringCalculator scoringCalculator;
    private PunishmentCalculator nodePunishmentCalculator;
    private PunishmentCalculator ipPunishmentCalculator;

    private final Object accessLock = new Object();

    private InetAddressTable addressTable = new InetAddressTable();

    @GuardedBy("accessLock")
    private LinkedHashMap<NodeID, PeerScoring> peersByNodeID;

    @GuardedBy("accessLock")
    private Map<InetAddress, PeerScoring> peersByAddress;

    public PeerScoringManager(int nodePeersSize, PunishmentParameters nodeParameters, PunishmentParameters ipParameters) {
        this.scoringCalculator = new ScoringCalculator();
        this.nodePunishmentCalculator = new PunishmentCalculator(nodeParameters);
        this.ipPunishmentCalculator = new PunishmentCalculator(ipParameters);

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
                reviewReputation(scoring, this.nodePunishmentCalculator);
            }

            if (address != null) {
                if (!peersByAddress.containsKey(address))
                    peersByAddress.put(address, new PeerScoring());

                PeerScoring scoring = peersByAddress.get(address);
                scoring.recordEvent(event);
                reviewReputation(scoring, this.ipPunishmentCalculator);
            }
        }
    }

    public boolean hasGoodReputation(NodeID id) {
        return this.getPeerScoring(id).hasGoodReputation();
    }

    public boolean hasGoodReputation(InetAddress address)
    {
        if (this.addressTable.contains(address))
            return false;

        return this.getPeerScoring(address).hasGoodReputation();
    }

    public void addBannedAddress(InetAddress address) {
        this.addressTable.addAddress(address);
    }

    public void removeBannedAddress(InetAddress address) {
        this.addressTable.removeAddress(address);
    }

    public void addBannedAddressBlock(InetAddressBlock addressBlock) {
        this.addressTable.addAddressBlock(addressBlock);
    }

    public void removeBannedAddressBlock(InetAddressBlock addressBlock) {
        this.addressTable.removeAddressBlock(addressBlock);
    }

    public List<PeerScoringInformation> getPeersInformation() {
        List<PeerScoringInformation> list = new ArrayList<>(this.peersByNodeID.size() + this.peersByAddress.size());

        list.addAll(this.peersByNodeID.entrySet().stream().map(entry -> new PeerScoringInformation(entry.getValue(), Hex.toHexString(entry.getKey().getID()).substring(0, 8))).collect(Collectors.toList()));
        list.addAll(this.peersByAddress.entrySet().stream().map(entry -> new PeerScoringInformation(entry.getValue(), entry.getKey().getHostAddress())).collect(Collectors.toList()));

        return list;
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

    private void reviewReputation(PeerScoring scoring, PunishmentCalculator calculator) {
        boolean reputation = scoringCalculator.hasGoodReputation(scoring);

        if (!reputation && scoring.hasGoodReputation())
            scoring.startPunishment(calculator.calculate(scoring.getPunishmentCounter(), scoring.getScore()));
    }
}
