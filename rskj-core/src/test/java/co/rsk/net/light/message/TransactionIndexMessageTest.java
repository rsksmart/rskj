package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransactionIndexMessageTest {

    private long id;
    private long blockNumber;
    private byte[] blockHash;
    private long txIndex;

    @Before
    public void setUp() {
        id = 1L;
        blockNumber = 1000L;
        blockHash = HashUtil.randomHash();
        txIndex = 1234L;
    }

    @Test
    public void createMessage() {
        TransactionIndexMessage message = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);

        assertEquals(LightClientMessageCodes.TRANSACTION_INDEX, message.getCommand());
        assertEquals(id, message.getId());
        assertEquals(blockNumber, message.getBlockNumber());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertEquals(txIndex, message.getTransactionIndex());
        assertNull(message.getAnswerMessage());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        TransactionIndexMessage testMessage = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);
        byte[] encoded = testMessage.getEncoded();

        byte code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();

        LCMessageFactory lcMessageFactory = new LCMessageFactory();
        TransactionIndexMessage message = (TransactionIndexMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());

        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getBlockNumber(), message.getBlockNumber());
        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertEquals(testMessage.getTransactionIndex(), message.getTransactionIndex());

    }
}