package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;

public class BodyResponseMessageTest {
    private BodyResponseMessage createBodyResponseMessage(List<Transaction> transactions, List<BlockHeader> uncles, BlockHeaderExtension headerExtension) {
        return new BodyResponseMessage(100, transactions, uncles, headerExtension);
    }

    @Test
    public void createMessage() {
        List<Transaction> transactions = new ArrayList<>();

        for (int k = 1; k <= 10; k++)
            transactions.add(createTransaction(k));

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();

        for (int k = 1; k < 10; k++) {
            Block block = blockGenerator.createChildBlock(parent);
            uncles.add(block.getHeader());
            parent = block;
        }

        BodyResponseMessage message = this.createBodyResponseMessage(transactions, uncles, null);

        Assert.assertEquals(100, message.getId());

        Assert.assertNotNull(message.getTransactions());
        Assert.assertEquals(transactions.size(), message.getTransactions().size());

        Assert.assertEquals(
                transactions,
                message.getTransactions());

        Assert.assertNotNull(message.getUncles());
        Assert.assertEquals(uncles.size(), message.getUncles().size());

        for (int k = 0; k < uncles.size(); k++)
            Assert.assertArrayEquals(uncles.get(k).getFullEncoded(), message.getUncles().get(k).getFullEncoded());
    }

    @Test
    public void createMessageWithExtension() {
        List<Transaction> transactions = mock(ArrayList.class);
        List<BlockHeader> uncles = mock(ArrayList.class);

        byte version = 0x1;
        byte[] logsBloom = new byte[]{ 1, 2, 3, 4 };

        BlockHeader header = mock(BlockHeader.class);
        when(header.getVersion()).thenReturn(version);
        when(header.getLogsBloom()).thenReturn(logsBloom);
        BlockHeaderExtension headerExtension = BlockHeaderExtension.fromHeader(header);

        BodyResponseMessage message = this.createBodyResponseMessage(transactions, uncles, headerExtension);

        BlockHeaderExtension messageHeaderExtension = message.getHeaderExtension();
        Assert.assertEquals(version, messageHeaderExtension.getHeaderVersion());
        Assert.assertArrayEquals(logsBloom, messageHeaderExtension.getLogsBloom());
    }

    private static Transaction createTransaction(int number) {
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }

    @Test
    public void accept() {
        List<Transaction> transactions = new LinkedList<>();
        List<BlockHeader> uncles = new LinkedList<>();
        BlockHeaderExtension blockHeaderExtension = mock(BlockHeaderExtension.class);

        BodyResponseMessage message = this.createBodyResponseMessage(transactions, uncles, blockHeaderExtension);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
