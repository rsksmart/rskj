package co.rsk.core;

import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.Bloom;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class BlockHeaderExtensionTest {
    @Test
    public void decodeV1() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom);

        BlockHeaderExtension decoded = BlockHeaderExtension.fromEncoded(
                RLP.decodeList(extension.getEncoded())
        );

        Assertions.assertEquals(extension.getHeaderVersion(), decoded.getHeaderVersion());
        Assertions.assertArrayEquals(extension.getHash(), extension.getHash());
    }
}
