package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.StatusMessage;
import co.rsk.net.simples.SimpleMessageSender;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
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
    }

    @Test
    @Ignore
    public void sendSkeletonRequest() {
        Blockchain blockchain = createBlockchain(100);
        SimpleMessageSender sender = new SimpleMessageSender(new byte[] { 0x01 });
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(blockchain.getStatus().getBestBlockNumber(), hash, parentHash, blockchain.getStatus().getTotalDifficulty());

        SyncProcessor processor = new SyncProcessor(blockchain);
        processor.processStatus(sender, status);

        processor.sendSkeletonRequest(0);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
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
}
