package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class GetBlockReceiptsMessageTest {

    private byte[] hash;

    @Before
    public void setUp() {
        hash = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        GetBlockReceiptsMessage testMessage = new GetBlockReceiptsMessage(id, hash);
        assertEquals(LightClientMessageCodes.GET_BLOCK_RECEIPTS, testMessage.getCommand());
        assertArrayEquals(testMessage.getBlockHash(), hash);
        assertEquals(testMessage.getId(), id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        createMessageAndAssertEncodeDecode(id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroId() {
        long id = 0;
        createMessageAndAssertEncodeDecode(id);
    }

    private void createMessageAndAssertEncodeDecode(long id) {
        GetBlockReceiptsMessage testMessage = new GetBlockReceiptsMessage(id, hash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        GetBlockReceiptsMessage message = (GetBlockReceiptsMessage) lcMessageFactory.create((byte) 1, encoded);

        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

}
