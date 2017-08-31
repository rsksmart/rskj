package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Ignore;
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
        Blockchain blockchain = createBlockchain();
        SyncProcessor processor = new SyncProcessor(blockchain);

        Assert.assertEquals(0, processor.getNoPeers());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());
    }

    @Test
    public void processStatusWithAdvancedPeers() {
        Blockchain blockchain = createBlockchain();
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(100, hash, parentHash, blockchain.getTotalDifficulty().add(BigInteger.TEN));

        SyncProcessor processor = new SyncProcessor(blockchain);
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
        Blockchain blockchain = createBlockchain(100);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(blockchain.getStatus().getBestBlockNumber(), hash, parentHash, blockchain.getStatus().getTotalDifficulty());

        SyncProcessor processor = new SyncProcessor(blockchain);
        processor.processStatus(sender, status);

        Assert.assertEquals(1, processor.getNoPeers());
        Assert.assertEquals(0, processor.getNoAdvancedPeers());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void sendSkeletonRequest() {
        Blockchain blockchain = createBlockchain(100);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
        Blockchain blockchain = createBlockchain();
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
        Blockchain blockchain = createBlockchain();
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
        Blockchain blockchain = createBlockchain();
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
        Blockchain blockchain = createBlockchain();
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

        List<BlockHeader> headers = new ArrayList<>();
        BlockHeadersResponseMessage response = new BlockHeadersResponseMessage(99, headers);

        processor.processBlockHeadersResponse(sender, response);
        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void findConnectionPointBlockchainWithGenesisVsBlockchainWith100Blocks() {
        Blockchain blockchain = createBlockchain();
        Blockchain advancedBlockchain = createBlockchain(100);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
        Blockchain blockchain = createBlockchain(30);
        Blockchain advancedBlockchain = copyBlockchain(blockchain);
        extendBlockchain(advancedBlockchain, 30, 70);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

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
    @Ignore
    public void processSkeletonResponseWithTenBlockIdentifiers() {
        Blockchain blockchain = createBlockchain(30);

        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });

        SyncProcessor processor = new SyncProcessor(blockchain);

        List<BlockIdentifier> bids = new ArrayList<>();

        for (int k = 0; k < 10; k++)
            bids.add(new BlockIdentifier(HashUtil.randomHash(), (k + 1) * 10));

        processor.processSkeletonResponse(sender, new SkeletonResponseMessage(1, bids));

        Assert.assertFalse(sender.getMessages().isEmpty());
    }

    private static Blockchain createBlockchain() {
        return createBlockchain(0);
    }

    private static Blockchain createBlockchain(int size) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        Block genesis = BlockGenerator.getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        if (size > 0) {
            List<Block> blocks = BlockGenerator.getBlockChain(genesis, size);

            for (Block block: blocks)
                blockChain.tryToConnect(block);
        }

        return blockChain;
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
