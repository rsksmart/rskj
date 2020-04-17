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

public class GetStorageMessageTest {

    private long id;
    private byte[] blockHash;
    private byte[] addressHash;
    private byte[] storageKeyHash;

    @Before
    public void setUp() {
        id = 1;
        blockHash = HashUtil.randomHash();
        addressHash = HashUtil.randomHash();
        storageKeyHash = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        GetStorageMessage testMessage = new GetStorageMessage(id, blockHash, addressHash, storageKeyHash);
        assertEquals(LightClientMessageCodes.GET_STORAGE, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getBlockHash(), blockHash);
        assertArrayEquals(testMessage.getAddressHash(), addressHash);
        assertArrayEquals(testMessage.getStorageKeyHash(), storageKeyHash);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        GetStorageMessage testMessage = new GetStorageMessage(id, blockHash, addressHash, storageKeyHash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.GET_STORAGE.asByte();
        GetStorageMessage message = (GetStorageMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertArrayEquals(testMessage.getAddressHash(), message.getAddressHash());
        assertArrayEquals(testMessage.getStorageKeyHash(), message.getStorageKeyHash());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }
}
