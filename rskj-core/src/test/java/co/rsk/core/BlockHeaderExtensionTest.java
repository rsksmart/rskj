package co.rsk.core;

import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.Bloom;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockHeaderExtensionTest {
    @Test
    public void decodeV1() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x00;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        short[] edges = { 1, 2, 3, 4 };

        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom, edges);

        BlockHeaderExtension decoded = BlockHeaderExtension.fromEncoded(
                BlockHeaderExtension.toEncoded(extension)
        );

        Assertions.assertArrayEquals(extension.getHash(), decoded.getHash());
    }

    @Test
    void invalidDecode() {
        byte version = 0;
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        short[] edges = { 1, 2, 3, 4 };

        Assertions.assertThrows(IllegalArgumentException.class, () -> BlockHeaderExtension.fromEncoded(
                RLP.encodeList(
                        RLP.encodeByte(version),
                        RLP.encodeList(
                                RLP.encodeElement(logsBloom),
                                ByteUtil.shortsToRLP(edges)
                        )
                )
        ), "Unknown extension with version: " + version);
    }
}
