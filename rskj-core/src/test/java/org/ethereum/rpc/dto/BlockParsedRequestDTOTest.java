package org.ethereum.rpc.dto;

import org.bouncycastle.util.encoders.DecoderException;
import org.ethereum.TestUtils;
import org.junit.Test;

import static org.ethereum.rpc.TypeConverter.toJsonHex;
import static org.junit.Assert.assertEquals;

public class BlockParsedRequestDTOTest {

    @Test
    public void blockParsedRequest_blockNumberConstructor_existingConversion() {
        String blockNumberOrString = "earliest";
        BlockParsedRequestDTO blockParsedRequestDTO = new BlockParsedRequestDTO(blockNumberOrString);

        assertEquals(true, blockParsedRequestDTO.getUseBlockNumber());
        assertEquals(blockNumberOrString, blockParsedRequestDTO.getBlockNumber());
    }

    @Test
    public void blockParsedRequest_blockNumberConstructor_blockNumber() {
        String blockNumberOrString = "0x2";
        BlockParsedRequestDTO blockParsedRequestDTO = new BlockParsedRequestDTO(blockNumberOrString, null, null);

        assertEquals(true, blockParsedRequestDTO.getUseBlockNumber());
        assertEquals(blockNumberOrString, blockParsedRequestDTO.getBlockNumber());
    }

    @Test
    public void blockParsedRequest_blockNumberConstructor_invalidBlockHash() {
        String invalidBlockHash = "wewewerer";
        TestUtils.assertThrows(
                DecoderException.class,
                () -> new BlockParsedRequestDTO(null, invalidBlockHash, true));
    }

    @Test
    public void blockParsedRequest_blockNumberConstructor_validBlockHash() {
        String hashString = "0xf88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0";
        Boolean requireCanonical = true;

        BlockParsedRequestDTO result = new BlockParsedRequestDTO(null, hashString, requireCanonical);

        assertEquals(hashString, result.getBlockHashAsString());
        assertEquals(hashString, toJsonHex(result.getBlockHash()));

        assertEquals(false, result.getUseBlockNumber());
        assertEquals(requireCanonical, result.getRequireCanonical());
    }

    @Test
    public void blockParsedRequest_blockNumberConstructor_validBlockHashCanonicalSetOnNull() {
        String hashString = "0xf88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0";
        Boolean requireCanonical = null;

        BlockParsedRequestDTO result = new BlockParsedRequestDTO(null, hashString, requireCanonical);

        Boolean expectedDefaultCanonical = false;
        assertEquals(expectedDefaultCanonical, result.getRequireCanonical());
    }


}
