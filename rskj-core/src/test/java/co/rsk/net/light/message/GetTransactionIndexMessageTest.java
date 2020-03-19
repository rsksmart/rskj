package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class GetTransactionIndexMessageTest {

    private byte[] txHash;
    private int id;

    @Before
    public void setUp() {
        txHash = HashUtil.randomHash();
        id = 1;
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        GetTransactionIndexMessage testMessage = new GetTransactionIndexMessage(id, txHash);
        assertEquals(LightClientMessageCodes.GET_TRANSACTION_INDEX, testMessage.getCommand());
        assertArrayEquals(testMessage.getTxHash(), txHash);
        assertEquals(testMessage.getId(), id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {

        GetTransactionIndexMessage testMessage = new GetTransactionIndexMessage(id, txHash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory();
        byte code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
        GetTransactionIndexMessage message = (GetTransactionIndexMessage) lcMessageFactory.create(code, encoded);

        assertArrayEquals(testMessage.getTxHash(), message.getTxHash());
        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

}
