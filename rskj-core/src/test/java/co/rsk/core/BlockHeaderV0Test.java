package co.rsk.core;

import co.rsk.peg.PegTestUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeaderExtensionV1;
import org.ethereum.core.BlockHeaderV0;
import org.ethereum.core.BlockHeaderV1;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

public class BlockHeaderV0Test {
    private BlockHeaderV0 createBlockHeader(byte[] logsBloom, short[] edges) {
        return new BlockHeaderV0(
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
                edges
        );
    }

    @Test
    void hasNullExtension() {
        short[] edges = new short[]{ 1, 2, 3, 4 };
        BlockHeaderV0 header = createBlockHeader(TestUtils.randomBytes(256), edges);
        Assertions.assertEquals(null, header.getExtension());
    }

    @Test
    void setsExtensionIsVoid() {
        short[] edges = new short[]{ 1, 2, 3, 4 };
        BlockHeaderV0 header = createBlockHeader(TestUtils.randomBytes(256), edges);
        byte[] bloom = Arrays.copyOf(header.getLogsBloom(), header.getLogsBloom().length);
        header.setExtension(new BlockHeaderExtensionV1(TestUtils.randomBytes(256), edges));
        Assertions.assertEquals(null, header.getExtension());
        Assertions.assertArrayEquals(bloom, header.getLogsBloom());
    }

    @Test
    void logsBloomFieldEncoded() {
        byte[] bloom = TestUtils.randomBytes(256);
        short[] edges = new short[]{ 1, 2, 3, 4 };
        BlockHeaderV0 header = createBlockHeader(bloom, edges);
        byte[] field = RLP.decode2(header.getLogsBloomFieldEncoded()).get(0).getRLPData();
        Assertions.assertArrayEquals(bloom, field);
    }
}
