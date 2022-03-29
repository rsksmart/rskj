package co.rsk.scoring;

import co.rsk.net.NodeID;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger("peerScoring");

    private final PeerScoring.Factory peerScoringFactory;
    private final ScoringCalculator scoringCalculator;
    private final PunishmentCalculator nodePunishmentCalculator;
    private final PunishmentCalculator ipPunishmentCalculator;

    private final Object accessLock = new Object();

    private final InetAddressTable addressTable = new InetAddressTable();
    private final Set<NodeID> bannedNodeIds;

    @GuardedBy("accessLock")
    private final Map<NodeID, PeerScoring> peersByNodeID;

    @GuardedBy("accessLock")
    private final Map<InetAddress, PeerScoring> peersByAddress;

    /**
     * Creates and initialize the scoring manager
     * usually only one object per running node
     *
     * @param peerScoringFactory     creates empty peer scorings
     * @param nodePeersSize          maximum number of nodes to keep
     * @param nodeParameters         nodes punishment parameters (@see PunishmentParameters)
     * @param ipParameters           address punishment parameters
     */
    public PeerScoringManager(
            PeerScoring.Factory peerScoringFactory,
            int nodePeersSize,
            PunishmentParameters nodeParameters,
            PunishmentParameters ipParameters,
            Collection<String> bannedPeerIPs,
            Collection<String> bannedPeerIDs) {
        this.peerScoringFactory = peerScoringFactory;
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

        this.bannedNodeIds = Collections.unmodifiableSet(
                bannedPeerIDs.stream().map(NodeID::ofHexString).collect(Collectors.toSet())
        );

        bannedPeerIPs.forEach(this::banAddressOrThrow);
    }

    /**
     * Record the event, given the node id and/or the network address
     *
     * Usually we collected the events TWICE, if possible: by node id and by address.
     * In some events we don't have the node id, yet. The rationale to have both, is to collect events for the
     * same node_id, but maybe with different address along the time. Or same address with different node id.
     *
     * @param id        node id or null
     * @param address   address or null
     * @param event     event type (@see EventType)
     */
    public void recordEvent(NodeID id, InetAddress address, EventType event) {
        //todo(techdebt) this method encourages null params, this is not desirable
        synchronized (accessLock) {
            if (id != null) {
                PeerScoring scoring = peersByNodeID.computeIfAbsent(id, k -> peerScoringFactory.newInstance());
                recordEventAndStartPunishment(scoring, event, this.nodePunishmentCalculator, id);
            }

            if (address != null) {
                PeerScoring scoring = peersByAddress.computeIfAbsent(address, k -> peerScoringFactory.newInstance());
                recordEventAndStartPunishment(scoring, event, this.ipPunishmentCalculator, id);
            }

            logger.debug("Recorded {}. {}, Address: {}", event, nodeIdForLog(id),  addressForLog(address));
        }
    }

    /**
     * Returns if the given node id has good reputation
     *
     * @param id    the node id
     * @return  <tt>true</tt> if the node has good reputation
     */
    public boolean hasGoodReputation(NodeID id) {
        if (isNodeIDBanned(id)) {
            return false;
        }

        synchronized (accessLock) {
            return this.getPeerScoring(id).refreshReputationAndPunishment();
        }
    }

    /**
     * Returns if the given networkaddress has good reputation
     *
     * @param address   the network address
     * @return  <tt>true</tt> if the address has good reputation
     */
    public boolean hasGoodReputation(InetAddress address) {
        if (isAddressBanned(address)) {
            return false;
        }

        synchronized (accessLock) {
            return this.getPeerScoring(address).refreshReputationAndPunishment();
        }
    }

    public boolean isAddressBanned(InetAddress address) {
        return this.addressTable.contains(address);
    }

    public boolean isNodeIDBanned(NodeID id) {
        return this.bannedNodeIds.contains(id);
    }

    /**
     * Adds a network address to the set of banned addresses
     *
     * @param address   the address to be banned
     */
    public void banAddress(InetAddress address) {
        this.addressTable.addAddress(address);
    }

    /**
     * Adds a network address to the set of banned addresses
     * The address is represented in an string
     * If it is a block, it has a mask
     *
     * @param address   the address or address block to be banned
     */
    public void banAddress(String address) throws InvalidInetAddressException {
        boolean isAddressBlock = InetAddressUtils.hasMask(address);
        if (isAddressBlock) {
            this.banAddressBlock(InetAddressUtils.parse(address));
        } else {
            this.banAddress(InetAddressUtils.getAddressForBan(address));
        }

        String addressOrAddressBlock = isAddressBlock ? "block" : "";
        logger.debug("Banned address {} {}", addressOrAddressBlock ,address);
    }

    /**
     * Removes a network address from the set of banned addresses
     *
     * @param address   the address to be removed
     */
    public void unbanAddress(InetAddress address) {
        this.addressTable.removeAddress(address);
    }

    /**
     * Removes a network address from the set of banned addresses
     * The address is represented in an string
     * If it is a block, it has a mask
     *
     * @param address   the address or address block to be removed
     */
    public void unbanAddress(String address) throws InvalidInetAddressException {
        boolean isAddressBlock = InetAddressUtils.hasMask(address);
        if (isAddressBlock) {
            this.unbanAddressBlock(InetAddressUtils.parse(address));
        } else {
            this.unbanAddress(InetAddressUtils.getAddressForBan(address));
        }

        String addressOrAddressBlock = isAddressBlock ? "block" : "";
        logger.debug("Unbanned address {} {}", addressOrAddressBlock ,address);
    }

    /**
     * Adds a network address block to the set of banned blocks
     *
     * @param addressBlock   the address block to be banned
     */
    public void banAddressBlock(InetAddressCidrBlock addressBlock) {
        this.addressTable.addAddressBlock(addressBlock);
    }

    /**
     * Removes a network address block from the set of banned blocks
     *
     * @param addressBlock   the address block to be removed
     */
    public void unbanAddressBlock(InetAddressCidrBlock addressBlock) {
        this.addressTable.removeAddressBlock(addressBlock);
    }

    /**
     * Returns the list of peer scoring information
     * It contains the information recorded by node id and by address
     *
     * @return  the list of peer scoring information
     */
    public List<PeerScoringInformation> getPeersInformation() {
        synchronized (accessLock) {
            List<PeerScoringInformation> list = new ArrayList<>(this.peersByNodeID.size() + this.peersByAddress.size());

            list.addAll(this.peersByNodeID.entrySet().stream().map(entry -> PeerScoringInformation.buildByScoring(entry.getValue(), ByteUtil.toHexString(entry.getKey().getID()).substring(0, 8), "node")).collect(Collectors.toList()));
            list.addAll(this.peersByAddress.entrySet().stream().map(entry -> PeerScoringInformation.buildByScoring(entry.getValue(), entry.getKey().getHostAddress(), "address")).collect(Collectors.toList()));

            return list;
        }
    }

    /**
     * Returns the list of banned addresses, represented by a textual description
     * The list includes the banned addresses and the banned blocks
     *
     * @return a list of strings describing the banned addresses and blocks
     */
    public List<String> getBannedAddresses() {
        List<String> list = new ArrayList<>();

        list.addAll(this.addressTable.getAddressList().stream().map(InetAddress::getHostAddress).collect(Collectors.toList()));
        list.addAll(this.addressTable.getAddressBlockList().stream().map(InetAddressCidrBlock::getDescription).collect(Collectors.toList()));

        return list;
    }

    public void clearPeerScoring(InetAddress address) {
        if (this.peersByAddress.remove(address) != null) {
            logger.debug("Address {} scoring correctly cleared", address.getHostName());
        } else {
            logger.debug("Could not clear address {} scoring", address.getHostName());
        }
    }

    public void clearPeerScoring(NodeID nodeID) {
        if (this.peersByNodeID.remove(nodeID) != null) {
            logger.debug("nodeID {} scoring correctly cleared", nodeID);
        } else {
            logger.debug("Could not clear nodeID {} scoring", nodeID);
        }
    }

    @VisibleForTesting
    public boolean isEmpty() {
        synchronized (accessLock) {
            return this.peersByAddress.isEmpty() && this.peersByNodeID.isEmpty();
        }
    }

    @VisibleForTesting
    public PeerScoring getPeerScoring(NodeID id) {
        synchronized (accessLock) {
            if (peersByNodeID.containsKey(id)) {
                return peersByNodeID.get(id);
            }

            return peerScoringFactory.newInstance();
        }
    }

    @VisibleForTesting
    public PeerScoring getPeerScoring(InetAddress address) {
        synchronized (accessLock) {
            if (peersByAddress.containsKey(address)) {
                return peersByAddress.get(address);
            }

            return peerScoringFactory.newInstance();
        }
    }

    /**
     * Records an event and starts punishment if needed
     * @param peerScoring the peer scoring
     * @param event an event type
     * @param punishmentCalculator calculator to use
     * @param nodeID a node id
     */
    private void recordEventAndStartPunishment(PeerScoring peerScoring, EventType event, PunishmentCalculator punishmentCalculator, NodeID nodeID) {
        peerScoring.updateScoring(event);

        boolean hasBadReputationAlready = !peerScoring.refreshReputationAndPunishment();
        if (hasBadReputationAlready) {
            return;
        }

        boolean shouldStartPunishment = !scoringCalculator.hasGoodScore(peerScoring);
        if (shouldStartPunishment) {
            long punishmentTime = punishmentCalculator.calculate(peerScoring.getPunishmentCounter(), peerScoring.getScore());
            peerScoring.startPunishment(punishmentTime);

            String nodeIDFormated = nodeIdForLog(nodeID);
            logger.debug("NodeID {} has been punished for {} milliseconds. Last event {}", nodeIDFormated, punishmentTime, event);
            logger.debug("{}", PeerScoringInformation.buildByScoring(peerScoring, nodeIDFormated, ""));
        }
    }

    private String nodeIdForLog(NodeID id) {
        if(id == null) {
            return "NO_NODE_ID";
        }
        return id.toString();
    }

    private String addressForLog(InetAddress address) {
        if(address == null) {
            return "NO_ADDRESS";
        }
        return address.getHostAddress();
    }

    private void banAddressOrThrow(String address) {
        try {
            banAddress(address);
        } catch (InvalidInetAddressException e) {
            throw new IllegalArgumentException("Invalid address", e);
        }
    }
}
