package org.ethereum.util;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;

import java.math.BigInteger;

/**
 * Created by ajlopez on 28/02/2018.
 */
public class TransactionFactoryHelper {
    public static Account createAccount(int naccount) {
        return new AccountBuilder().name("account" + naccount).build();
    }

    public static Transaction createSampleTransaction() {
        return createSampleTransaction(0);
    }

    public static Transaction createSampleTransaction(long nonce) {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = getBuilder(sender, receiver, nonce, 10).build();

        return tx;
    }

    private static TransactionBuilder getBuilder(Account sender, Account receiver, long nonce, long value) {
        return new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(nonce)
                .value(BigInteger.valueOf(value));
    }

    public static Transaction createSampleTransaction(int from, int to, long value, int nonce) {
        Account sender = createAccount(from);
        Account receiver = createAccount(to);

        Transaction tx = getBuilder(sender, receiver, nonce, value).build();

        return tx;
    }

    public static Transaction createSampleTransaction(int from, int to, long value, int nonce, BigInteger gasLimit) {
        Account sender = createAccount(from);
        Account receiver = createAccount(to);

        Transaction tx = getBuilder(sender, receiver, nonce, value)
                .gasLimit(gasLimit)
                .build();

        return tx;
    }

    public static Transaction createSampleTransactionWithGasPrice(int from, int to, long value, int nonce, long gasPrice) {
        Account sender = createAccount(from);
        Account receiver = createAccount(to);

        Transaction tx = getBuilder(sender, receiver, nonce, value)
                .gasPrice(BigInteger.valueOf(gasPrice))
                .build();

        return tx;
    }

    public static Transaction createSampleTransactionWithData(int from, int nonce, String data) {
        Account sender = createAccount(from);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiverAddress(new byte[0])
                .nonce(nonce)
                .data(data)
                .gasLimit(BigInteger.valueOf(1000000))
                .build();

        return tx;
    }
}
