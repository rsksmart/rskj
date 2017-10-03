package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageChannel;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.net.sync.SyncPeerProcessor;
import co.rsk.net.sync.SyncPeerStatus;
import co.rsk.net.utils.StatusUtils;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.validator.ProofOfWorkRule;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessorTest {
    @Test
    public void noPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        Assert.assertEquals(0, processor.getNoPeers());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertEquals(50, request.getHeight());
    }

    @Test
    public void syncWithAdvancedPeerAfterTimeoutWaitingPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(BigInteger.TEN));

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.DEFAULT, new ProofOfWorkRule());
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getPeerNodeID()));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assert.assertTrue(sender.getMessages().isEmpty());

        processor.onTimePassed(Duration.ofMinutes(1));
        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertEquals(50, request.getHeight());
    }

    @Test
    public void dontSyncWithoutAdvancedPeerAfterTimeoutWaitingPeers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(0, hash, parentHash, blockchain.getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.DEFAULT, new ProofOfWorkRule());
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.DEFAULT, new ProofOfWorkRule());

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

        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertEquals(50, request.getHeight());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
        Assert.assertFalse(processor.isPeerSyncing(sender.getPeerNodeID()));
    }

    @Test
    public void sendSkeletonRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(100);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        processor.sendSkeletonRequestTo(sender, 0);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(0, request.getStartNumber());

        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void sendBlockHashRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        processor.sendBlockHashRequestTo(sender, 100);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(100, request.getHeight());

        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
        Assert.assertFalse(processor.isPeerSyncing(sender.getPeerNodeID()));
    }

    @Test(expected = Exception.class)
    public void processBlockHashResponseWithUnknownHash() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.processStatus(sender, new Status(100, null));
    }

    @Test
    public void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(98, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(98, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockHeadersResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(100, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(100, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockHeadersResponseWithOneHeader() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain otherBlockchain = BlockChainBuilder.ofSize(3, true);
        Block block = otherBlockchain.getBlockByNumber(1);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(99, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(99, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BODY_REQUEST_MESSAGE, message.getMessageType());

        BodyRequestMessage request = (BodyRequestMessage) message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertArrayEquals(block.getHash(), request.getBlockHash());

        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithManyHeaders() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain otherBlockchain = BlockChainBuilder.ofSize(10, true);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();

        Assert.assertEquals(10, otherBlockchain.getStatus().getBestBlockNumber());

        for (int k = 10; k >= 1; k--)
            headers.add(otherBlockchain.getBlockByNumber(k).getHeader());

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(99, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(99, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(10, sender.getMessages().size());

        for (int k = 0; k < 10; k++) {
            Message message = sender.getMessages().get(k);

            Assert.assertEquals(MessageType.BODY_REQUEST_MESSAGE, message.getMessageType());

            BodyRequestMessage request = (BodyRequestMessage)message;

            Assert.assertArrayEquals(otherBlockchain.getBlockByNumber(k + 1).getHash(), request.getBlockHash());
        }

        Assert.assertEquals(10, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithManyHeadersMissingFirstParent() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain otherBlockchain = BlockChainBuilder.ofSize(10, true);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 10; k >= 2; k--)
            headers.add(otherBlockchain.getBlockByNumber(k).getHeader());

        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(99, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(99, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(0, sender.getMessages().size());
        Assert.assertEquals(0, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithOneExistingHeader() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(97, headers);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(97, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        BodyResponseMessage response = new BodyResponseMessage(100, null, null);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(100, MessageType.BODY_RESPONSE_MESSAGE);
        processor.processBodyResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getPeerNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseAddsToBlockchainAndRequestHeaders() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getPeerNodeID(), block.getHeader());

        List<BlockIdentifier> blockIdentifiers = new ArrayList<>();
        blockIdentifiers.add(new BlockIdentifier(block.getHash(), block.getNumber()));
        blockIdentifiers.add(new BlockIdentifier(HashUtil.randomHash(), block.getNumber() + 192));
        processor.getSyncPeerProcessor().setSkeleton(blockIdentifiers);

        processor.processBodyResponse(sender, response);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(192, request.getCount());
        Assert.assertArrayEquals(blockIdentifiers.get(1).getHash(), request.getHash());

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertFalse(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        List<Transaction> transactions = block.getTransactionsList();
        List<BlockHeader> uncles = block.getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getPeerNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        BlockResponseMessage response = new BlockResponseMessage(96, block);

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(96, MessageType.BLOCK_RESPONSE_MESSAGE);
        processor.processBlockResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain advancedBlockchain = BlockChainBuilder.ofSize(100);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        processor.processStatus(sender, StatusUtils.fromBlockchain(advancedBlockchain));

        long []expectedHeights = new long[] { 50, 25, 13, 7, 4, 3, 2, 1, 0 };

        for (int k = 0; k < expectedHeights.length; k++) {
            Assert.assertEquals(k + 1, sender.getMessages().size());
            Message message = sender.getMessages().get(k);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(expectedHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(expectedHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash()));
        }

        Assert.assertEquals(expectedHeights.length + 1, sender.getMessages().size());

        Message message = sender.getMessages().get(sender.getMessages().size() - 1);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertEquals(0, request.getStartNumber());
        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void findConnectionPointBlockchainWith30BlocksVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(30);
        Blockchain advancedBlockchain = BlockChainBuilder.copyAndExtend(blockchain, 70);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        Status status = StatusUtils.fromBlockchain(advancedBlockchain);
        processor.processStatus(sender, status);

        long []expectedHeights = new long[] { 50, 25, 25 + 12, 25 + 12 - 6, 25 + 12 - 6 - 3, 25 + 12 - 6 - 3 + 2, 25 + 12 - 6 - 3 + 2 + 2, 25 + 12 - 6 - 3 + 2 + 2 - 1, 25 + 12 - 6 - 3 + 2 + 2 - 1 - 1 };

        for (int k = 0; k < expectedHeights.length; k++) {
            Assert.assertEquals(k + 1, sender.getMessages().size());
            Message message = sender.getMessages().get(k);
            Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
            BlockHashRequestMessage request = (BlockHashRequestMessage)message;
            long requestId = request.getId();
            Assert.assertEquals(expectedHeights[k], request.getHeight());

            Block block = advancedBlockchain.getBlockByNumber(expectedHeights[k]);

            processor.processBlockHashResponse(sender, new BlockHashResponseMessage(requestId, block.getHash()));
        }

        Assert.assertEquals(expectedHeights.length + 1, sender.getMessages().size());

        Message message = sender.getMessages().get(sender.getMessages().size() - 1);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertEquals(30, request.getStartNumber());
        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithTenBlockIdentifiers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockIdentifier> bids = new ArrayList<>();

        for (int k = 0; k < 10; k++)
            bids.add(new BlockIdentifier(HashUtil.randomHash(), (k + 1) * 10));

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().setConnectionPoint(0);
        processor.getSyncPeerProcessor().registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertArrayEquals(bids.get(0).getHash(), request.getHash());
        Assert.assertEquals(10, request.getCount());

        SyncPeerStatus peerStatus = processor.getPeerStatus(sender.getPeerNodeID());
        SyncPeerProcessor syncPeerProcessor = processor.getSyncPeerProcessor();

        Assert.assertNotNull(peerStatus);
        Assert.assertTrue(syncPeerProcessor.hasSkeleton());
        Assert.assertEquals(10, syncPeerProcessor.getSkeleton().size());
        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithoutBlockIdentifiers() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());

        List<BlockIdentifier> bids = new ArrayList<>();

        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getSyncPeerProcessor().getExpectedResponses().isEmpty());
    }

    @Test
    public void processSkeletonResponseWithConnectionPoint() {
        Blockchain blockchain = BlockChainBuilder.ofSize(25);

        final BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);

        SimpleMessageChannel sender = new SimpleMessageChannel(new byte[] { 0x01 });
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService, SyncConfiguration.IMMEDIATE_FOR_TESTING, new ProofOfWorkRule());
        processor.getPeerStatusAndSaveSender(sender);
        processor.getSyncPeerProcessor().setConnectionPoint(25);

        List<BlockIdentifier> bids = new ArrayList<>();

        for (int k = 0; k < 10; k++) {
            int number = (k + 1) * 10;
            Block block = blockchain.getBlockByNumber(number);

            byte[] hash;

            if (block != null)
                hash = block.getHash();
            else
                hash = HashUtil.randomHash();

            bids.add(new BlockIdentifier(hash, number));
        }

        processor.getSyncPeerProcessor().registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(5, request.getCount());
        Assert.assertArrayEquals(bids.get(2).getHash(), request.getHash());
        Assert.assertEquals(1, processor.getSyncPeerProcessor().getExpectedResponses().size());
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = Transaction.create(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
        tx.sign(privateKeyBytes);
        return tx;
    }

    public static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.sha3(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }
}
