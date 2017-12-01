package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by usuario on 25/08/2017.
 */
public class BodyResponseMessageTest {
    @Test
    public void createMessage() {
        List<Transaction> transactions = new ArrayList<>();

        for (int k = 1; k <= 10; k++)
            transactions.add(createTransaction(k));

        List<BlockHeader> uncles = new ArrayList<>();

        Block parent = BlockGenerator.getInstance().getGenesisBlock();

        for (int k = 1; k < 10; k++) {
            Block block = BlockGenerator.getInstance().createChildBlock(parent);
            uncles.add(block.getHeader());
            parent = block;
        }

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles);

        Assert.assertEquals(100, message.getId());

        Assert.assertNotNull(message.getTransactions());
        Assert.assertEquals(transactions.size(), message.getTransactions().size());

        for (int k = 0; k < transactions.size(); k++)
            Assert.assertArrayEquals(transactions.get(k).getHash(), message.getTransactions().get(k).getHash());

        Assert.assertNotNull(message.getUncles());
        Assert.assertEquals(uncles.size(), message.getUncles().size());

        for (int k = 0; k < uncles.size(); k++)
            Assert.assertArrayEquals(uncles.get(k).getEncoded(), message.getUncles().get(k).getEncoded());
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
}
