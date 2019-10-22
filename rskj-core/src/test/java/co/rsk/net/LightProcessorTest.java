package co.rsk.net;

import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.messages.BlockReceiptsResponseMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import co.rsk.net.simples.SimpleMessageChannel;
import co.rsk.net.sync.SyncConfiguration;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.*;

import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LightProcessorTest {

    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});

    @Test
    public void processBlockReceiptRequestMessageAndReturnsReceiptsCorrectly() {
        final Blockchain blockchain = mock(Blockchain.class);
        final Block block = mock(Block.class);
        Transaction tx = mock(Transaction.class);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);

        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);

        TransactionReceipt receipt = createReceipt();

        Keccak256 blockHash = new Keccak256(HASH_1);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(txs);
        Mockito.when(tx.getHash()).thenReturn(new Keccak256(TestUtils.randomBytes(32)));
        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);
        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(transactionInfo.getReceipt()).thenReturn(receipt);

        final LightProcessor lightProcessor = getLightProcessor(blockchain);
        final SimpleMessageChannel sender = new SimpleMessageChannel();

        lightProcessor.processBlockReceiptsRequest(sender, 100, block.getHash().getBytes());

        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_RECEIPTS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockReceiptsResponseMessage response = (BlockReceiptsResponseMessage) message;

        Assert.assertEquals(100, response.getId());
        Assert.assertEquals(receipt, response.getBlockReceipts().get(0));
        Assert.assertEquals(1, response.getBlockReceipts().size());
    }

    @Test
    public void processBlockReceiptRequestMessageWithIncorrectBlockHash() {
        final Blockchain blockchain = mock(Blockchain.class);
        Keccak256 blockHash = new Keccak256(HASH_1);

        final LightProcessor lightProcessor = getLightProcessor(blockchain);
        final SimpleMessageChannel sender = new SimpleMessageChannel();

        lightProcessor.processBlockReceiptsRequest(sender, 100, blockHash.getBytes());

        Assert.assertEquals(0, sender.getMessages().size());

    }

    private LightProcessor getLightProcessor(Blockchain blockchain) {
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        final NetBlockStore store = new NetBlockStore();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        return new LightProcessor(blockchain, blockSyncService);
    }

    // from TransactionTest
    private static TransactionReceipt createReceipt() {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});

        receipt.setTransaction(new Transaction((byte[]) null, null, null, null, null, null));

        return receipt;
    }
}