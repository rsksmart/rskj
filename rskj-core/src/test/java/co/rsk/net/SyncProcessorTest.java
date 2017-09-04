package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
    }

    @Test
    public void findConnectionPointSendingFirstMessage() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, 100);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());

        BlockHashRequestMessage request = (BlockHashRequestMessage)message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertEquals(50, request.getHeight());
    }

    @Test
    public void processBlockHashResponseWithUnknownHash() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, 100);

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
    }

    @Test
    public void processBlockHeadersResponseWithEmptyList() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockHeader> headers = new ArrayList<>();
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(98, headers);

        processor.expectMessageFrom(98, sender.getNodeID());
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
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

        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
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

        processor.expectMessageFrom(99, sender.getNodeID());
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.BODY_REQUEST_MESSAGE, message.getMessageType());

        BodyRequestMessage request = (BodyRequestMessage) message;

        Assert.assertNotEquals(0, request.getId());
        Assert.assertArrayEquals(block.getHash(), request.getBlockHash());
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

        processor.expectMessageFrom(97, sender.getNodeID());
        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBodyResponseRejectsNonSolicitedMessages() {
        Blockchain blockchain = BlockChainBuilder.ofSize(3);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        BodyResponseMessage response = new BodyResponseMessage(100, null, null);

        processor.processBodyResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
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

        processor.expectBodyResponseFor(96, sender.getNodeID(), block.getHeader());
        processor.processBodyResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
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

        processor.processBlockResponse(sender, response);

        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);
        Blockchain advancedBlockchain = BlockChainBuilder.ofSize(100);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, 100);

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
    }

    @Test
    public void findConnectionPointBlockchainWith30BlocksVsBlockchainWith100Blocks() {
        Blockchain blockchain = BlockChainBuilder.ofSize(30);
        Blockchain advancedBlockchain = copyBlockchain(blockchain);
        extendBlockchain(advancedBlockchain, 30, 70);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        processor.findConnectionPoint(sender, 100);

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
        Assert.assertTrue(peerStatus.hasBlockIdentifiers());
        Assert.assertEquals(10, peerStatus.getBlockIdentifiers().size());
    }

    @Test
    public void processSkeletonResponseWithoutBlockIdentifiers() {
        Blockchain blockchain = BlockChainBuilder.ofSize(0);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain, null);

        List<BlockIdentifier> bids = new ArrayList<>();

        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertTrue(sender.getMessages().isEmpty());
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

            bids.add(new BlockIdentifier(hash, (k + 1) * 10));
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
    }

    private static Blockchain copyBlockchain(Blockchain original) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        long height = original.getStatus().getBestBlockNumber();

        for (long k = 0; k <= height; k++)
            blockChain.tryToConnect(original.getBlockByNumber(k));

        return blockChain;
    }

    private static void extendBlockchain(Blockchain blockChain, long from, int size) {
        Block initial = blockChain.getBlockByNumber(from);
        List<Block> blocks = BlockGenerator.getBlockChain(initial, size);

        for (Block block: blocks)
            blockChain.tryToConnect(block);
    }
}
