package co.rsk.core;

import co.rsk.peg.PegTestUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.BlockHeaderV1;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Arrays;

public class BlockHeaderV1Test {
    private BlockHeaderV1 createBlockHeader(byte[] logsBloom) {
        return new BlockHeaderV1(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                new RskAddress(TestUtils.randomAddress().getBytes()),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                logsBloom,
                new BlockDifficulty(BigInteger.ONE),
                1,
                BigInteger.valueOf(6800000).toByteArray(),
                3000000,
                7731067,
                new byte[0],
                Coin.ZERO,
                new byte[80],
                new byte[32],
                new byte[128],
                new byte[0],
                Coin.valueOf(10L),
                0,
                false,
                false,
                false,
                null,
                new short[0],
                false
        );
    }

    @Test
    void createsAnExtensionWithGivenData() {
        byte[] bloom = TestUtils.randomBytes(256);
        BlockHeaderV1 header = createBlockHeader(bloom);
        Assertions.assertArrayEquals(bloom, header.getExtension().getLogsBloom());
    }

    @Test
    void setsExtension() {
        byte[] bloom = TestUtils.randomBytes(256);
        short[] edges = new short[]{ 1, 2, 3, 4 };
        BlockHeaderV1 header = createBlockHeader(bloom);
        BlockHeaderExtensionV1 extension = new BlockHeaderExtensionV1(bloom, edges);
        header.setExtension(extension);
        Assertions.assertArrayEquals(extension.getEncoded(), header.getExtension().getEncoded());
    }

    @Test
    void setsLogsBloomToExtension() {
        byte[] bloom = TestUtils.randomBytes(256);
        BlockHeaderV1 header = createBlockHeader(new byte[]{});
        header.setLogsBloom(bloom);
        Assertions.assertArrayEquals(bloom, header.getExtension().getLogsBloom());
    }

    @Test
    void logsBloomFieldEncoded() {
        byte[] bloom = TestUtils.randomBytes(256);
        BlockHeaderV1 header = createBlockHeader(bloom);
        RLPList extensionDataRLP = RLP.decodeList(header.getExtensionData());

        byte version = extensionDataRLP.get(0).getRLPData()[0];
        byte[] extensionHash = extensionDataRLP.get(1).getRLPData();

        Assertions.assertEquals((byte) 0x1, version);
        Assertions.assertArrayEquals(header.getExtension().getHash(), extensionHash);
    }

    BlockHeaderV1 encodedHeaderWithRandomLogsBloom() {
        return createBlockHeader(TestUtils.randomBytes(256));
    }

    @Test
    void logsBloomFieldEncodedIncludesExtensionHash() {
        BlockHeaderV1 header = encodedHeaderWithRandomLogsBloom();
        BlockHeaderExtensionV1 extension = Mockito.mock(BlockHeaderExtensionV1.class);
        byte[] hash = TestUtils.randomBytes(32);
        Mockito.when(extension.getHash()).thenReturn(hash);
        header.setExtension(extension);

        BlockHeaderV1 otherHeader = encodedHeaderWithRandomLogsBloom();
        BlockHeaderExtensionV1 otherExtension = Mockito.mock(BlockHeaderExtensionV1.class);
        byte[] otherHash = TestUtils.randomBytes(32);
        Mockito.when(otherExtension.getHash()).thenReturn(otherHash);
        otherHeader.setExtension(otherExtension);

        Assertions.assertFalse(Arrays.equals(header.getExtensionData(), otherHeader.getExtensionData()));
    }
}
