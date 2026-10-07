package org.ethereum.core;

import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockHeaderExtensionCodecTest {
    private static final short[] EDGES = new short[] { 1, 2, 3, 4 };

    // Golden fixtures: expected bytes were captured from the current implementation
    // before any refactor; the refactor must reproduce them byte for byte.
    private static final String GOLDEN_V1_BLOOM =
            "6a66323a9e4d08c68b9591d3216c5b8d18eb631fdf0fecb3ca9f08656eb25317e851ef7dec705b1252743c7ff56b37ded80afdf6ba729c877070060c416faeb515cba865daffc0f74f2a7365452a7141b168c15f222fab57d2958896a1c82c5a923dfe638c9d9f21a422891023fa8c9aad4533295d0289d18c2702148f1d3dd8746cec9489619e6b052f0583a2d38a118dbd6bf955804f3f0df9d00f543e70aac36c8d923e8071e03d4628ea5e706cc341e9cf3919c4dfdb19c0e1aafbd0f4d5fc6fedd27b836389a9921d5af177b26d71d157975ab0f6bc488b42eed2c56ae7905ebeaef4129d13bbd0ad817fbca236f730d128efbf586684881d08eddb0036";
    private static final String GOLDEN_V2_BLOOM =
            "57afbc42df5c50a2b79c978bf534cf093f95ddba9a3bd04c11fa4ae8658500458f01ee2ba7a657487425abe67b4d2cd4e49ef79d0c1e985706b1aa6097c8d30516579bb4f52f842a217955fdb0886ceabda21729ebeb1fc47d69021eacbedffef6cce54245d508918ef0f9b4f1334535672f01f8de0866f12ac5859c3bc95b9d7faa97ad088ac8a66ee2846db08e0aa402de12c8bfea8e02e2dde8f031c952a2c4e55dba21c3de0adbfb2fb6f278cc21b49e43781dac79f620a6415c4c354c8c1cb8fdc26ab1da31d02849d8d171eab62615cde9bb6e64a582d984c9a08091fba142f83e304c37acae0cbe5ffbe5b810050515a40f8eca484a7d80d616d1490a";
    private static final String GOLDEN_V2_BASE_EVENT =
            "b6925d33ad9d6297a4907256a7e5f2683102537aa2909bb6c17b4f1adc51b38d";

    @Test
    public void decodeV1() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 0x00;
        logsBloom[1] = 0x02;
        logsBloom[2] = 0x03;
        logsBloom[3] = 0x04;

        short[] edges = { 1, 2, 3, 4 };

        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(logsBloom, edges);

        BlockHeaderExtension decoded = BlockHeaderExtensionCodec.fromEncoded(
                BlockHeaderExtensionCodec.toEncoded(extension)
        );

        Assertions.assertArrayEquals(extension.getHash(), decoded.getHash());
    }

    @Test
    void decodeV2() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(
                Hex.decode(GOLDEN_V2_BLOOM), EDGES, Hex.decode(GOLDEN_V2_BASE_EVENT));

        BlockHeaderExtension decoded = BlockHeaderExtensionCodec.fromEncoded(
                BlockHeaderExtensionCodec.toEncoded(extension)
        );

        Assertions.assertInstanceOf(BlockHeaderExtensionV2.class, decoded);
        Assertions.assertArrayEquals(extension.getHash(), decoded.getHash());
    }

    @Test
    void invalidDecode() {
        byte version = 0;
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        short[] edges = { 1, 2, 3, 4 };

        Assertions.assertThrows(IllegalArgumentException.class, () -> BlockHeaderExtensionCodec.fromEncoded(
                RLP.encodeList(
                        RLP.encodeByte(version),
                        RLP.encodeList(
                                RLP.encodeElement(logsBloom),
                                ByteUtil.shortsToRLP(edges)
                        )
                )
        ), "Unknown extension with version: " + version);
    }

    @Test
    void decodeRejectsUnknownVersionThree() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        short[] edges = { 1, 2, 3, 4 };

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(unknownVersionEncoding((byte) 0x3, logsBloom, edges)));

        Assertions.assertEquals("Unknown extension with version: 3", ex.getMessage());
    }

    @Test
    void decodeRejectsOneElementOuterList() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(RLP.encodeList(RLP.encodeByte((byte) 0x1))));

        Assertions.assertEquals("Invalid extension encoding", ex.getMessage());
    }

    @Test
    void decodeRejectsThreeElementOuterList() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(RLP.encodeList(
                        RLP.encodeByte((byte) 0x1),
                        RLP.encodeElement(new byte[] { 0x01 }),
                        RLP.encodeElement(new byte[] { 0x02 }))));

        Assertions.assertEquals("Invalid extension encoding", ex.getMessage());
    }

    @Test
    void decodeRejectsNonListPayload() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(RLP.encodeElement(new byte[] { 0x01 })));

        Assertions.assertEquals("The decoded element wasn't a list", ex.getMessage());
    }

    @Test
    void decodeRejectsTwoTopLevelItems() {
        byte[] twoLists = ArrayUtils.addAll(
                RLP.encodeList(RLP.encodeByte((byte) 0x1)),
                RLP.encodeList(RLP.encodeByte((byte) 0x2)));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(twoLists));

        Assertions.assertEquals("Expected one RLP item but got 2", ex.getMessage());
    }

    @Test
    void decodeTreatsEmptyVersionElementAsVersionZero() {
        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        short[] edges = { 1, 2, 3, 4 };

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.fromEncoded(RLP.encodeList(
                        RLP.encodeElement(new byte[0]),
                        RLP.encodeList(
                                RLP.encodeElement(logsBloom),
                                ByteUtil.shortsToRLP(edges)))));

        Assertions.assertEquals("Unknown extension with version: 0", ex.getMessage());
    }

    @Test
    void encodeRejectsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> BlockHeaderExtensionCodec.toEncoded(null));
    }

    @Test
    void encodeRejectsNonMemberExtension() {
        // Pins the current instanceof guard. The guard and this test go away once the
        // interface is sealed: a non-member then fails to compile instead of being
        // rejected at runtime.
        BlockHeaderExtension nonMember = new BlockHeaderExtension() {
            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }

            @Override
            public byte[] getHash() {
                return new byte[0];
            }

            @Override
            public byte getVersion() {
                return 0x3;
            }
        };

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> BlockHeaderExtensionCodec.toEncoded(nonMember));

        Assertions.assertEquals("Unknown extension", ex.getMessage());
    }

    @Test
    void goldenOuterEncodingV1WithEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), EDGES);

        Assertions.assertArrayEquals(Hex.decode(
                "f9011301" + "b9010f" + "f9010c" + "b90100" + GOLDEN_V1_BLOOM + "880100020003000400"),
                BlockHeaderExtensionCodec.toEncoded(extension));
    }

    @Test
    void goldenOuterEncodingV1WithoutEdges() {
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(Hex.decode(GOLDEN_V1_BLOOM), null);

        Assertions.assertArrayEquals(Hex.decode(
                "f9010a01" + "b90106" + "f90103" + "b90100" + GOLDEN_V1_BLOOM),
                BlockHeaderExtensionCodec.toEncoded(extension));
    }

    @Test
    void goldenOuterEncodingV2WithEdgesAndBaseEvent() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(
                Hex.decode(GOLDEN_V2_BLOOM), EDGES, Hex.decode(GOLDEN_V2_BASE_EVENT));

        Assertions.assertArrayEquals(Hex.decode(
                "f9013402" + "b90130" + "f9012d" + "b90100" + GOLDEN_V2_BLOOM + "a0" + GOLDEN_V2_BASE_EVENT + "880100020003000400"),
                BlockHeaderExtensionCodec.toEncoded(extension));
    }

    @Test
    void goldenOuterEncodingV2WithNullBaseEvent() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(Hex.decode(GOLDEN_V2_BLOOM), EDGES, null);

        Assertions.assertArrayEquals(Hex.decode(
                "f9011402" + "b90110" + "f9010d" + "b90100" + GOLDEN_V2_BLOOM + "80" + "880100020003000400"),
                BlockHeaderExtensionCodec.toEncoded(extension));
    }

    @Test
    void goldenOuterEncodingV2WithNullEdges() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(
                Hex.decode(GOLDEN_V2_BLOOM), null, Hex.decode(GOLDEN_V2_BASE_EVENT));

        Assertions.assertArrayEquals(Hex.decode(
                "f9012b02" + "b90127" + "f90124" + "b90100" + GOLDEN_V2_BLOOM + "a0" + GOLDEN_V2_BASE_EVENT),
                BlockHeaderExtensionCodec.toEncoded(extension));
    }

    private static byte[] unknownVersionEncoding(byte version, byte[] logsBloom, short[] edges) {
        return RLP.encodeList(
                RLP.encodeByte(version),
                RLP.encodeList(
                        RLP.encodeElement(logsBloom),
                        ByteUtil.shortsToRLP(edges)));
    }
}
