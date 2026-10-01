/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A legacy gasPrice of zero can be encoded as the RLP empty string ({@code 0x80}) or as a single zero byte
 * ({@code 0x00}), and both appear in existing chain data. The hash, the sender and the block's transactions trie
 * root are computed over the re-encoding, so each form must re-encode to exactly the bytes that were received.
 */
class LegacyZeroGasPriceEncodingTest {

    private static final ECKey COW = ECKey.fromPrivate(HashUtil.keccak256("cow".getBytes()));
    private static final RskAddress COW_ADDRESS = new RskAddress(COW.getAddress());

    /** Legacy (EIP-155, chainId 33) tx from cow with gasPrice 0 encoded as 0x80, signed by ethers v6. */
    private static final String LEGACY_GAS_PRICE_EMPTY =
            "f85f808082520894000000000000000000000000000000000000dead018066a07748baa85982194bc67117f93dee9a70837708dc56b6ee48201c540de6a06e2ea01809a205c90ad3606d13fd670018bf1ce2fe0d0cfd8ae386f2c7050f567865ae";

    /**
     * Regtest block 2 as mined and stored by a node built from master: a user tx with gasPrice 0x80 followed by the
     * REMASC tx, whose gasPrice is 0x00.
     */
    private static final String MASTER_MINED_BLOCK_2 =
            "f90355f902d0a08459f455949d381dab200d1f682a2fe509372cd42bbb37e03f5e630120f83dfea01dcc4de8dec75d7aab85"
            + "b567b6ccd41ad312451b948a7413f0a142fd40d4934794ec4ddeb4380ad69b3e509baad9f158cdf4e4681da090172d205b64"
            + "142f0136a6cd971214feef1ead5ffc34995c682c740c3a0efd48a0398f4e12c0ab1571e4e4c01a26842d91ab17d678fb49bc"
            + "1531d360780a2f89bea0eee108fb307460673dc10fdf0a60ed5b75e8711d20d52fa597926e1dd46f9fc4b901000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0001028400984a3f825208846ab45fc095d40192534e415053484f542d656365663535646466800080800282010080b85071"
            + "1101000000000000000000000000000000000000000000000000000000000000000000616242c4d763287d763503bf750fc4"
            + "c0101bfb8ac5c1b3a8011a188dfde1cb6cbf5fb46affff7f210600000080b89000000000000002c0ce93de585ea16bae0ee1"
            + "ad8ec10241dcc728d5339e60fceaac8831ec3635f6d29eddcf6d5cad21bb25cdfa2f9e2b0f260c47b945f3b9968ec6a75f90"
            + "852a8a25b9037bb8adc4d0082d6f92e6b290c091f5cdd635b5c19f7c260e5652534b424c4f434b3a894ffa48616f2a7726e2"
            + "854cf7c83f79edd118a8879b402d2b3e4a0b7eacbf7000000000f87ff85f0180825208940000000000000000000000000000"
            + "00000000dead018066a0b2e26e5a9db80b9d28a8cdf0c1c26645962232449e5c64fa09c1edc3bdfae594a00abc9df6111940"
            + "e293b421af8531a46ea45e47f8ae33463774f11a505048708bdd010000940000000000000000000000000000000001000008"
            + "8080808080c0";
    private static final String MASTER_MINED_BLOCK_2_HASH = "2ce822275d6d4b9f5cc3db507f40da21b63bc5ad9e78a8b5d0c9d5d59d3ca465";

    @Test
    void gasPriceZeroAsEmptyStringRoundTrips() {
        assertRoundTrip(Hex.decode(LEGACY_GAS_PRICE_EMPTY));
    }

    @Test
    void gasPriceZeroAsSingleZeroByteRoundTrips() {
        assertRoundTrip(signLegacy(new byte[]{0}));
    }

