package co.rsk.net.light.message;

import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.net.light.LightClientMessageCodes.BLOCK_HEADER;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class BlockHeaderMessageTest {

    private long id;
    private byte[] blockHeaderHash;
    private BlockHeader blockHeader;
    private BlockHeaderMessage testMessage;
    private LCMessageFactory messageFactory;

    @Before
    public void setUp() {
        id = 1;
        blockHeaderHash = randomHash();
        byte[] fullBlockHeaderHash = randomHash();
        BlockFactory blockFactory = mock(BlockFactory.class);
        blockHeader = mock(BlockHeader.class);
        messageFactory = new LCMessageFactory(blockFactory);
        testMessage = new BlockHeaderMessage(id, blockHeader);
        when(blockHeader.getEncoded()).thenReturn(blockHeaderHash);
        when(blockHeader.getFullEncoded()).thenReturn(fullBlockHeaderHash);
        when(blockFactory.decodeHeader(fullBlockHeaderHash)).thenReturn(blockHeader);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        assertEquals(id, testMessage.getId());
        assertArrayEquals(blockHeaderHash, testMessage.getBlockHeader().getEncoded());
        assertNull(testMessage.getAnswerMessage());
        assertEquals(BLOCK_HEADER, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        byte[] encoded = testMessage.getEncoded();

        BlockHeaderMessage blockHeaderMessage = (BlockHeaderMessage) messageFactory.create(BLOCK_HEADER.asByte(), encoded);

        assertEquals(id, blockHeaderMessage.getId());
        assertArrayEquals(blockHeader.getEncoded(), blockHeaderMessage.getBlockHeader().getEncoded());
        assertEquals(BLOCK_HEADER, blockHeaderMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), blockHeaderMessage.getAnswerMessage());
        assertArrayEquals(encoded, blockHeaderMessage.getEncoded());
    }
}
