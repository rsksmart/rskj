package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageChannel;
import co.rsk.net.sync.DownloadingBodiesSyncState;
import co.rsk.net.sync.DownloadingHeadersSyncState;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.utils.StatusUtils;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.DummyBlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessorTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    public static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config);

    @Test
    public void noPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        Assert.assertEquals(0, processor.getPeersCount());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Assert.assertTrue(processor.getKnownPeersNodeIDs().isEmpty());
    }

    @Test
    public void processStatusWithAdvancedPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final List<Message> messages = new ArrayList<>();
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            messages.add(invocation.getArgumentAt(1, Message.class));
            return true;
        });

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));

        Assert.assertFalse(messages.isEmpty());
        Assert.assertEquals(1, messages.size());

        Message message = messages.get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    public void syncWithAdvancedPeerAfterTimeoutWaitingPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final List<Message> messages = new ArrayList<>();
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            messages.add(invocation.getArgumentAt(1, Message.class));
            return true;
        });

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));
        Assert.assertTrue(messages.isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assert.assertTrue(messages.isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assert.assertFalse(messages.isEmpty());
        Assert.assertEquals(1, messages.size());

        Message message = messages.get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage) message;

        Assert.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    public void dontSyncWithoutAdvancedPeerAfterTimeoutWaitingPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(0, hash, parentHash, blockchain.getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.DEFAULT, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void syncWithAdvancedStatusAnd5Peers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        final ChannelManager channelManager = mock(ChannelManager.class);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.DEFAULT, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        List<SimpleMessageChannel> senders = new ArrayList<>();
        List<Channel> channels = new ArrayList<>();
        Map<NodeID, List<Message>> messagesByNode = new HashMap<>();

        int lessPeers = SyncConfiguration.DEFAULT.getExpectedPeers() - 1;
        for (int i = 0; i < lessPeers; i++) {
            SimpleMessageChannel sender = new SimpleMessageChannel();
            senders.add(sender);
            Channel channel = mock(Channel.class);
            messagesByNode.put(sender.getPeerNodeID(), new ArrayList<>());
            when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
            when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
            when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
                messagesByNode.get(sender.getPeerNodeID()).add(invocation.getArgumentAt(1, Message.class));
                return true;
            });
        }

        messagesByNode.values().forEach(m -> Assert.assertTrue(m.isEmpty()));
        senders.forEach(s -> processor.processStatus(s, status));
        messagesByNode.values().forEach(m -> Assert.assertTrue(m.isEmpty()));

        Assert.assertEquals(lessPeers, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        senders.stream()
                .map(SimpleMessageChannel::getPeerNodeID)
                .forEach(peerId -> Assert.assertTrue(ids.contains(peerId)));

        SimpleMessageChannel lastSender = new SimpleMessageChannel();
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(lastSender.getPeerNodeID());
        Assert.assertFalse(ids.contains(lastSender.getPeerNodeID()));

        processor.processStatus(lastSender, status);

        // now test with all senders
        senders.add(lastSender);
        Assert.assertTrue(ids.contains(lastSender.getPeerNodeID()));
        Assert.assertFalse(messagesByNode.values().stream().allMatch(m -> m.isEmpty()));
        Assert.assertEquals(1, messagesByNode.values().stream()
                .mapToInt(List::size)
                .sum());

        Message message = messagesByNode.values().stream().filter(m -> !m.isEmpty())
                .findFirst()
                .get().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    public void processStatusWithPeerWithSameDifficulty() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(100);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(blockchain.getStatus().getBestBlockNumber(), hash, parentHash, blockchain.getStatus().getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Assert.assertTrue(sender.getMessages().isEmpty());

        // is null when we're not syncing
        Assert.assertEquals(0, processor.getExpectedResponses().size());
        Assert.assertFalse(processor.isPeerSyncing(sender.getPeerNodeID()));
    }

    @Test
    public void sendSkeletonRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(100);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final Message[] msg = new Message[1];
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            msg[0] = invocation.getArgumentAt(1, Message.class);
            return true;
        });

        SyncProcessor processor = new SyncProcessor(config, blockchain, null, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        processor.sendSkeletonRequest(sender.getPeerNodeID(), 0);

        Message message = msg[0];
        Assert.assertNotNull(message);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(0, request.getStartNumber());

        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    public void sendBlockHashRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final Message[] msg = new Message[1];
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            msg[0] = invocation.getArgumentAt(1, Message.class);
            return true;
        });

        SyncProcessor processor = new SyncProcessor(config, blockchain, null, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        Assert.assertFalse(processor.isPeerSyncing(sender.getPeerNodeID()));

        processor.sendBlockHashRequest(100);

        Message message = msg[0];

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(100, request.getHeight());

        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test(expected = Exception.class)
    public void processBlockHashResponseWithUnknownHash() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });


        SyncProcessor processor = new SyncProcessor(config, blockchain, null, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.processStatus(sender, new Status(100, null));
    }

    @Test
    public void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        List<BlockHeader> headers = new ArrayList<>();
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(0, sender.getMessages().size());
        Assert.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(0, sender.getMessages().size());
        Assert.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithManyHeadersMissingFirstParent() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain otherBlockchain = BlockChainBuilder.ofSize(10, true);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 10; k >= 2; k--)
            headers.add(otherBlockchain.getBlockByNumber(k).getHeader());

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(0, sender.getMessages().size());
        Assert.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithOneExistingHeader() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(0, sender.getMessages().size());
        Assert.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    public void processBodyResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        BodyResponseMessage response = new BodyResponseMessage(new Random().nextLong(), null, null);
        processor.registerExpectedMessage(response);

        processor.processBodyResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseAddsToBlockchain() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Deque<BlockHeader> headerStack = new ArrayDeque<>();
        headerStack.add(block.getHeader());
        List<Deque<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash().getBytes(), 0));
        bids.add(new BlockIdentifier(block.getHash().getBytes(), 1));

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender.getPeerNodeID(), bids));
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void doesntProcessInvalidBodyResponse() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        Account senderAccount = createAccount("sender");
        Account receiverAccount = createAccount("receiver");
        Transaction tx = createTransaction(senderAccount, receiverAccount, BigInteger.valueOf(1000000), BigInteger.ZERO);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, txs, uncles);
        processor.registerExpectedMessage(response);

        Deque<BlockHeader> headerStack = new ArrayDeque<>();
        headerStack.add(block.getHeader());
        headerStack.add(block.getHeader());
        headerStack.add(block.getHeader());
        List<Deque<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash().getBytes(), 0));
        bids.add(new BlockIdentifier(block.getHash().getBytes(), 1));

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender.getPeerNodeID(), bids));
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertNotEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
        // if an unexpected body arrives then stops syncing
        Assert.assertFalse(processor.getSyncState().isSyncing());
    }

    @Test
    public void doesntProcessUnexpectedBodyResponse() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Blockchain extended = BlockChainBuilder.copy(blockchain);
        extended.tryToConnect(block);

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Deque<BlockHeader> headerStack = new ArrayDeque<>();
        headerStack.add(block.getHeader());
        List<Deque<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        int connectionPoint = 10;
        int step = 192;
        int linkCount = 1;
        processor.startDownloadingBodies(headers,
                Collections.singletonMap(sender.getPeerNodeID(),
                        buildSkeleton(extended, connectionPoint, step, linkCount)));
