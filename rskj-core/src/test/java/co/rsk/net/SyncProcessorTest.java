package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.*;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimplePeer;
import co.rsk.net.sync.*;
import co.rsk.net.utils.StatusUtils;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RskMockFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 29/08/2017.
 */
class SyncProcessorTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    public static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());

    @Test
    void noPeers() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final EthereumListener listener = mock(EthereumListener.class);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, getPeerScoringManager()),
                mock(Genesis.class),
                listener);

        Assertions.assertEquals(0, processor.getPeersCount());
        Assertions.assertEquals(0, processor.getNoAdvancedPeers());

        Assertions.assertTrue(processor.getKnownPeersNodeIDs().isEmpty());
        verify(listener, never()).onLongSyncStarted();
        verify(listener, never()).onLongSyncDone();
    }

    @Test
    void processStatusWithAdvancedPeers() {
        final NetBlockStore store = new NetBlockStore();
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.ofSize(0);
        BlockStore blockStore = builder.getBlockStore();
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SimplePeer sender = new SimplePeer(new byte[]{0x01});
        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
        processor.processStatus(sender, status);

        Assertions.assertEquals(1, processor.getPeersCount());
        Assertions.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assertions.assertFalse(ids.isEmpty());
        Assertions.assertTrue(ids.contains(sender.getPeerNodeID()));

        List<Message> messages = sender.getMessages();
        Assertions.assertFalse(messages.isEmpty());
        Assertions.assertEquals(1, messages.size());

        Message message = messages.get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assertions.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    void syncWithAdvancedPeerAfterTimeoutWaitingPeers() {
        final NetBlockStore store = new NetBlockStore();
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.ofSize(0);
        BlockStore blockStore = builder.getBlockStore();
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;

        SimplePeer sender = new SimplePeer(new byte[]{0x01});
        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
        processor.processStatus(sender, status);

        Assertions.assertEquals(1, processor.getPeersCount());
        Assertions.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assertions.assertFalse(ids.isEmpty());
        Assertions.assertTrue(ids.contains(sender.getPeerNodeID()));

        List<Message> messages = sender.getMessages();
        Assertions.assertTrue(messages.isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assertions.assertTrue(messages.isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assertions.assertFalse(messages.isEmpty());
        Assertions.assertEquals(1, messages.size());

        Message message = sender.getMessages().get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage) message;

        Assertions.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    void dontSyncWithoutAdvancedPeerAfterTimeoutWaitingPeers() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(0, hash, parentHash, blockchain.getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        Genesis genesisBlock = mock(Genesis.class);
        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getMinNumber()).thenReturn(0L);

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.DEFAULT, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.DEFAULT, blockchain, RskMockFactory.getPeerScoringManager()),
                genesisBlock,
                mock(EthereumListener.class));

        SimplePeer sender = new SimplePeer(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assertions.assertEquals(1, processor.getPeersCount());
        Assertions.assertEquals(0, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assertions.assertFalse(ids.isEmpty());
        Assertions.assertTrue(ids.contains(sender.getPeerNodeID()));
        Assertions.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assertions.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assertions.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void syncWithAdvancedStatusAnd5Peers() {
        final NetBlockStore store = new NetBlockStore();
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.ofSize(0);
        BlockStore blockStore = builder.getBlockStore();
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(new BlockDifficulty(BigInteger.TEN)));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        final ChannelManager channelManager = mock(ChannelManager.class);
        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.DEFAULT, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.DEFAULT, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<SimplePeer> senders = new ArrayList<>();

        int lessPeers = SyncConfiguration.DEFAULT.getExpectedPeers() - 1;
        for (int i = 0; i < lessPeers; i++) {
            SimplePeer sender = new SimplePeer();
            senders.add(sender);
            when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));
        }

        Assertions.assertTrue(senders.stream().map(SimplePeer::getMessages).allMatch(List::isEmpty));
        senders.forEach(s -> processor.processStatus(s, status));
        Assertions.assertTrue(senders.stream().map(SimplePeer::getMessages).allMatch(List::isEmpty));

        Assertions.assertEquals(lessPeers, processor.getNoAdvancedPeers());

        Set<NodeID> knownPeersNodeIDs = processor.getKnownPeersNodeIDs();
        Assertions.assertTrue(senders.stream()
                .map(SimplePeer::getPeerNodeID)
                .allMatch(knownPeersNodeIDs::contains));

        SimplePeer lastSender = new SimplePeer();
        Assertions.assertFalse(processor.getKnownPeersNodeIDs().contains(lastSender.getPeerNodeID()));

        processor.processStatus(lastSender, status);

        // now test with all senders
        senders.add(lastSender);
        Set<NodeID> ids = processor.getKnownPeersNodeIDs();

        Assertions.assertTrue(ids.contains(lastSender.getPeerNodeID()));
        Assertions.assertFalse(senders.stream().map(SimplePeer::getMessages).allMatch(List::isEmpty));
        Assertions.assertEquals(1, senders.stream()
                .map(SimplePeer::getMessages)
                .mapToInt(List::size)
                .sum());

        Message message = senders.stream()
                .map(SimplePeer::getMessages)
                .filter(m -> !m.isEmpty())
                .findFirst()
                .get()
                .get(0);

        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage) message;

        Assertions.assertEquals(status.getBestBlockHash(), request.getHash());
    }

    @Test
    void processStatusWithPeerWithSameDifficulty() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(blockchain.getStatus().getBestBlockNumber(), hash, parentHash, blockchain.getStatus().getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        BlockStore blockStore = mock(BlockStore.class);
        Genesis genesisBlock = mock(Genesis.class);
        when(blockStore.getMinNumber()).thenReturn(0L);

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                genesisBlock,
                mock(EthereumListener.class));

        processor.processStatus(sender, status);

        Assertions.assertEquals(1, processor.getPeersCount());
        Assertions.assertEquals(0, processor.getNoAdvancedPeers());

        Assertions.assertTrue(sender.getMessages().isEmpty());

        // is null when we're not syncing
        Assertions.assertEquals(0, processor.getExpectedResponses().size());
        Assertions.assertFalse(processor.isSyncing());
    }

    @Test
    void sendSkeletonRequest() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(100);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Peer channel = mock(Peer.class);
        when(channel.getPeerNodeID()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), null,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        processor.sendSkeletonRequest(sender, 0);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Message message = sender.getMessages().get(0);
        Assertions.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage) message;

        Assertions.assertNotEquals(0, request.getId());
        Assertions.assertEquals(0, request.getStartNumber());

        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void sendBlockHashRequest() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Peer channel = mock(Peer.class);
        when(channel.getPeerNodeID()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), null,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        processor.sendBlockHashRequest(sender, 100);


        Assertions.assertFalse(sender.getMessages().isEmpty());
        Message message = sender.getMessages().get(0);
        Assertions.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage) message;

        Assertions.assertNotEquals(0, request.getId());
        Assertions.assertEquals(100, request.getHeight());

        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void processBlockHashResponseWithUnknownHash() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), null,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        Assertions.assertThrows(Exception.class, () -> processor.processStatus(sender, new Status(100, null)));
    }

    @Test
    void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<BlockHeader> headers = new ArrayList<>();

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);
        processor.processBlockHeadersResponse(sender, response);
        Assertions.assertEquals(0, sender.getMessages().size());
        Assertions.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    void processBlockHeadersResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assertions.assertEquals(0, sender.getMessages().size());
        Assertions.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    void processBlockHeadersResponseWithManyHeadersMissingFirstParent() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        Blockchain otherBlockchain = new BlockChainBuilder().ofSize(10, true);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 10; k >= 2; k--)
            headers.add(otherBlockchain.getBlockByNumber(k).getHeader());

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assertions.assertEquals(0, sender.getMessages().size());
        Assertions.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    void processBlockHeadersResponseWithOneExistingHeader() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(new Random().nextLong(), headers);
        processor.registerExpectedMessage(response);

        processor.processBlockHeadersResponse(sender, response);
        Assertions.assertEquals(0, sender.getMessages().size());
        Assertions.assertEquals(0, processor.getExpectedResponses().size());
    }

    @Test
    void processBodyResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(3);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        BodyResponseMessage response = new BodyResponseMessage(new Random().nextLong(), null, null);
        processor.registerExpectedMessage(response);

        processor.processBodyResponse(sender, response);
        Assertions.assertTrue(sender.getMessages().isEmpty());
        Assertions.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    void processBodyResponseAddsToBlockchain() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
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

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender, bids), sender);
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assertions.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    void doesntProcessInvalidBodyResponse() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);
        final EthereumListener listener = mock(EthereumListener.class);

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                listener);
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

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender, bids), sender);
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertNotEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
        // if an unexpected body arrives then stops syncing
        Assertions.assertFalse(processor.isSyncing());
    }

    @Test
    void doesntProcessUnexpectedBodyResponse() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        SimplePeer sender = new SimplePeer(new byte[]{0x01});

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Blockchain extended = BlockChainBuilder.copy(blockchain);
        extended.tryToConnect(block);

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
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
                Collections.singletonMap(sender,
                        buildSkeleton(extended, connectionPoint, step, linkCount)), sender);

        processor.processBodyResponse(sender, response);

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assertions.assertNotEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
        // if an invalid body arrives then stops syncing
        Assertions.assertFalse(processor.isSyncing());
    }

    @Test
    void processBodyResponseWithTransactionAddsToBlockchain() {
        Account senderAccount = createAccount("sender");
        Account receiverAccount = createAccount("receiver");

        Map<RskAddress, AccountState> accounts = new HashMap<>();
        accounts.put(senderAccount.getAddress(), new AccountState(BigInteger.ZERO, Coin.valueOf(20000000)));
        accounts.put(receiverAccount.getAddress(), new AccountState(BigInteger.ZERO, Coin.ZERO));

        final NetBlockStore store = new NetBlockStore();
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        Blockchain blockchain = blockChainBuilder.ofSize(0, false, accounts);

        Block genesis = blockchain.getBestBlock();
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        List<Transaction> txs = Collections.singletonList(
                createTransaction(senderAccount, receiverAccount, BigInteger.valueOf(1000000), BigInteger.ZERO)
        );

        Block block = new BlockGenerator().createChildBlock(genesis, txs, blockChainBuilder.getRepository().getRoot());

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig(), signatureCache);

        BlockExecutor blockExecutor = new BlockExecutor(
                config.getActivationConfig(),
                new RepositoryLocator(blockChainBuilder.getTrieStore(), stateRootHandler),
                new TransactionExecutorFactory(
                        config,
                        blockChainBuilder.getBlockStore(),
                        null,
                        blockFactory,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                        new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                )
        );
        Assertions.assertEquals(1, block.getTransactionsList().size());
        blockExecutor.executeAndFillAll(block, genesis.getHeader());
        Assertions.assertEquals(21000, block.getFeesPaidToMiner().asBigInteger().intValueExact());
        Assertions.assertEquals(1, block.getTransactionsList().size());

        Assertions.assertEquals(1, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
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

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender, bids), sender);
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assertions.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    void processBlockResponseAddsToBlockchain() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        Assertions.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = new BlockGenerator().createChildBlock(blockchain.getBlockByNumber(10));

        Assertions.assertEquals(11, block.getNumber());
        Assertions.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash().getBytes());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));
        BlockResponseMessage response = new BlockResponseMessage(new Random().nextLong(), block);
        processor.registerExpectedMessage(response);

        processor.processBlockResponse(sender, response);

        Assertions.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlockHash());
        Assertions.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.ofSize(0);
        Blockchain advancedBlockchain = new BlockChainBuilder().ofSize(100);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, builder.getBlockStore(), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory, new DummyBlockValidationRule(),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        processor.processStatus(sender, StatusUtils.fromBlockchain(advancedBlockchain));

        Assertions.assertFalse(sender.getMessages().isEmpty());
        List<Message> messages = sender.getMessages();
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage) messages.get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] expectedHeights = new long[] { 50, 25, 12, 6, 3, 1};

        for (int k = 0; k < expectedHeights.length; k++) {
            Assertions.assertEquals( k + 2, messages.size());
            Message message = messages.get(k + 1);
            Assertions.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assertions.assertEquals(expectedHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(expectedHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash().getBytes()));
        }

        Assertions.assertEquals(expectedHeights.length + 2, messages.size());

        Message message = messages.get(messages.size() - 1);

        Assertions.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage) message;

        Assertions.assertEquals(0, request.getStartNumber());
        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void findConnectionPointBlockchainWith30BlocksVsBlockchainWith100Blocks() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.ofSize(30);
        org.ethereum.db.BlockStore blockStore = builder.getBlockStore();
        Blockchain advancedBlockchain = BlockChainBuilder.copyAndExtend(blockchain, 70);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(sender));

        NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory, new DummyBlockValidationRule(),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        Status status = StatusUtils.fromBlockchain(advancedBlockchain);
        processor.processStatus(sender, status);

        List<Message> messages = sender.getMessages();
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage) messages.get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] binarySearchHeights = new long[] { 50, 25, 37, 31, 28, 29, 30, 30 };
        for (int k = 0; k < binarySearchHeights.length; k++) {
            Assertions.assertEquals(k + 2, messages.size());
            Message message = messages.get(k + 1);
            Assertions.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assertions.assertEquals(binarySearchHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(binarySearchHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash().getBytes()));
        }

        Assertions.assertEquals(binarySearchHeights.length + 2, messages.size());

        Message message = messages.get(messages.size() - 1);

        Assertions.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assertions.assertEquals(30, request.getStartNumber());
        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void processSkeletonResponseWithTenBlockIdentifiers() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Peer channel = mock(Peer.class);
        when(channel.getPeerNodeID()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        int connectionPoint = 0;
        int step = 10;
        int linkCount = 9;
        processor.startDownloadingSkeleton(connectionPoint, sender);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Assertions.assertFalse(sender.getMessages().isEmpty());
        Message message = sender.getMessages().get(0);
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage) message;

        Assertions.assertArrayEquals(blockIdentifiers.get(1).getHash(), request.getHash());
        Assertions.assertEquals(10, request.getCount());

        DownloadingHeadersSyncState syncState = (DownloadingHeadersSyncState) processor.getSyncState();
        List<BlockIdentifier> skeleton = syncState.getSkeleton();

        Assertions.assertEquals(10, skeleton.size());
        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void processSkeletonResponseWithoutBlockIdentifiers() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(0);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });

        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, new NetBlockStore(), blockchain, new BlockNodeInformation(), syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory, new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        List<BlockIdentifier> blockIdentifiers = new ArrayList<>();

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Assertions.assertFalse(processor.isSyncing());
        Assertions.assertTrue(sender.getMessages().isEmpty());
        Assertions.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    void processSkeletonResponseWithConnectionPoint() {
        Blockchain blockchain = new BlockChainBuilder().ofSize(25);

        final NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);

        SimplePeer sender = new SimplePeer(new byte[] { 0x01 });
        final ChannelManager channelManager = mock(ChannelManager.class);
        Peer channel = mock(Peer.class);
        when(channel.getPeerNodeID()).thenReturn(sender.getPeerNodeID());
        when(channelManager.getActivePeers()).thenReturn(Collections.singletonList(channel));

        SyncProcessor processor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, new PeersInformation(channelManager, SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()),
                mock(Genesis.class),
                mock(EthereumListener.class));

        int connectionPoint = 25;
        int step = 10;
        int linkCount = 10;
        processor.startDownloadingSkeleton(connectionPoint, sender);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);


        Message message = sender.getMessages().get(0);
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage) message;

        Assertions.assertEquals(5, request.getCount());
        Assertions.assertArrayEquals(blockIdentifiers.get(1).getHash(), request.getHash());
        Assertions.assertEquals(1, processor.getExpectedResponses().size());
    }

    @Test
    void syncEventsSentToListener() {
        final NetBlockStore store = new NetBlockStore();
        Blockchain blockchain = new BlockChainBuilder().ofSize(10);
        SimplePeer peer = new SimplePeer(new byte[] { 0x01 });
        BlockStore blockStore = mock(BlockStore.class);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        PeersInformation peersInformation = spy(new PeersInformation(getChannelManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, blockchain, RskMockFactory.getPeerScoringManager()));
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, DummyBlockValidator.VALID_RESULT_INSTANCE);
        EthereumListener listener = mock(EthereumListener.class);

        SyncProcessor processor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService,
                SyncConfiguration.IMMEDIATE_FOR_TESTING, blockFactory,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new SyncBlockValidatorRule(
                        new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())
                ),
                DIFFICULTY_CALCULATOR, peersInformation,
                mock(Genesis.class),
                listener);

        peersInformation.registerPeer(peer);

        Block block = mock(Block.class);
        doReturn(1L).when(block).getNumber();
        doReturn(Keccak256.ZERO_HASH).when(block).getHash();
        doReturn(block).when(blockStore).getBestBlock();
        doReturn(Optional.of(peer)).when(peersInformation).getBestPeer();
        SyncPeerStatus peerStatus = mock(SyncPeerStatus.class);
        Status status = mock(Status.class);
        doReturn(status).when(peerStatus).getStatus();
        doReturn(peerStatus).when(peersInformation).getPeer(peer);

        processor.getSyncState().newPeerStatus();
        verify(listener).onLongSyncStarted();

        doReturn(1L).when(blockStore).getMinNumber();
        doReturn(block).when(blockStore).getChainBlockByNumber(anyLong());
        processor.stopSyncing();
        processor.getSyncState().newPeerStatus();

        verify(listener).onLongSyncDone();
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
        String toAddress = ByteUtil.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = Transaction
                .builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(Hex.decode(toAddress))
                .chainId(config.getNetworkConstants().getChainId())
                .value(value)
                .build();
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
