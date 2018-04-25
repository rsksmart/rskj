package co.rsk.core;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 03/08/2017.
 */
public class ImmutableTransactionTest {
    @Test
    public void tryingToSignImmutableTransaction() {
        Transaction tx = createImmutableTransaction();

        try {
            tx.sign(new byte[32]);
        }
        catch (ImmutableTransaction.ImmutableTransactionException ex) {
            Assert.assertTrue(ex.getMessage().contains("Immutable transaction: trying to sign"));
        }
    }

    @Test
    public void tryingToSetGasLimit() {
        Transaction tx = createImmutableTransaction();

        try {
            tx.setGasLimit(new byte[32]);
        }
        catch (ImmutableTransaction.ImmutableTransactionException ex) {
            Assert.assertTrue(ex.getMessage().contains("Immutable transaction: trying to set gas limit"));
        }
    }

    private static Transaction createImmutableTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .nonce(2)
                .immutable()
                .build();

        return tx;
    }
}
