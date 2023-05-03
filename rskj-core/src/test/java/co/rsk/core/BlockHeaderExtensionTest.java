package co.rsk.core;

import com.google.common.collect.Lists;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.Bloom;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockHeaderExtensionTest {
    @Test
    public void decodeV1() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        short[] edges = { 1, 2, 3, 4 };

        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom, edges);

        BlockHeaderExtension decoded = BlockHeaderExtension.fromEncoded(
                RLP.decodeList(extension.getEncoded())
        );

        Assertions.assertArrayEquals(extension.getHash(), decoded.getHash());
    }

    @Test
    void invalidDecode() {
        byte version = 0;
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        short[] edges = { 1, 2, 3, 4 };

        BlockHeaderExtension decoded = BlockHeaderExtension.fromEncoded(
                new RLPList(RLP.encodeList(
                        RLP.encodeByte(version),
                        RLP.encodeElement(logsBloom),
                        ByteUtil.shortsToRLP(edges)
                ), 0)
        );

        Assertions.assertNull(decoded);
    }
}
