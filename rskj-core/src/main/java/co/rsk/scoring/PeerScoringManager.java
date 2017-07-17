package co.rsk.scoring;

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.concurrent.GuardedBy;
import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PeerScoringManager keeps list of nodes and addresses scoring
 * Records events by node id and address
 * Calculates good reputation by node id and address
 * Starts punishments when the good reputation is lost
 * Alsa keeps a list of banned addresses and blocks
 * <p>
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

    /**
     * Creates and initialize the scoring manager
     * usually only one object per running node
     *
     * @param nodePeersSize     maximum number of nodes to keep
     * @param nodeParameters    nodes punishment parameters (@see PunishmentParameters)
     * @param ipParameters      address punishment parameters
     */
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

    /**
     * Record the event, givent the node id and/or the network address
     *
     * @param id        node id or null
     * @param address   address or null
     * @param event     event type (@see EventType)
     */
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

    /**
     * Returns if the given node id has good reputation
     *
     * @param id    the node id
     * @return  <tt>true</tt> if the node has good reputation
     */
    public boolean hasGoodReputation(NodeID id) {
        return this.getPeerScoring(id).hasGoodReputation();
    }

    /**
     * Returns if the given networkaddress has good reputation
     *
     * @param address   the network address
     * @return  <tt>true</tt> if the address has good reputation
     */
    public boolean hasGoodReputation(InetAddress address)
    {
        if (this.addressTable.contains(address))
            return false;

        return this.getPeerScoring(address).hasGoodReputation();
    }

    /**
     * Adds a network address to the set of banned addresses
     *
     * @param address   the address to be banned
     */
    public void addBannedAddress(InetAddress address) {
        this.addressTable.addAddress(address);
    }

    /**
     * Adds a network address to the set of banned addresses
     * The address is represented in an string
     * If it is a block, it has a mask
     *
     * @param address   the address or address block to be banned
     */
    public void addBannedAddress(String address) throws InvalidInetAddressBlockException, InvalidInetAddressException {
        if (InetAddressUtils.hasMask(address))
            this.addBannedAddressBlock(InetAddressUtils.parse(address));
        else
            this.addBannedAddress(InetAddressUtils.getAddress(address));
    }

    /**
     * Removes a network address from the set of banned addresses
     *
     * @param address   the address to be removed
     */
    public void removeBannedAddress(InetAddress address) {
        this.addressTable.removeAddress(address);
    }

    /**
     * Removes a network address from the set of banned addresses
     * The address is represented in an string
     * If it is a block, it has a mask
     *
     * @param address   the address or address block to be removed
     */
    public void removeBannedAddress(String address) throws InvalidInetAddressBlockException, InvalidInetAddressException {
        if (InetAddressUtils.hasMask(address))
            this.removeBannedAddressBlock(InetAddressUtils.parse(address));
        else
            this.removeBannedAddress(InetAddressUtils.getAddress(address));
    }

    /**
     * Adds a network address block to the set of banned blocks
     *
     * @param addressBlock   the address block to be banned
     */
    public void addBannedAddressBlock(InetAddressBlock addressBlock) {
        this.addressTable.addAddressBlock(addressBlock);
    }

    /**
     * Removes a network address block from the set of banned blocks
     *
     * @param addressBlock   the address block to be removed
     */
    public void removeBannedAddressBlock(InetAddressBlock addressBlock) {
        this.addressTable.removeAddressBlock(addressBlock);
    }

    /**
     * Returns the list of peer scoring information
     * It contains the information recorded by node id and by address
     *
     * @return  the list of peer scoring information
     */
    public List<PeerScoringInformation> getPeersInformation() {
        List<PeerScoringInformation> list = new ArrayList<>(this.peersByNodeID.size() + this.peersByAddress.size());

        list.addAll(this.peersByNodeID.entrySet().stream().map(entry -> new PeerScoringInformation(entry.getValue(), Hex.toHexString(entry.getKey().getID()).substring(0, 8))).collect(Collectors.toList()));
        list.addAll(this.peersByAddress.entrySet().stream().map(entry -> new PeerScoringInformation(entry.getValue(), entry.getKey().getHostAddress())).collect(Collectors.toList()));

        return list;
    }

    /**
     * Returns the list of banned addresses, represented by a textual description
     * The list includes the banned addresses and the banned blocks
     *
     * @return a list of strings describing the banned addresses and blocks
     */
    public List<String> getBannedAddressList() {
        List<String> list = new ArrayList<>();

        list.addAll(this.addressTable.getAddressList().stream().map(entry -> entry.getHostAddress()).collect(Collectors.toList()));
        list.addAll(this.addressTable.getAddressBlockList().stream().map(entry -> entry.getDescription()).collect(Collectors.toList()));

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

    /**
     * Calculates the reputation for a peer
     * Starts punishment if needed
     *
     * @param scoring       the peer scoring
     * @param calculator    the calculator to use
     */
    private void reviewReputation(PeerScoring scoring, PunishmentCalculator calculator) {
        boolean reputation = scoringCalculator.hasGoodReputation(scoring);

        if (!reputation && scoring.hasGoodReputation())
            scoring.startPunishment(calculator.calculate(scoring.getPunishmentCounter(), scoring.getScore()));
    }
}
