package co.rsk.db;


import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockStoreEncoderTest {

    private BlockStoreEncoder target;
    private BlockFactory blockFactory;

    @Before
    public void setUp() {
        blockFactory = mock(BlockFactory.class);
        target = new BlockStoreEncoder(blockFactory);
    }

    /**
     * Verifies that a block header value is decoded correctly.
     * A BlockStoreEncoder header encode is a RLP list with the encoded header as an element.
     */
    @Test
    public void decodeBlockHeader_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);

        byte[] encodedHeader = new byte[] {0x0A};
        when(blockHeader.getEncoded()).thenReturn(encodedHeader);
        when(blockFactory.decodeHeader(encodedHeader)).thenReturn(blockHeader);
        byte [] rlpHeader = RLP.encodeList(encodedHeader);

        Optional<BlockHeader> result = target.decodeBlockHeader(rlpHeader);

        assertTrue(result.isPresent());
        assertArrayEquals(encodedHeader, result.get().getEncoded());
    }

    /**
     * Verifies that a block value is decoded correctly.
     * A BlockStoreEncoder block encode is the same as the block encode.
     */
    @Test
    public void decodeBlock_success() {
        Block block = mock(Block.class);

        byte [] rlpBlock = RLP.encodeList(new byte[]{0,1,2});
        when(blockFactory.decodeBlock(rlpBlock)).thenReturn(block);
        Optional<Block> result = target.decodeBlock(rlpBlock);

        assertTrue(result.isPresent());
        assertEquals(block, result.get());
    }

    /**
     * Verifies that a header is retrieved from a block encoding.
     */
    @Test
    public void decodeHeaderFromBlock_success() {

        Block block = mock(Block.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);

        byte [] rlpBlock = RLP.encodeList(new byte[]{0, 1, 2});
        when(blockFactory.decodeBlock(rlpBlock)).thenReturn(block);
        Optional<BlockHeader> result = target.decodeBlockHeader(rlpBlock);

        assertTrue(result.isPresent());
        assertEquals(blockHeader, result.get());
    }

    /**
     * Verifies that an invalid encoding throws exception.
     * Validity is only verified by the rlp list size.
     */
    @Test(expected = IllegalArgumentException.class)
    public void decodeBlockHeader_fails() {
        byte [] rlpBlock = RLP.encodeList(new byte[]{0, 1});
        target.decodeBlockHeader(rlpBlock);
    }

    /**
     * Verifies that an invalid encoding throws exception.
     * Validity is only verified by the rlp list size.
     */
    @Test(expected = IllegalArgumentException.class)
    public void decodeBlock_fails() {
        byte [] rlpBlock = RLP.encodeList(new byte[]{0, 1});
        target.decodeBlock(rlpBlock);
    }

    @Test
    public void encodeBlockHeader_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);

        byte[] encodedBlockHeader = RLP.encodeList(new byte[]{0x0F, 0x0A});

        when(blockHeader.getEncoded()).thenReturn(encodedBlockHeader);

        byte[] result = target.encodeBlockHeader(blockHeader);

        assertNotNull(result);

        RLPList rlpList = RLP.decodeList(result);
        assertEquals(1, rlpList.size());
        assertArrayEquals(encodedBlockHeader, rlpList.get(0).getRLPData());

    }

    @Test
    public void encodeBlock_success() {
        Block block = mock(Block.class);

        byte[] encodedBlock = RLP.encodeList(new byte[]{0x01, 0x02, 0x03, 0x04});

        when(block.getEncoded()).thenReturn(encodedBlock);

        byte[] result = target.encodeBlock(block);

        assertNotNull(result);

        RLPList rlpList = RLP.decodeList(result);
        assertArrayEquals(encodedBlock, rlpList.getRLPData());
    }
}
