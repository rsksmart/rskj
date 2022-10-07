package co.rsk.core;

import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.Bloom;
import org.junit.Assert;
import org.junit.Test;

public class BlockHeaderExtensionTest {
    @Test
    public void createFromBlockHeader() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
        BlockHeader header = new BlockHeaderBuilder(ActivationConfigsForTest.all())
                .setLogsBloom(logsBloom)
                .build();

        BlockHeaderExtension extension = BlockHeaderExtension.fromHeader(header);

        Assert.assertEquals((byte) 0x1, extension.getHeaderVersion());
        Assert.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }

    @Test
    public void encodeDecode() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
        BlockHeader header = new BlockHeaderBuilder(ActivationConfigsForTest.all())
                .setLogsBloom(logsBloom)
                .build();

        BlockHeaderExtension extension = BlockHeaderExtension.fromEncoded(
                BlockHeaderExtension.fromHeader(header).getEncoded()
        );

        Assert.assertEquals((byte) 0x1, extension.getHeaderVersion());
        Assert.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }
}
