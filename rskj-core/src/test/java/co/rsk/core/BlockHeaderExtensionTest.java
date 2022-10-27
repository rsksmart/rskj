package co.rsk.core;

import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.Bloom;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class BlockHeaderExtensionTest {
    @Test
    public void createFromBlockHeader() {
        BlockHeader header = createBlockHeader();

        BlockHeaderExtension extension = BlockHeaderExtension.fromHeader(header);

        Assert.assertEquals((byte) 0x1, extension.getHeaderVersion());
        Assert.assertArrayEquals(header.getLogsBloom(), extension.getLogsBloom());
    }

    @Test
    public void encodeDecode() {
        BlockHeader header = createBlockHeader();

        BlockHeaderExtension extension = BlockHeaderExtension.fromEncoded(
                BlockHeaderExtension.fromHeader(header).getEncoded()
        );

        Assert.assertEquals((byte) 0x1, extension.getHeaderVersion());
        Assert.assertArrayEquals(header.getLogsBloom(), extension.getLogsBloom());
    }

    @Test
    public void encodeForHeaderMessageIncludesVersion() {
        byte version = getHeaderEncoding(createBlockHeader())[0];

        Assert.assertEquals(0x1, version);
    }

    @Test
    public void encodeForHeaderMessageHashesLogsBloom() {
        byte[] headerEncoding = getHeaderEncoding(createBlockHeader());

        byte[] otherLogsBloom = new byte[Bloom.BLOOM_BYTES];
        otherLogsBloom[0] = 0x01;
        otherLogsBloom[1] = 0x02;
        otherLogsBloom[2] = 0x03;
        otherLogsBloom[3] = 0x05;
        BlockHeader otherHeader = createBlockHeader();
        otherHeader.setLogsBloom(otherLogsBloom);
        byte[] otherHeaderEncoding = getHeaderEncoding(otherHeader);

        byte[] hash = extractHash(headerEncoding);
        byte[] otherHash = extractHash(otherHeaderEncoding);

        Assert.assertFalse(Arrays.equals(hash, otherHash));
    }

    @Test
    public void encodeForHeaderMessageIsPaddedWithZeros() {
        // 0 is the primitive of byte, no need to add Arrays.fill in src
        byte[] headerEncoding = getHeaderEncoding(createBlockHeader());

        byte[] pad = new byte[headerEncoding.length - 33];
        Arrays.fill(pad, (byte) 0x0);
        Assert.assertArrayEquals(pad,  Arrays.copyOfRange(headerEncoding, 33, headerEncoding.length));
    }

    private byte[] getHeaderEncoding(BlockHeader blockHeader) {
        return RLP.decodeFirstElement(BlockHeaderExtension.fromEncoded(
                BlockHeaderExtension.fromHeader(blockHeader).getEncoded()
        ).getEncodedForHeaderMessage(), 0).getRLPData();
    }

    private byte[] extractHash(byte[] encodedHeader) {
        return Arrays.copyOfRange(encodedHeader, 1, 33);
    }

    private BlockHeader createBlockHeader() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
        BlockHeader header = new BlockHeaderBuilder(ActivationConfigsForTest.all())
                .setLogsBloom(logsBloom)
                .build();
        return header;
    }
}