//        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertNotEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
        // if an invalid body arrives then stops syncing
        Assert.assertFalse(processor.getSyncState().isSyncing());
    }

    @Test
    public void processBodyResponseWithTransactionAddsToBlockchain() {
        Account senderAccount = createAccount("sender");
        Account receiverAccount = createAccount("receiver");

        List<Account> accounts = new ArrayList<Account>();
        List<Coin> balances = new ArrayList<>();

        accounts.add(senderAccount);
        balances.add(Coin.valueOf(20000000));
        accounts.add(receiverAccount);
        balances.add(Coin.ZERO);

        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0, false, accounts, balances);

        Block genesis = blockchain.getBestBlock();
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        Transaction tx = createTransaction(senderAccount, receiverAccount, BigInteger.valueOf(1000000), BigInteger.ZERO);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block block = new BlockGenerator().createChildBlock(genesis, txs, blockchain.getRepository().getRoot());

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);
        Assert.assertEquals(1, block.getTransactionsList().size());
        blockExecutor.executeAndFillAll(block, genesis);
        Assert.assertEquals(21000, block.getFeesPaidToMiner().asBigInteger().intValueExact());
        Assert.assertEquals(1, block.getTransactionsList().size());

        Assert.assertEquals(1, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = block.getTransactionsList();
        List<BlockHeader> uncles = block.getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Deque<BlockHeader> headerStack = new ArrayDeque<>();
        headerStack.add(block.getHeader());
        List<Deque<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash().getBytes(), 0));
        bids.add(new BlockIdentifier(block.getHash().getBytes(), 1));

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender.getPeerNodeID(), bids));
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockResponseAddsToBlockchain() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        BlockResponseMessage response = new BlockResponseMessage(new Random().nextLong(), block);
        processor.registerExpectedMessage(response);

        processor.processBlockResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain advancedBlockchain = BlockChainBuilder.ofSize(100);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final List<Message> messages = new ArrayList<>();
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            messages.add(invocation.getArgumentAt(1, Message.class));
            return true;
        });

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new DummyBlockValidationRule(), DIFFICULTY_CALCULATOR);

        processor.processStatus(sender, StatusUtils.fromBlockchain(advancedBlockchain));
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage)messages.get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] expectedHeights = new long[] { 50, 25, 12, 6, 3, 1 };

        for (int k = 0; k < expectedHeights.length; k++) {
            Assert.assertEquals( k + 2, messages.size());
            Message message = messages.get(k + 1);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(expectedHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(expectedHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash().getBytes()));
        }

        Assert.assertEquals(expectedHeights.length + 2, messages.size());

        Message message = messages.get(messages.size() - 1);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertEquals(0, request.getStartNumber());
        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    public void findConnectionPointBlockchainWith30BlocksVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(30);
        Blockchain advancedBlockchain = BlockChainBuilder.copyAndExtend(blockchain, 70);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final List<Message> messages = new ArrayList<>();
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            messages.add(invocation.getArgumentAt(1, Message.class));
            return true;
        });

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new DummyBlockValidationRule(), DIFFICULTY_CALCULATOR);

        Status status = StatusUtils.fromBlockchain(advancedBlockchain);
        processor.processStatus(sender, status);
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage)messages.get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] binarySearchHeights = new long[] { 50, 25, 37, 31, 28, 29, 30 };
        for (int k = 0; k < binarySearchHeights.length; k++) {
            Assert.assertEquals(k + 2, messages.size());
            Message message = messages.get(k + 1);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(binarySearchHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(binarySearchHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash().getBytes()));
        }

        Assert.assertEquals(binarySearchHeights.length + 2, messages.size());

        Message message = messages.get(messages.size() - 1);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertEquals(30, request.getStartNumber());
        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithTenBlockIdentifiers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final Message[] msg = new Message[1];
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            msg[0] = invocation.getArgumentAt(1, Message.class);
            return true;
        });

        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        int connectionPoint = 0;
        int step = 10;
        int linkCount = 9;
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), connectionPoint);
        processor.startDownloadingSkeleton(connectionPoint);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Message message = msg[0];

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertArrayEquals(blockIdentifiers.get(1).getHash(), request.getHash());
        Assert.assertEquals(10, request.getCount());

        DownloadingHeadersSyncState syncState = (DownloadingHeadersSyncState) processor.getSyncState();
        List<BlockIdentifier> skeleton = syncState.getSkeleton();

        Assert.assertEquals(10, skeleton.size());
        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithoutBlockIdentifiers() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new BlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration);
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), getChannelManager(),
                syncConfiguration, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);

        List<BlockIdentifier> blockIdentifiers = new ArrayList<>();

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Assert.assertFalse(processor.isPeerSyncing(sender.getPeerNodeID()));
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void processSkeletonResponseWithConnectionPoint() {
        Blockchain blockchain = BlockChainBuilder.ofSize(25);

        final BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Channel channel = mock(Channel.class);
        when(channel.getNodeId()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        final Message[] msg = new Message[1];
        when(channelManager.sendMessageTo(eq(sender.getPeerNodeID()), any())).then((InvocationOnMock invocation) -> {
            msg[0] = invocation.getArgumentAt(1, Message.class);
            return true;
        });
        SyncProcessor processor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), channelManager,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), DIFFICULTY_CALCULATOR);

        int connectionPoint = 25;
        int step = 10;
        int linkCount = 10;
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), connectionPoint);
        processor.startDownloadingSkeleton(connectionPoint);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Message message = msg[0];

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(5, request.getCount());
        Assert.assertArrayEquals(blockIdentifiers.get(1).getHash(), request.getHash());
        Assert.assertEquals(1, processor.getExpectedResponses().size());
    }

    private List<BlockIdentifier> buildSkeleton(Blockchain blockchain, int connectionPoint, int step, int linkCount) {
        List<BlockIdentifier> bids = new ArrayList<>();
        int start = connectionPoint - (connectionPoint % step);
        for (int k = 0; k < linkCount; k++) {
            int number = start + k * step;
            Block block = blockchain.getBlockByNumber(number);

            byte[] hash;

            if (block != null)
                hash = block.getHash().getBytes();
            else
                hash = HashUtil.randomHash();

            bids.add(new BlockIdentifier(hash, number));
        }
        BlockIdentifier bid = new BlockIdentifier(blockchain.getBestBlockHash(), blockchain.getBestBlock().getNumber());
        bids.add(bid);
        return bids;
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = Transaction.create(config, toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }

    private PeerScoringManager getPeerScoringManager() {
        return RskMockFactory.getPeerScoringManager();
    }

    private ChannelManager getChannelManager() {
        return new SimpleChannelManager();
    }
}
