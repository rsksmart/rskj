package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
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
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessorTest {
    @Test
    public void noPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());
        PeerScoringManager peerScoringManager = getPeerScoringManager();
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

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

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(BigInteger.TEN));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(BigInteger.TEN));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), syncConfiguration, new ProofOfWorkRule());
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getPeersCount());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(syncConfiguration.getTimeoutWaitingPeers().dividedBy(2));
        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.DEFAULT, new ProofOfWorkRule());
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

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(BigInteger.TEN));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.DEFAULT, new ProofOfWorkRule());

        List<SimpleMessageChannel> senders = new ArrayList<SimpleMessageChannel>();

        int lessPeers = SyncConfiguration.DEFAULT.getExpectedPeers() - 1;
        for (int i = 0; i < lessPeers; i++) {
            senders.add(new SimpleMessageChannel());
        }

        senders.forEach(s -> Assert.assertTrue(s.getMessages().isEmpty()));
        senders.forEach(s -> processor.processStatus(s, status));
        senders.forEach(s -> Assert.assertTrue(s.getMessages().isEmpty()));

        Assert.assertEquals(lessPeers, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        senders.stream()
                .map(SimpleMessageChannel::getPeerNodeID)
                .forEach(peerId -> Assert.assertTrue(ids.contains(peerId)));

        SimpleMessageChannel lastSender = new SimpleMessageChannel();
        Assert.assertFalse(ids.contains(lastSender.getPeerNodeID()));

        processor.processStatus(lastSender, status);

        // now test with all senders
        senders.add(lastSender);
        Assert.assertTrue(ids.contains(lastSender.getPeerNodeID()));
        Assert.assertFalse(senders.stream().allMatch(s -> s.getMessages().isEmpty()));
        Assert.assertEquals(1, senders.stream()
                .map(SimpleMessageChannel::getMessages)
                .mapToInt(List::size)
                .sum());

        Message message = senders.stream().filter(s -> !s.getMessages().isEmpty())
                .findFirst()
                .map(SimpleMessageChannel::getMessages)
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        processor.sendSkeletonRequest(sender, 0);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        Assert.assertTrue(processor.isPeerSyncing(sender.getPeerNodeID()));

        processor.sendBlockHashRequest(100);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.processStatus(sender, new Status(100, null));
    }

    @Test
    public void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Stack<BlockHeader> headerStack = new Stack<>();
        headerStack.add(block.getHeader());
        List<Stack<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash(), 0));
        bids.add(new BlockIdentifier(block.getHash(), 1));

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender.getPeerNodeID(), bids));
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Ignore("should be enabled when fix for txtrieroot is merged")
    @Test
    public void doesntProcessInvalidBodyResponse() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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

        Stack<BlockHeader> headerStack = new Stack<>();
        headerStack.add(block.getHeader());
        headerStack.add(block.getHeader());
        headerStack.add(block.getHeader());
        List<Stack<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash(), 0));
        bids.add(new BlockIdentifier(block.getHash(), 1));

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

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Blockchain extended = BlockChainBuilder.copy(blockchain);
        extended.tryToConnect(block);

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Stack<BlockHeader> headerStack = new Stack<>();
//        for (int i = 1; i <= blockchain.getBestBlock().getNumber(); i++){
//            headerStack.add(blockchain.getBlockByNumber(i).getHeader());
//        }
        headerStack.add(block.getHeader());
        List<Stack<BlockHeader>> headers = new ArrayList<>();
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
        List<BigInteger> balances = new ArrayList<BigInteger>();

        accounts.add(senderAccount);
        balances.add(BigInteger.valueOf(20000000));
        accounts.add(receiverAccount);
        balances.add(BigInteger.ZERO);

        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0, false, accounts, balances);

        Block genesis = blockchain.getBestBlock();
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        Transaction tx = createTransaction(senderAccount, receiverAccount, BigInteger.valueOf(1000000), BigInteger.ZERO);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block block = BlockGenerator.createChildBlock(genesis, txs, blockchain.getRepository().getRoot());

        BlockExecutor blockExecutor = new BlockExecutor(blockchain.getRepository(), blockchain, blockchain.getBlockStore(), null);
        Assert.assertEquals(1, block.getTransactionsList().size());
        blockExecutor.executeAndFillAll(block, genesis);
        Assert.assertEquals(21000, block.getFeesPaidToMiner());
        Assert.assertEquals(1, block.getTransactionsList().size());

        Assert.assertEquals(1, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        List<Transaction> transactions = block.getTransactionsList();
        List<BlockHeader> uncles = block.getUncleList();
        long lastRequestId = new Random().nextLong();
        BodyResponseMessage response = new BodyResponseMessage(lastRequestId, transactions, uncles);
        processor.registerExpectedMessage(response);

        Stack<BlockHeader> headerStack = new Stack<>();
        headerStack.add(block.getHeader());
        List<Stack<BlockHeader>> headers = new ArrayList<>();
        headers.add(headerStack);

        List<BlockIdentifier> bids = new ArrayList<>();
        bids.add(new BlockIdentifier(blockchain.getBlockByNumber(0).getHash(), 0));
        bids.add(new BlockIdentifier(block.getHash(), 1));

        processor.startDownloadingBodies(headers, Collections.singletonMap(sender.getPeerNodeID(), bids));
        ((DownloadingBodiesSyncState)processor.getSyncState()).expectBodyResponseFor(lastRequestId, sender.getPeerNodeID(), block.getHeader());

        processor.processBodyResponse(sender, response);

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockResponseAddsToBlockchain() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), 0);
        BlockResponseMessage response = new BlockResponseMessage(new Random().nextLong(), block);
        processor.registerExpectedMessage(response);

        processor.processBlockResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getExpectedResponses().isEmpty());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain advancedBlockchain = BlockChainBuilder.ofSize(100);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new DummyBlockValidationRule());

        processor.processStatus(sender, StatusUtils.fromBlockchain(advancedBlockchain));
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage)sender.getMessages().get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] expectedHeights = new long[] { 50, 25, 12, 6, 3, 1 };

        for (int k = 0; k < expectedHeights.length; k++) {
            Assert.assertEquals( k + 2, sender.getMessages().size());
            Message message = sender.getMessages().get(k + 1);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(expectedHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(expectedHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash()));
        }

        Assert.assertEquals(expectedHeights.length + 2, sender.getMessages().size());

        Message message = sender.getMessages().get(sender.getMessages().size() - 1);

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

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new DummyBlockValidationRule());

        Status status = StatusUtils.fromBlockchain(advancedBlockchain);
        processor.processStatus(sender, status);
        BlockHeadersRequestMessage requestMessage = (BlockHeadersRequestMessage)sender.getMessages().get(0);
        processor.processBlockHeadersResponse(sender, new BlockHeadersResponseMessage(requestMessage.getId(), Collections.singletonList(advancedBlockchain.getBestBlock().getHeader())));

        long[] binarySearchHeights = new long[] { 50, 25, 37, 31, 28, 29, 30 };
        for (int k = 0; k < binarySearchHeights.length; k++) {
            Assert.assertEquals(k + 2, sender.getMessages().size());
            Message message = sender.getMessages().get(k + 1);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(binarySearchHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(binarySearchHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash()));
        }

        Assert.assertEquals(binarySearchHeights.length + 2, sender.getMessages().size());

        Message message = sender.getMessages().get(sender.getMessages().size() - 1);

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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });


        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        int connectionPoint = 0;
        int step = 10;
        int linkCount = 9;
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), connectionPoint);
        processor.startDownloadingSkeleton(connectionPoint);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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


        SyncProcessor processor = new SyncProcessor(blockchain, null, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, getPeerScoringManager(), SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        int connectionPoint = 25;
        int step = 10;
        int linkCount = 10;
        processor.setSelectedPeer(sender, StatusUtils.getFakeStatus(), connectionPoint);
        processor.startDownloadingSkeleton(connectionPoint);
        List<BlockIdentifier> blockIdentifiers = buildSkeleton(blockchain, connectionPoint, step, linkCount);

        SkeletonResponseMessage response = new SkeletonResponseMessage(new Random().nextLong(), blockIdentifiers);
        processor.registerExpectedMessage(response);
        processor.processSkeletonResponse(sender, response);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

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
                hash = block.getHash();
            else
                hash = HashUtil.randomHash();

            bids.add(new BlockIdentifier(hash, number));
        }
        BlockIdentifier bid = new BlockIdentifier(blockchain.getBestBlockHash(), blockchain.getBestBlock().getNumber());
        bids.add(bid);
        return bids;
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = Transaction.create(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.sha3(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }

    private PeerScoringManager getPeerScoringManager() {
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        return peerScoringManager;
    }
}
