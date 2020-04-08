package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class GetAccountsMessageTest {

    private long id;
    private byte[] blockHash;
    private byte[] addressHash;

    @Before
    public void setUp() {
        id = 1;
        blockHash = HashUtil.randomHash();
        addressHash = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        GetAccountsMessage testMessage = new GetAccountsMessage(id, blockHash, addressHash);
        assertEquals(LightClientMessageCodes.GET_ACCOUNTS, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getBlockHash(), blockHash);
        assertArrayEquals(testMessage.getAddressHash(), addressHash);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {

        GetAccountsMessage testMessage = new GetAccountsMessage(id, blockHash, addressHash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.GET_ACCOUNTS.asByte();
        GetAccountsMessage message = (GetAccountsMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertArrayEquals(testMessage.getAddressHash(), message.getAddressHash());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

}
