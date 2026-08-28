/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.core;

import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.remasc.RemascTransaction;
import com.typesafe.config.ConfigFactory;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block whose body holds nothing but its REMASC transaction and no uncles has a body that follows
 * from its header, so the sync does not have to ask a peer for it. These tests pin that claim
 * against real mainnet blocks: the vectors carry the transaction hash, the transaction RLP and the
 * transactions trie root that the chain itself committed to, on both sides of the RSKIP126 trie
 * encoding change.
 */
class DerivableBodyTest {

    private static final String VECTORS = "/derivation/remasc-body-vectors.csv";

    /**
     * The real mainnet activation config, not a test fixture with everything switched on. Both
     * details that this optimization depends on are properties of mainnet specifically: RSKIP126
     * activates at 1,591,000 (so the trie encoding differs across the vector range) and reed810 is
     * -1, so every mainnet header is version 0.
     */
    private final ActivationConfig mainnet = ActivationConfig.read(
            ConfigFactory.load("config/main").getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG));

    static final class Vector {
        final long number;
        final int txCount;
        final int uncleCount;
        final byte[] unclesHash;
        final byte[] remascTxHash;
        final byte[] txTrieRoot;
        final byte[] remascTxRlp;
        final boolean rskip126;

        Vector(String[] c) {
            this.number = Long.parseLong(c[0]);
            this.txCount = Integer.parseInt(c[1]);
            this.uncleCount = Integer.parseInt(c[2]);
            this.unclesHash = decode(c[3]);
            this.remascTxHash = decode(c[4]);
            this.txTrieRoot = decode(c[5]);
            this.remascTxRlp = decode(c[6]);
            this.rskip126 = "rskip126".equals(c[7]);
        }

        private static byte[] decode(String hex) {
            return Hex.decode(hex.startsWith("0x") ? hex.substring(2) : hex);
        }

        @Override
        public String toString() {
            return "block " + number + (rskip126 ? " (rskip126)" : " (orchid)")
                    + ", " + txCount + " tx, " + uncleCount + " uncles";
        }
    }

    static List<Vector> vectors() throws IOException {
        List<Vector> out = new ArrayList<>();
        try (InputStream in = DerivableBodyTest.class.getResourceAsStream(VECTORS);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // header row
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    out.add(new Vector(line.split(",")));
                }
            }
        }
        return out;
    }

    @Test
    void vectorsAreLoaded() throws IOException {
        List<Vector> all = vectors();
        assertEquals(32, all.size(), "expected the full mainnet vector set");
        assertTrue(all.stream().anyMatch(v -> !v.rskip126), "vectors must cover the pre-RSKIP126 encoding");
        assertTrue(all.stream().anyMatch(v -> v.rskip126), "vectors must cover the RSKIP126 encoding");
        assertTrue(all.stream().anyMatch(v -> v.uncleCount == 0), "vectors must cover derivable blocks");
        assertTrue(all.stream().anyMatch(v -> v.uncleCount > 0), "vectors must cover non-derivable blocks");
    }

    /**
     * The whole optimization rests on the REMASC transaction being a pure function of the block
     * number. If that ever stops being true these vectors, taken from the live chain, stop matching.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void remascTransactionIsDerivedFromTheBlockNumberAlone(Vector vector) {
        RemascTransaction derived = new RemascTransaction(vector.number);

        assertArrayEquals(vector.remascTxRlp, derived.getEncoded(),
                "derived REMASC RLP differs from the one on chain");
        assertArrayEquals(vector.remascTxHash, derived.getHash().getBytes(),
                "derived REMASC transaction hash differs from the one on chain");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void derivedTransactionsTrieRootMatchesTheChain(Vector vector) {
        List<Transaction> txs = Collections.singletonList(new RemascTransaction(vector.number));

        assertArrayEquals(vector.txTrieRoot, BlockHashesHelper.getTxTrieRoot(txs, vector.rskip126),
                "derived transactions trie root differs from the one on chain");
    }

    /**
     * The two trie encodings agree on the empty list but not on a REMASC-only list, so resolving
     * RSKIP126 per height is load bearing: using one flag for the whole chain would silently derive
     * a wrong root on one side of the fork.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void theTwoTrieEncodingsDisagreeOnARemascOnlyList(Vector vector) {
        List<Transaction> txs = Collections.singletonList(new RemascTransaction(vector.number));

        byte[] orchid = BlockHashesHelper.getTxTrieRoot(txs, false);
        byte[] unitrie = BlockHashesHelper.getTxTrieRoot(txs, true);

        assertNotEquals(ByteUtil.toHexString(orchid), ByteUtil.toHexString(unitrie));
        assertArrayEquals(vector.txTrieRoot, vector.rskip126 ? unitrie : orchid);
    }

    @Test
    void theEmptyUncleListHashIsTheDocumentedConstant() {
        byte[] fromValidationPath = HashUtil.keccak256(BlockHeader.getUnclesEncoded(Collections.emptyList()));

        assertArrayEquals(HashUtil.keccak256(RLP.encodeList()), fromValidationPath);
        assertEquals("1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
                ByteUtil.toHexString(fromValidationPath));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void aBodyIsDerivableExactlyWhenItHasNoUnclesAndOnlyRemasc(Vector vector) {
        BlockFactory factory = new BlockFactory(mainnet);
        BlockHeader header = headerFor(vector);

        boolean expected = vector.uncleCount == 0 && vector.txCount == 1;

        assertEquals(expected, factory.hasDerivableBody(header),
                "derivability disagrees with what the chain says this block holds");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void aDerivedBlockCarriesExactlyTheRemascTransactionAndNoUncles(Vector vector) {
        if (vector.uncleCount != 0 || vector.txCount != 1) {
            return; // covered by the negative assertions above
        }
        BlockFactory factory = new BlockFactory(mainnet);

        Block block = factory.newBlockWithDerivedBody(headerFor(vector));

        assertEquals(1, block.getTransactionsList().size());
        assertTrue(block.getUncleList().isEmpty());
        assertArrayEquals(vector.remascTxHash, block.getTransactionsList().get(0).getHash().getBytes());
        assertArrayEquals(vector.txTrieRoot, block.getTxTrieRoot());
    }

    /** A header that commits to any uncles cannot be derived, whatever its transactions look like. */
    @Test
    void aHeaderWithUnclesIsNeverDerivable() throws IOException {
        BlockFactory factory = new BlockFactory(mainnet);
        Vector withUncles = vectors().stream()
                .filter(v -> v.uncleCount > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no vector with uncles"));

        assertFalse(factory.hasDerivableBody(headerFor(withUncles)));
    }

    /** Guards the RSKIP126 branch: an Orchid-era root must not validate under the Unitrie encoding. */
    @Test
    void aRootFromTheWrongEncodingIsNotDerivable() throws IOException {
        BlockFactory factory = new BlockFactory(mainnet);
        Vector orchid = vectors().stream()
                .filter(v -> !v.rskip126 && v.uncleCount == 0 && v.txCount == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no pre-RSKIP126 derivable vector"));

        List<Transaction> txs = Collections.singletonList(new RemascTransaction(orchid.number));
        BlockHeader wrongEncoding = new BlockHeaderBuilder(mainnet)
                .setNumber(orchid.number)
                .setEmptyUnclesHash()
                .setTxTrieRoot(BlockHashesHelper.getTxTrieRoot(txs, true))
                .build();

        assertFalse(factory.hasDerivableBody(wrongEncoding),
                "a Unitrie root on a pre-RSKIP126 block must not be accepted");
    }

    private BlockHeader headerFor(Vector vector) {
        return new BlockHeaderBuilder(mainnet)
                .setNumber(vector.number)
                .setUnclesHash(vector.unclesHash)
                .setTxTrieRoot(vector.txTrieRoot)
                .build();
    }

    /**
     * Pins the two mainnet facts the derivation relies on. If either moves, the vectors above stop
     * being the right expectation and this fails first, with a clearer reason.
     */
    @Test
    void mainnetSwitchesTrieEncodingAtWasabiAndKeepsVersionZeroHeaders() {
        assertFalse(mainnet.isActive(ConsensusRule.RSKIP126, 1_590_999L));
        assertTrue(mainnet.isActive(ConsensusRule.RSKIP126, 1_591_000L));

        assertEquals(0, mainnet.getHeaderVersion(0L));
        assertEquals(0, mainnet.getHeaderVersion(9_190_721L));
    }
}
