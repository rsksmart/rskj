package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockchainBuilder;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
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
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);

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
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[]{0x01});
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(1, processor.getNoAdvancedPeers());

        Set<NodeID> ids = processor.getKnownPeersNodeIDs();
        Assert.assertFalse(ids.isEmpty());
        Assert.assertTrue(ids.contains(sender.getNodeID()));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertEquals(50, request.getHeight());
    }

    @Test
    public void processStatusWithPeerWithSameDifficulty() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(100);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(blockchain.getStatus().getBestBlockNumber(), hash, parentHash, blockchain.getStatus().getTotalDifficulty());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void sendSkeletonRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(100);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.sendSkeletonRequest(sender, 0);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);

        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, message.getMessageType());

        SkeletonRequestMessage request = (SkeletonRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(0, request.getStartNumber());

        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void sendBlockHashRequest() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.sendBlockHashRequest(sender, 100);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(100, request.getHeight());

        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void findConnectionPointSendingFirstMessage() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, new Status(100, null));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(50, request.getHeight());

        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void processBlockHashResponseWithUnknownHash() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, new Status(100, null));

        long requestId = ((BlockHashRequestMessage)sender.getMessages().get(0)).getId();

        BlockHashResponseMessage response = new BlockHashResponseMessage(requestId, HashUtil.randomHash());

        processor.processBlockHashResponse(sender, response);

        Assert.assertEquals(2, sender.getMessages().size());

        Message message2 = sender.getMessages().get(1);

        Assert.assertNotNull(message2);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message2.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message2;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(25, request.getHeight());

        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockHeader> headers = new ArrayList<>();
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(98, headers);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(98, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockHeadersResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(100, headers);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(100, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockHeadersResponseWithOneHeader() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Blockchain otherBlockchain = BlockChainBuilder.ofSize(3);
        Block block = otherBlockchain.getBlockByNumber(2);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(99, headers);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(99, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BODY_REQUEST_MESSAGE, message.getMessageType());

        BodyRequestMessage request = (BodyRequestMessage) message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertArrayEquals(block.getHash(), request.getBlockHash());

        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void processBlockHeadersResponseWithOneExistingHeader() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        Block block = blockchain.getBlockByNumber(2);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(block.getHeader());
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(97, headers);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(97, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE);
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        BodyResponseMessage response = new BodyResponseMessage(100, null, null);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(100, MessageType.BODY_RESPONSE_MESSAGE);
        processor.processBodyResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseAddsToBlockchain() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseAddsToBlockchainAndRequestHeaders() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        List<Transaction> transactions = blockchain.getBestBlock().getTransactionsList();
        List<BlockHeader> uncles = blockchain.getBestBlock().getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getNodeID(), block.getHeader());

        List<BlockIdentifier> blockIdentifiers = new ArrayList<>();
        blockIdentifiers.add(new BlockIdentifier(block.getHash(), block.getNumber()));
        blockIdentifiers.add(new BlockIdentifier(HashUtil.randomHash(), block.getNumber() + 192));
        processor.getPeerStatus(sender.getNodeID()).setSkeleton(blockIdentifiers);

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
        Assert.assertFalse(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBodyResponseWithTransactionAddsToBlockchain() {
        Account senderAccount = createAccount("sender");
        Account receiverAccount = createAccount("receiver");

        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Block genesis = blockchain.getBestBlock();
        Repository repository = blockchain.getRepository();
        repository.createAccount(senderAccount.getAddress());
        repository.addBalance(senderAccount.getAddress(), BigInteger.valueOf(20000000));
        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();
        blockchain.getBlockStore().saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

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
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        List<Transaction> transactions = block.getTransactionsList();
        List<BlockHeader> uncles = block.getUncleList();
        BodyResponseMessage response = new BodyResponseMessage(96, transactions, uncles);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(96, MessageType.BODY_RESPONSE_MESSAGE);
        processor.expectBodyResponseFor(96, sender.getNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertTrue(sender.getMessages().isEmpty());

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processBlockResponseAddsToBlockchain() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        BlockResponseMessage response = new BlockResponseMessage(96, block);

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(96, MessageType.BLOCK_RESPONSE_MESSAGE);
        processor.processBlockResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain advancedBlockchain = BlockChainBuilder.ofSize(100);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, new Status(100, null));

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
        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void findConnectionPointBlockchainWith30BlocksVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(30);
        Blockchain advancedBlockchain = BlockChainBuilder.copyAndExtend(blockchain, 70);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, new Status(100, null));

        long []expectedHeights = new long[] { 50, 25, 25 + 12, 25 + 12 - 6, 25 + 12 - 6 - 3, 25 + 12 - 6 - 3 + 1, 25 + 12 - 6 - 3 + 1 + 1, 25 + 12 - 6 - 3 + 1 + 1 + 1 };

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
        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithTenBlockIdentifiers() {
        final BlockStore store = new BlockStore();
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);

        List<BlockIdentifier> bids = new ArrayList<>();

        for (int k = 0; k < 10; k++)
            bids.add(new BlockIdentifier(HashUtil.randomHash(), (k + 1) * 10));

        processor.getPeerStatus(sender.getNodeID()).setConnectionPoint(0);
        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertArrayEquals(bids.get(0).getHash(), request.getHash());
        Assert.assertEquals(10, request.getCount());

        SyncPeerStatus peerStatus = processor.getPeerStatus(sender.getNodeID());

        Assert.assertNotNull(peerStatus);
        Assert.assertTrue(peerStatus.hasSkeleton());
        Assert.assertEquals(10, peerStatus.getSkeleton().size());
        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
    }

    @Test
    public void processSkeletonResponseWithoutBlockIdentifiers() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockIdentifier> bids = new ArrayList<>();

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertTrue(sender.getMessages().isEmpty());
        Assert.assertTrue(processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().isEmpty());
    }

    @Test
    public void processSkeletonResponseWithConnectionPoint() {
        Blockchain blockchain = BlockChainBuilder.ofSize(25);

        final BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        SyncProcessor processor = new SyncProcessor(blockchain, blockSyncService);
        processor.getPeerStatus(sender.getNodeID()).setConnectionPoint(25);

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

        processor.getPeerStatus(sender.getNodeID()).registerExpectedResponse(1, MessageType.SKELETON_RESPONSE_MESSAGE);
        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());

        BlockHeadersRequestMessage request = (BlockHeadersRequestMessage)message;

        Assert.assertEquals(5, request.getCount());
        Assert.assertArrayEquals(bids.get(2).getHash(), request.getHash());
        Assert.assertEquals(1, processor.getPeerStatus(sender.getNodeID()).getExpectedResponses().size());
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
