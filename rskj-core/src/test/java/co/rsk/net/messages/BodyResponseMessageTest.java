package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;

class BodyResponseMessageTest {

    private BodyResponseMessage testCreateMessage(BlockHeaderExtension extension) {
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

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles, extension);

        Assertions.assertEquals(100, message.getId());

        Assertions.assertNotNull(message.getTransactions());
        Assertions.assertEquals(transactions.size(), message.getTransactions().size());

        Assertions.assertEquals(
                transactions,
                message.getTransactions());

        Assertions.assertNotNull(message.getUncles());
        Assertions.assertEquals(uncles.size(), message.getUncles().size());

        for (int k = 0; k < uncles.size(); k++) {
            Assertions.assertArrayEquals(uncles.get(k).getFullEncoded(), message.getUncles().get(k).getFullEncoded());
        }

        return  message;
    }

    private BodyResponseMessage encodeAndDecodeMessage(BodyResponseMessage message) {
        return (BodyResponseMessage) Message.create(new BlockFactory(new TestSystemProperties().getActivationConfig()), message.getEncoded());
    }

    @Test
    void createMessage() {
        BodyResponseMessage message = testCreateMessage(null);
        Assertions.assertNull(message.getBlockHeaderExtension());
        message = encodeAndDecodeMessage(message);
        Assertions.assertNull(message.getBlockHeaderExtension());
    }

    @Test
    void createMessageWithExtension() {
        Bloom bloom = new Bloom();
        short[] edges = new short[]{ 1, 2, 3, 4 };
        BlockHeaderExtension extension = new BlockHeaderExtensionV1(bloom.getData(), edges);
        BodyResponseMessage message = testCreateMessage(extension);
        Assertions.assertArrayEquals(extension.getEncoded(), message.getBlockHeaderExtension().getEncoded());
        message = encodeAndDecodeMessage(message);
        Assertions.assertArrayEquals(extension.getEncoded(), message.getBlockHeaderExtension().getEncoded());
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
    void typedTransactionRoundTrip() {
        List<Transaction> transactions = new ArrayList<>();

        AccountBuilder acbuilder = new AccountBuilder();
        transactions.add(new TransactionBuilder()
                .sender(acbuilder.name("legacySender").build())
                .receiver(acbuilder.name("legacyReceiver").build())
                .value(BigInteger.valueOf(1000)).nonce(0)
                .build());

        transactions.add(new TransactionBuilder()
                .sender(acbuilder.name("type1Sender").build())
                .receiver(acbuilder.name("type1Receiver").build())
                .value(BigInteger.valueOf(2000)).nonce(0)
                .transactionType((byte) 1)
                .build());

        transactions.add(new TransactionBuilder()
                .sender(acbuilder.name("rskSender").build())
                .receiver(acbuilder.name("rskReceiver").build())
                .value(BigInteger.valueOf(3000)).nonce(0)
                .transactionType((byte) 2)
                .rskSubtype((byte) 5)
                .build());

        List<BlockHeader> uncles = new ArrayList<>();
        BodyResponseMessage message = new BodyResponseMessage(42, transactions, uncles, null);
        BodyResponseMessage decoded = encodeAndDecodeMessage(message);

        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(3, decoded.getTransactions().size());

        Transaction decodedLegacy = decoded.getTransactions().get(0);
        Assertions.assertTrue(decodedLegacy.getTypePrefix().isLegacy());
        Assertions.assertArrayEquals(transactions.get(0).getHash().getBytes(), decodedLegacy.getHash().getBytes());

        Transaction decodedType1 = decoded.getTransactions().get(1);
        Assertions.assertTrue(decodedType1.getTypePrefix().isTyped());
        Assertions.assertFalse(decodedType1.getTypePrefix().isRskNamespace());
        Assertions.assertArrayEquals(transactions.get(1).getHash().getBytes(), decodedType1.getHash().getBytes());

        Transaction decodedRsk = decoded.getTransactions().get(2);
        Assertions.assertTrue(decodedRsk.getTypePrefix().isRskNamespace());
        Assertions.assertInstanceOf(TransactionTypePrefix.RskNamespace.class, decodedRsk.getTypePrefix());
        Assertions.assertEquals((byte) 5, ((TransactionTypePrefix.RskNamespace) decodedRsk.getTypePrefix()).subtype());
        Assertions.assertArrayEquals(transactions.get(2).getHash().getBytes(), decodedRsk.getHash().getBytes());
    }

    @Test
    void accept() {
        List<Transaction> transactions = new LinkedList<>();
        List<BlockHeader> uncles = new LinkedList<>();

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles, null);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
