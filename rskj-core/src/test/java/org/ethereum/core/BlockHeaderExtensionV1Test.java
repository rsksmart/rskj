package org.ethereum.core;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class BlockHeaderExtensionV1Test {
    private static final short[] EDGES = new short[] { 1, 2, 3, 4 };
    private static final short[] NO_EDGES = new short[0];

    // Golden fixtures: the bloom input and expected bytes were captured from the
    // current implementation before any refactor; the refactor must reproduce the
    // encodings and hashes byte for byte.
    private static final String GOLDEN_V1_BLOOM =
            "6a66323a9e4d08c68b9591d3216c5b8d18eb631fdf0fecb3ca9f08656eb25317e851ef7dec705b1252743c7ff56b37ded80afdf6ba729c877070060c416faeb515cba865daffc0f74f2a7365452a7141b168c15f222fab57d2958896a1c82c5a923dfe638c9d9f21a422891023fa8c9aad4533295d0289d18c2702148f1d3dd8746cec9489619e6b052f0583a2d38a118dbd6bf955804f3f0df9d00f543e70aac36c8d923e8071e03d4628ea5e706cc341e9cf3919c4dfdb19c0e1aafbd0f4d5fc6fedd27b836389a9921d5af177b26d71d157975ab0f6bc488b42eed2c56ae7905ebeaef4129d13bbd0ad817fbca236f730d128efbf586684881d08eddb0036";

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
    void withLogsBloomReturnsNewInstance() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x01;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        BlockHeaderExtensionV1 updated = extension.withLogsBloom(logsBloom);

        Assertions.assertArrayEquals(logsBloom, updated.getLogsBloom());
        // the source instance is untouched: value objects are immutable
        Assertions.assertArrayEquals(new byte[32], extension.getLogsBloom());
    }

    @Test
    void withLogsBloomRejectsNull() {
        // Pinned quirk, kept from the old setter: null throws NullPointerException
        // (Arrays.copyOf) while the constructor accepts null.
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        Assertions.assertThrows(NullPointerException.class, () -> extension.withLogsBloom(null));
    }

    @Test
    void withEdgesReturnsNewInstance() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        short[] edges = new short[] { 5, 6, 7, 8};

        BlockHeaderExtensionV1 updated = extension.withTxExecutionSublistsEdges(edges);

        Assertions.assertArrayEquals(edges, updated.getTxExecutionSublistsEdges());
        // the source instance is untouched: value objects are immutable
        Assertions.assertArrayEquals(EDGES, extension.getTxExecutionSublistsEdges());
    }

    @Test
    void withEdgesAcceptsNull() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(new byte[32], EDGES);

        BlockHeaderExtensionV1 updated = extension.withTxExecutionSublistsEdges(null);

        Assertions.assertNull(updated.getTxExecutionSublistsEdges());
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

    @Test
    void goldenEncodingWithEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), EDGES);

        Assertions.assertArrayEquals(Hex.decode("f9010c" + "b90100" + GOLDEN_V1_BLOOM + "880100020003000400"),
                extension.getEncoded());
    }

    @Test
    void goldenHashWithEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), EDGES);

        Assertions.assertArrayEquals(Hex.decode("f26c14ea90289d65601ee7304730c0eea1e5bec5777d12d0c8f240279434149f"),
                extension.getHash());
    }

    @Test
    void goldenEncodingWithoutEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), null);

        Assertions.assertArrayEquals(Hex.decode("f90103" + "b90100" + GOLDEN_V1_BLOOM),
                extension.getEncoded());
    }

    @Test
    void goldenHashWithoutEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), null);

        Assertions.assertArrayEquals(Hex.decode("5df7f958b248cf0c68050a2a5b215f0d7d707f14565b853798da76f4b4e59fba"),
                extension.getHash());
    }
}
