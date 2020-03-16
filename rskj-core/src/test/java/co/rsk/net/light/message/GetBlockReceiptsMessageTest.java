package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class GetBlockReceiptsMessageTest {

    private byte[] hash;
    private int id;

    @Before
    public void setUp() {
        hash = HashUtil.randomHash();
        id = 1;
    }

    @Test
    public void messageCreationShouldBeCorrect() {

        GetBlockReceiptsMessage testMessage = new GetBlockReceiptsMessage(id, hash);
        assertEquals(testMessage.getCommand(), LightClientMessageCodes.GET_BLOCK_RECEIPTS);
        assertArrayEquals(testMessage.getBlockHash(), hash);
        assertEquals(testMessage.getId(), id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {

        GetBlockReceiptsMessage testMessage = new GetBlockReceiptsMessage(id, hash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory();
        GetBlockReceiptsMessage message = (GetBlockReceiptsMessage) lcMessageFactory.create((byte) 1, encoded);

        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

}