    @Test
    void zeroGasPriceFormsStayDistinctButPriceTheSame() {
        Transaction empty = new ImmutableTransaction(Hex.decode(LEGACY_GAS_PRICE_EMPTY));
        Transaction zeroByte = new ImmutableTransaction(signLegacy(new byte[]{0}));

        assertEquals(Coin.ZERO, empty.getGasPrice());
        assertEquals(Coin.ZERO, zeroByte.getGasPrice());
        assertEquals(0, gasPricePayload(empty.getEncoded()).length);
        assertArrayEquals(new byte[]{0}, gasPricePayload(zeroByte.getEncoded()));
    }

    @Test
    void remascTransactionKeepsItsSingleZeroByteGasPrice() {
        byte[] encoded = new RemascTransaction(10).getEncoded();

        assertArrayEquals(new byte[]{0}, gasPricePayload(encoded));
        assertArrayEquals(encoded, new RemascTransaction(encoded).getEncoded());
    }

    @Test
    void txTrieRootOfZeroGasPriceTxMatchesTheReceivedBytes() {
        byte[] raw = Hex.decode(LEGACY_GAS_PRICE_EMPTY);
        byte[] expectedRoot = new Trie().put(RLP.encodeInt(0), raw).getHash().getBytes();

        byte[] actualRoot = BlockHashesHelper.getTxTrieRoot(List.of(new ImmutableTransaction(raw)), true);

        assertArrayEquals(expectedRoot, actualRoot);
    }

    @Test
    void blockWithBothZeroGasPriceFormsDecodes() {
        byte[] rawBlock = Hex.decode(MASTER_MINED_BLOCK_2);
        RLPList rawTxs = (RLPList) RLP.decodeList(rawBlock).get(1);

        Block block = new BlockFactory(ActivationConfigsForTest.regtest()).decodeBlock(rawBlock);

        assertEquals(MASTER_MINED_BLOCK_2_HASH, block.getHash().toHexString());
        assertEquals(2, block.getTransactionsList().size());
        for (int i = 0; i < 2; i++) {
            byte[] rawTx = rawTxs.get(i).getRLPData();
            Transaction tx = block.getTransactionsList().get(i);
            assertArrayEquals(rawTx, tx.getEncoded());
            assertArrayEquals(HashUtil.keccak256(rawTx), tx.getHash().getBytes());
        }
        assertEquals(0, gasPricePayload(block.getTransactionsList().get(0).getEncoded()).length);
        assertInstanceOf(RemascTransaction.class, block.getTransactionsList().get(1));
        assertArrayEquals(new byte[]{0}, gasPricePayload(block.getTransactionsList().get(1).getEncoded()));
        assertEquals(COW_ADDRESS, block.getTransactionsList().get(0).getSender(new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
    }

    private static void assertRoundTrip(byte[] raw) {
        Transaction tx = new ImmutableTransaction(raw);

        assertArrayEquals(raw, tx.getEncoded());
        assertArrayEquals(HashUtil.keccak256(raw), tx.getHash().getBytes());
        assertEquals(COW_ADDRESS, tx.getSender(new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
    }

    /** Signs a legacy EIP-155 (chainId 33) tx from cow whose gasPrice item is the given bytes. */
    private static byte[] signLegacy(byte[] gasPrice) {
        byte[][] fields = {
                RLP.encodeElement(null),
                RLP.encodeElement(gasPrice),
                RLP.encodeElement(BigInteger.valueOf(21000).toByteArray()),
                RLP.encodeElement(Hex.decode("000000000000000000000000000000000000dead")),
                RLP.encodeElement(new byte[]{1}),
                RLP.encodeElement(null),
        };
        byte[] sigHash = HashUtil.keccak256(RLP.encodeList(
                fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                RLP.encodeByte((byte) 33), RLP.encodeElement(null), RLP.encodeElement(null)));
        ECDSASignature sig = ECDSASignature.fromSignature(COW.sign(sigHash));
        return RLP.encodeList(
                fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                RLP.encodeInt(sig.getV() - 27 + 33 * 2 + 35),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(sig.getR())),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(sig.getS())));
    }

    /** The gasPrice item's payload: empty for 0x80, {0} for 0x00. */
    private static byte[] gasPricePayload(byte[] legacyEncoded) {
        return RLP.decodeList(legacyEncoded).get(1).getRLPRawData();
    }
}
