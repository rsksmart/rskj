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

public class StorageMessageTest {

    private long id;
    private byte[] merkleInclusionProof;
    private byte[] storageValue;

    @Before
    public void setUp() {
        id = 1;
        merkleInclusionProof = HashUtil.randomHash();
        storageValue = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        StorageMessage testMessage = new StorageMessage(id, merkleInclusionProof, storageValue);
        assertEquals(LightClientMessageCodes.STORAGE, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getMerkleInclusionProof(), merkleInclusionProof);
        assertArrayEquals(testMessage.getStorageValue(), storageValue);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        StorageMessage testMessage = new StorageMessage(id, merkleInclusionProof, storageValue);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.STORAGE.asByte();
        StorageMessage message = (StorageMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getMerkleInclusionProof(), message.getMerkleInclusionProof());
        assertArrayEquals(testMessage.getStorageValue(), message.getStorageValue());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }
}
