package co.rsk.core;

import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class BlockHeaderExtensionV1Test {
    @Test
    public void hasVersion1 () {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(TestUtils.randomBytes(256));
        Assertions.assertEquals(1, extension.getHeaderVersion());
    }

    @Test
    public void createWithLogsBloom() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom);

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }

    @Test
    public void setLogsBloom() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32]);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        extension.setLogsBloom(logsBloom);

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }

    @Test
    public void hashIncludesLogsBloom() {
        byte[] logsBloom1 = new byte[Bloom.BLOOM_BYTES];
        logsBloom1[0] = 0x01;
        logsBloom1[1] = 0x02;
        logsBloom1[2] = 0x03;
        logsBloom1[3] = 0x04;
        BlockHeaderExtensionV1 extension1 = new BlockHeaderExtensionV1(logsBloom1);

        byte[] logsBloom2 = new byte[Bloom.BLOOM_BYTES];
        logsBloom2[0] = 0x01;
        logsBloom2[1] = 0x02;
        logsBloom2[2] = 0x03;
        logsBloom2[3] = 0x05;
        BlockHeaderExtensionV1 extension2 = new BlockHeaderExtensionV1(logsBloom2);

        Assertions.assertFalse(Arrays.equals(extension1.getHash(), extension2.getHash()));
    }

    @Test
    public void encodeDecode() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        BlockHeaderExtensionV1 extension = BlockHeaderExtensionV1.fromEncoded(
                new BlockHeaderExtensionV1(logsBloom).getEncoded()
        );

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }
}
