package co.rsk.net.handler;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 20/06/2017.
 */
public class TxsPerAccountTest {
    @Test
    public void containsNoTransactions() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assertions.assertNotNull(txspa.getTransactions());
        Assertions.assertTrue(txspa.getTransactions().isEmpty());
    }

    @Test
    public void nextNonceIsNull() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assertions.assertNull(txspa.getNextNonce());
    }

    @Test
    public void doesNotCointainNonce() {
        TxsPerAccount txspa = new TxsPerAccount();

        Assertions.assertFalse(txspa.containsNonce(BigInteger.ONE));
    }

    @Test
    public void containsNonce() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);

        txspa.getTransactions().add(tx);

        Assertions.assertTrue(txspa.containsNonce(BigInteger.ONE));
    }

    @Test
    public void retrieveTransactionsReadyToBeSend() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);

        txspa.getTransactions().add(tx);

        List<Transaction> txs = txspa.readyToBeSent(BigInteger.ONE);

        Assertions.assertNotNull(txs);
        Assertions.assertFalse(txs.isEmpty());
        Assertions.assertEquals(1, txs.size());
        Assertions.assertEquals(tx, txs.get(0));

        Assertions.assertNotNull(txspa.getNextNonce());
        Assertions.assertEquals(BigInteger.valueOf(2), txspa.getNextNonce());
    }

    @Test
    public void retrieveTransactionsReadyToBeSendTwoNonces() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);
        Transaction tx2 =  buildTransaction(2);

        txspa.getTransactions().add(tx);
        txspa.getTransactions().add(tx2);

        List<Transaction> txs = txspa.readyToBeSent(BigInteger.ONE);

        Assertions.assertNotNull(txs);
        Assertions.assertFalse(txs.isEmpty());
        Assertions.assertEquals(2, txs.size());
        Assertions.assertEquals(tx, txs.get(0));
        Assertions.assertEquals(tx2, txs.get(1));

        Assertions.assertNotNull(txspa.getNextNonce());
        Assertions.assertEquals(BigInteger.valueOf(3), txspa.getNextNonce());
    }

    @Test
    public void retrieveTransactionsReadyToBeSendAndRemoveNonce() {
        TxsPerAccount txspa = new TxsPerAccount();

        Transaction tx =  buildTransaction(1);
        Transaction tx2 =  buildTransaction(1);

        txspa.getTransactions().add(tx);
        txspa.getTransactions().add(tx2);

        List<Transaction> txs = txspa.readyToBeSent(BigInteger.ONE);

        Assertions.assertNotNull(txs);
        Assertions.assertFalse(txs.isEmpty());
        Assertions.assertEquals(1, txs.size());

        Assertions.assertNotNull(txspa.getNextNonce());
        Assertions.assertEquals(BigInteger.valueOf(2), txspa.getNextNonce());

        txspa.removeNonce(BigInteger.ONE);

        Assertions.assertNotNull(txspa.getTransactions());
        Assertions.assertTrue(txspa.getTransactions().isEmpty());

        Assertions.assertNull(txspa.getNextNonce());
    }

    private static Transaction buildTransaction(long nonce) {
        Account sender = new AccountBuilder().name("sender").build();
        return new TransactionBuilder().sender(sender).nonce(nonce).build();
    }
}
