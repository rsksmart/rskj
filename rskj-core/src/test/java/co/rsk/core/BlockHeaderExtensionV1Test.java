package co.rsk.core;

import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class BlockHeaderExtensionV1Test {
    private static final short[] EDGES = new short[] { 1, 2, 3, 4 };
    private static final short[] NO_EDGES = new short[0];

    @Test
    void createWithLogsBloomAndEdges() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom, EDGES);

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
        Assertions.assertArrayEquals(EDGES, extension.getTxExecutionSublistsEdges());
    }

    @Test
    void setLogsBloom() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        extension.setLogsBloom(logsBloom);

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
    }


    @Test
    void setEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        short[] edges = new short[] { 5, 6, 7, 8};

        extension.setTxExecutionSublistsEdges(edges);

        Assertions.assertArrayEquals(edges, extension.getTxExecutionSublistsEdges());
    }

    @Test
    void hashIncludesLogsBloom() {
        byte[] logsBloom1 = new byte[Bloom.BLOOM_BYTES];
        logsBloom1[0] = 0x01;
        logsBloom1[1] = 0x02;
        logsBloom1[2] = 0x03;
        logsBloom1[3] = 0x04;
        BlockHeaderExtensionV1 extension1 = new BlockHeaderExtensionV1(logsBloom1, EDGES);

        byte[] logsBloom2 = new byte[Bloom.BLOOM_BYTES];
        logsBloom2[0] = 0x01;
        logsBloom2[1] = 0x02;
        logsBloom2[2] = 0x03;
        logsBloom2[3] = 0x05;
        BlockHeaderExtensionV1 extension2 = new BlockHeaderExtensionV1(logsBloom2, EDGES);

        Assertions.assertFalse(Arrays.equals(extension1.getHash(), extension2.getHash()));
    }

    @Test
    void hashIncludesEdges() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;
        BlockHeaderExtensionV1 extension1 = new BlockHeaderExtensionV1(logsBloom, EDGES);

        short[] edges2 = new short[] { 5, 6, 7, 8 };
        BlockHeaderExtensionV1 extension2 = new BlockHeaderExtensionV1(logsBloom, edges2);

        Assertions.assertFalse(Arrays.equals(extension1.getHash(), extension2.getHash()));
    }

    @Test
    void encodeDecode() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        BlockHeaderExtensionV1 extension = BlockHeaderExtensionV1.fromEncoded(
                new BlockHeaderExtensionV1(logsBloom, EDGES).getEncoded()
        );

        Assertions.assertArrayEquals(logsBloom, extension.getLogsBloom());
        Assertions.assertArrayEquals(EDGES, extension.getTxExecutionSublistsEdges());

        extension = BlockHeaderExtensionV1.fromEncoded(
                new BlockHeaderExtensionV1(logsBloom, NO_EDGES).getEncoded()
        );

        Assertions.assertArrayEquals(NO_EDGES, extension.getTxExecutionSublistsEdges());
    }
}
