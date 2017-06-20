package co.rsk.net.handler;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 20/06/2017.
 */
public class TxsPerAccountTest {
    @Test
    public void containsNoTransactions() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assert.assertNotNull(txspa.getTransactions());
        Assert.assertTrue(txspa.getTransactions().isEmpty());
    }

    @Test
    public void nextNonceIsNull() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assert.assertNull(txspa.getNextNonce());
    }

    @Test
    public void doesNotCointainNonce() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assert.assertFalse(txspa.containsNonce(BigInteger.ONE));
    }

    @Test
    public void containsNonce() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);

        txspa.getTransactions().add(tx);

        Assert.assertTrue(txspa.containsNonce(BigInteger.ONE));
    }

    @Test
    public void retrieveTransactionsReadyToBeSend() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);

        txspa.getTransactions().add(tx);

        List<Transaction> txs = txspa.readyToBeSent(BigInteger.ONE);

        Assert.assertNotNull(txs);
        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        Assert.assertNotNull(txspa.getNextNonce());
        Assert.assertEquals(BigInteger.valueOf(2), txspa.getNextNonce());
    }

    private static Transaction buildTransaction(long nonce) {
        Account sender = new AccountBuilder().name("sender").build();
        return new TransactionBuilder().sender(sender).nonce(nonce).build();
    }
}
