package co.rsk.scoring;

import co.rsk.net.NodeID;
import org.ethereum.TestUtils;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 28/06/2017.
 */
class PeerScoringManagerTest {

    private final Random random = new Random(111);

    private static final int PUNISHMENT_DURATION = 100;
    private static final int PUNISHMENT_INCREMENT_RATE = 10;

    @Test
    void isAddressBanned_NoBannedPeers_ShouldNotBeBanned() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        Assertions.assertFalse(manager.isAddressBanned(address));
        Assertions.assertTrue(manager.hasGoodReputation(address));
    }

    @Test
    void isAddressBanned_AddressIsBanned_ShouldBeBannedWithBadReputation() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager(100, Collections.singleton(address.getHostAddress()), Collections.emptyList());

        Assertions.assertTrue(manager.isAddressBanned(address));
        Assertions.assertFalse(manager.hasGoodReputation(address));
    }

    @Test
    void isNodeIDBanned_NoBannedPeers_ShouldNotBeBanned() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        Assertions.assertFalse(manager.isNodeIDBanned(id));
        Assertions.assertTrue(manager.hasGoodReputation(id));
    }

    @Test
    void isNodeIDBanned_NodeIDIsBanned_ShouldBeBannedWithBadReputation() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager(100, Collections.emptyList(), Collections.singleton(ByteUtil.toHexString(id.getID())));

        Assertions.assertTrue(manager.isNodeIDBanned(id));
        Assertions.assertFalse(manager.hasGoodReputation(id));
    }

    @Test
    void getEmptyNodeStatusFromUnknownNodeId() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        PeerScoring result = manager.getPeerScoring(id);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void addBannedAddress() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.banAddress(address);
        Assertions.assertFalse(manager.hasGoodReputation(address));
    }

    @Test
    void addBannedAddressBlock() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddressCidrBlock addressBlock = new InetAddressCidrBlock(address, 8);

        PeerScoringManager manager = createPeerScoringManager();

        manager.banAddressBlock(addressBlock);
        Assertions.assertFalse(manager.hasGoodReputation(address));
    }

    @Test
    void addAndRemoveBannedAddress() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.banAddress(address);
        Assertions.assertFalse(manager.hasGoodReputation(address));
        manager.unbanAddress(address);
        Assertions.assertTrue(manager.hasGoodReputation(address));
    }

    @Test
    void addAndRemoveBannedAddressBlock() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddressCidrBlock addressBlock = new InetAddressCidrBlock(address, 8);

        PeerScoringManager manager = createPeerScoringManager();

        manager.banAddressBlock(addressBlock);
        Assertions.assertFalse(manager.hasGoodReputation(address));
        manager.unbanAddressBlock(addressBlock);
        Assertions.assertTrue(manager.hasGoodReputation(address));
    }

    @Test
    void newNodeHasGoodReputation() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        Assertions.assertTrue(manager.hasGoodReputation(id));
    }

    @Test
    void recordEventUsingNodeID() {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(id, null, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(id);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    void newAddressHasGoodReputation() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        Assertions.assertTrue(manager.hasGoodReputation(address));
    }

    @Test
    void recordEventUsingNodeIDAndAddress() throws UnknownHostException {
        NodeID id = generateNodeID();
        InetAddress address = generateIPAddressV4();

        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(id, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(id);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, result.getTotalEventCounter());

        result = manager.getPeerScoring(address);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    void recordEventUsingIPV4Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(address);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    void invalidBlockGivesBadReputationToNode() throws UnknownHostException {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(id, null, EventType.INVALID_BLOCK);

        Assertions.assertFalse(manager.hasGoodReputation(id));

        Assertions.assertNotEquals(0, manager.getPeerScoring(id).getTimeLostGoodReputation());
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void notGoodReputationByNodeIDExpires() throws UnknownHostException, InterruptedException {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(id, null, EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, manager.getPeerScoring(id).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertFalse(manager.hasGoodReputation(id));
        Assertions.assertNotEquals(0, manager.getPeerScoring(id).getTimeLostGoodReputation());

        Assertions.assertFalse(manager.hasGoodReputation(id));
        Assertions.assertNotEquals(0, manager.getPeerScoring(id).getTimeLostGoodReputation());
        Assertions.assertEquals(1, manager.getPeerScoring(id).getEventCounter(EventType.INVALID_BLOCK));

        TimeUnit.MILLISECONDS.sleep(100);

        Assertions.assertTrue(manager.hasGoodReputation(id));
        Assertions.assertEquals(0, manager.getPeerScoring(id).getTimeLostGoodReputation());
        Assertions.assertEquals(0, manager.getPeerScoring(id).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertTrue(manager.getPeerScoring(id).isEmpty());
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void notGoodReputationByAddressExpires() throws UnknownHostException, InterruptedException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertFalse(manager.hasGoodReputation(address));
        Assertions.assertNotEquals(0, manager.getPeerScoring(address).getTimeLostGoodReputation());

        Assertions.assertFalse(manager.hasGoodReputation(address));
        Assertions.assertNotEquals(0, manager.getPeerScoring(address).getTimeLostGoodReputation());
        Assertions.assertEquals(1, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));

        TimeUnit.MILLISECONDS.sleep(100);

        Assertions.assertTrue(manager.hasGoodReputation(address));
        Assertions.assertEquals(0, manager.getPeerScoring(address).getTimeLostGoodReputation());
        Assertions.assertEquals(0, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertTrue(manager.getPeerScoring(address).isEmpty());
    }

    @Test
    void firstPunishment() throws UnknownHostException, InterruptedException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, manager.getPeerScoring(address).getPunishmentCounter());
        Assertions.assertEquals(PUNISHMENT_DURATION, manager.getPeerScoring(address).getPunishmentTime());
        Assertions.assertFalse(manager.hasGoodReputation(address));
    }

    @Test
    void finishPunishment() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);
        Assertions.assertFalse(manager.hasGoodReputation(address));

        PeerScoring scoring = manager.getPeerScoring(address);
        long initialTimeLostGoodReputation = scoring.getTimeLostGoodReputation();
        long initialPunishmentTime = scoring.getPunishmentTime();

        manager.recordEvent(null, address, EventType.SUCCESSFUL_HANDSHAKE);
        Assertions.assertFalse(manager.hasGoodReputation(address), "Reputation should still be bad after event is received while punished");
        Assertions.assertEquals(scoring.getTimeLostGoodReputation(), initialTimeLostGoodReputation, "TimeLostGoodReputation value should remain the same after event is received while punished");
        Assertions.assertEquals(scoring.getPunishmentTime(), initialPunishmentTime, "PunishmentTime value should remain the same after event is received while punished");

        // simulate node was punished earlier so enough time has passed for node to be forgiven
        TestUtils.setInternalState(scoring, "timeLostGoodReputation", initialTimeLostGoodReputation - 2 * 60 * 1000);

        manager.recordEvent(null, address, EventType.SUCCESSFUL_HANDSHAKE);
        Assertions.assertTrue(manager.hasGoodReputation(address), "Reputation should be good after enough time has passed");
        Assertions.assertEquals(0, scoring.getTimeLostGoodReputation(), "TimeLostGoodReputation value should be 0 after good reputation is recovered");
        Assertions.assertEquals(0, scoring.getPunishmentTime(), "PunishmentTime value should be 0 after good reputation is recovered");
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void secondPunishment() throws UnknownHostException, InterruptedException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(0, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(PUNISHMENT_DURATION, manager.getPeerScoring(address).getPunishmentTime());
        Assertions.assertFalse(manager.hasGoodReputation(address));

        TimeUnit.MILLISECONDS.sleep(100);

        Assertions.assertTrue(manager.hasGoodReputation(address));

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(0, manager.getPeerScoring(address).getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(2, manager.getPeerScoring(address).getPunishmentCounter());
        Assertions.assertEquals(-1, manager.getPeerScoring(address).getScore());
        Assertions.assertEquals(PUNISHMENT_DURATION + (PUNISHMENT_DURATION / PUNISHMENT_INCREMENT_RATE), manager.getPeerScoring(address).getPunishmentTime());
        Assertions.assertFalse(manager.hasGoodReputation(address));
    }

    @Test
    void invalidTransactionGivesNoBadReputationToNode() throws UnknownHostException {
        NodeID id = generateNodeID();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(id, null, EventType.INVALID_TRANSACTION);

        Assertions.assertTrue(manager.hasGoodReputation(id));
        Assertions.assertEquals(0, manager.getPeerScoring(id).getTimeLostGoodReputation());
    }

    @Test
    void invalidBlockGivesBadReputationToAddress() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        Assertions.assertFalse(manager.hasGoodReputation(address));

        Assertions.assertNotEquals(0, manager.getPeerScoring(address).getTimeLostGoodReputation());
    }

    @Test
    void invalidTransactionGivesNoBadReputationToAddress() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_TRANSACTION);

        Assertions.assertTrue(manager.hasGoodReputation(address));
        Assertions.assertEquals(0, manager.getPeerScoring(address).getTimeLostGoodReputation());
    }

    @Test
    void recordEventUsingIPV6Address() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.INVALID_BLOCK);

        PeerScoring result = manager.getPeerScoring(address);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(1, result.getTotalEventCounter());
    }

    @Test
    void managesOnlyThreeNodes() {
        PeerScoringManager manager = createPeerScoringManager(3);

        NodeID node1 = generateNodeID();
        NodeID node2 = generateNodeID();
        NodeID node3 = generateNodeID();

        manager.recordEvent(node1, null, EventType.INVALID_BLOCK);

        Assertions.assertFalse(manager.getPeerScoring(node1).refreshReputationAndPunishment());
        manager.recordEvent(node2, null, EventType.INVALID_BLOCK);
        manager.recordEvent(node3, null, EventType.INVALID_BLOCK);

        NodeID node4 = generateNodeID();

        manager.recordEvent(node4, null, EventType.INVALID_BLOCK);

        Assertions.assertTrue(manager.getPeerScoring(node1).refreshReputationAndPunishment());
        Assertions.assertFalse(manager.getPeerScoring(node2).refreshReputationAndPunishment());
        Assertions.assertFalse(manager.getPeerScoring(node3).refreshReputationAndPunishment());
        Assertions.assertFalse(manager.getPeerScoring(node4).refreshReputationAndPunishment());
    }

    @Test
    void getPeersInformationFromEmptyManager() {
        PeerScoringManager manager = createPeerScoringManager();

        List<PeerScoringInformation> result = manager.getPeersInformation();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void getPeersInformationFromManagerWithOneEvent() throws UnknownHostException {
        PeerScoringManager manager = createPeerScoringManager();
        NodeID node = generateNodeID();
        InetAddress address = generateIPAddressV4();

        manager.recordEvent(node, address, EventType.VALID_BLOCK);

        List<PeerScoringInformation> result = manager.getPeersInformation();

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(2, result.size());

        PeerScoringInformation info = result.get(0);
        Assertions.assertEquals(ByteUtil.toHexString(node.getID()).substring(0, 8), info.getId());
        Assertions.assertEquals(1, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertEquals(0, info.getPunishments());
        Assertions.assertEquals(0, info.getSuccessfulHandshakes());
        Assertions.assertTrue(info.getScore() > 0);

        info = result.get(1);
        Assertions.assertEquals(address.getHostAddress(), info.getId());
        Assertions.assertEquals(1, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertEquals(0, info.getPunishments());
        Assertions.assertEquals(0, info.getSuccessfulHandshakes());
        Assertions.assertTrue(info.getScore() > 0);
    }

    @Test
    void getPeersInformationFromManagerWithThreeEvents() throws UnknownHostException {
        PeerScoringManager manager = createPeerScoringManager();
        NodeID node = generateNodeID();
        InetAddress address = generateIPAddressV4();

        manager.recordEvent(node, address, EventType.VALID_BLOCK);
        manager.recordEvent(node, address, EventType.VALID_TRANSACTION);
        manager.recordEvent(node, address, EventType.VALID_BLOCK);

        List<PeerScoringInformation> result = manager.getPeersInformation();

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(2, result.size());

        PeerScoringInformation info = result.get(0);
        Assertions.assertEquals(ByteUtil.toHexString(node.getID()).substring(0, 8), info.getId());
        Assertions.assertEquals(2, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(1, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertEquals(0, info.getPunishments());
        Assertions.assertEquals(0, info.getSuccessfulHandshakes());
        Assertions.assertTrue(info.getScore() > 0);

        info = result.get(1);
        Assertions.assertEquals(address.getHostAddress(), info.getId());
        Assertions.assertEquals(2, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(1, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertEquals(0, info.getPunishments());
        Assertions.assertEquals(0, info.getSuccessfulHandshakes());
        Assertions.assertTrue(info.getScore() > 0);
    }

    @Test
    void recordTimeoutIsNeutralEvent() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        PeerScoringManager manager = createPeerScoringManager();

        manager.recordEvent(null, address, EventType.TIMEOUT_MESSAGE);

        PeerScoring result = manager.getPeerScoring(address);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.getEventCounter(EventType.TIMEOUT_MESSAGE));
        Assertions.assertEquals(1, result.getTotalEventCounter());
        Assertions.assertEquals(0, result.getScore());
        Assertions.assertTrue(result.refreshReputationAndPunishment());
    }

    @Test
    void clearPeerInformationByAddress() throws UnknownHostException {
        PeerScoringManager manager = createPeerScoringManager();
        NodeID node = generateNodeID();
        InetAddress address = generateIPAddressV4();

        manager.recordEvent(node, address, EventType.VALID_BLOCK);
        manager.recordEvent(node, address, EventType.VALID_TRANSACTION);
        manager.recordEvent(node, address, EventType.VALID_BLOCK);

        List<PeerScoringInformation> result = manager.getPeersInformation();

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(ByteUtil.toHexString(node.getID()).startsWith(result.get(0).getId()));
        Assertions.assertEquals(address.getHostAddress(), result.get(1).getId());

        // clear by nodeId
        manager.clearPeerScoring(node);

        result = manager.getPeersInformation();
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(address.getHostAddress(), result.get(0).getId());

        // clear by address
        manager.clearPeerScoring(address);

        result = manager.getPeersInformation();
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    private NodeID generateNodeID() {
        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        return new NodeID(bytes);
    }

    private InetAddress generateIPAddressV4() throws UnknownHostException {
        byte[] bytes = new byte[4];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private InetAddress generateIPAddressV6() throws UnknownHostException {
        byte[] bytes = new byte[16];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static PeerScoringManager createPeerScoringManager() {
        return createPeerScoringManager(100);
    }

    private static PeerScoringManager createPeerScoringManager(int nnodes) {
        return createPeerScoringManager(nnodes,
                Collections.emptyList(),
                Collections.emptyList());
    }

    private static PeerScoringManager createPeerScoringManager(int nnodes,
                                                               Collection<String> bannedPeerIPs,
                                                               Collection<String> bannedPeerIDs) {
        return new PeerScoringManager(
                PeerScoring::new,
                nnodes,
                new PunishmentParameters(PUNISHMENT_DURATION, PUNISHMENT_INCREMENT_RATE, 1000),
                new PunishmentParameters(PUNISHMENT_DURATION, PUNISHMENT_INCREMENT_RATE, 1000),
                bannedPeerIPs,
                bannedPeerIDs
        );
    }
}
