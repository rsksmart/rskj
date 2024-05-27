package org.ethereum.util;

import java.math.BigInteger;
import java.util.Optional;

import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.CallArguments;

import co.rsk.core.RskAddress;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;

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
    
    public static CallArguments createArguments(RskAddress sender, RskAddress receiver) {

        // Simulation of the args handled in the sendTransaction call
        CallArguments args = new CallArguments();
        args.setFrom(sender.toJsonString());
        args.setTo(receiver.toJsonString());
        args.setGasLimit("0x76c0");
        args.setGasPrice("0x9184e72a000");
        args.setValue("0x186A0");
        args.setNonce("0x01");

        return args;
    }

    public static Transaction createTransaction(CallArguments args, byte chainId, Account senderAccount) {

        // Transaction that is expected to be constructed WITH the gasLimit
        Transaction tx = Transaction.builder()
                .nonce(HexUtils.stringHexToByteArray(args.getNonce()))
                .gasPrice(HexUtils.strHexOrStrNumberToBigInteger(args.getGasPrice()))
                .gasLimit(HexUtils.strHexOrStrNumberToBigInteger(args.getGasLimit()))
                .destination(HexUtils.strHexOrStrNumberToByteArray(args.getTo()))
                .chainId(chainId)
                .value(HexUtils.strHexOrStrNumberToBigInteger(args.getValue()))
                .build();
        tx.sign(senderAccount.getEcKey().getPrivKeyBytes());

        return tx;
    }

    public static CallArgumentsParam toCallArgumentsParam(CallArguments args) {
        return new CallArgumentsParam(
                Optional.ofNullable(args.getFrom()).filter(p -> !p.isEmpty()).map(HexAddressParam::new).orElse(null),
                Optional.ofNullable(args.getTo()).filter(p -> !p.isEmpty()).map(HexAddressParam::new).orElse(null),
                Optional.ofNullable(args.getGas()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getGasPrice()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getGasLimit()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getNonce()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getChainId()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getValue()).filter(p -> !p.isEmpty()).map(HexNumberParam::new).orElse(null),
                Optional.ofNullable(args.getData()).filter(p -> !p.isEmpty()).map(HexDataParam::new).orElse(null),
                Optional.ofNullable(args.getInput()).filter(p -> !p.isEmpty()).map(HexDataParam::new).orElse(null)
        );
    }

}
