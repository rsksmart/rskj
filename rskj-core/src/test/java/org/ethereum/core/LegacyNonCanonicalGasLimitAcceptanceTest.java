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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.google.common.primitives.Bytes;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy transactions have never required minimal scalar encodings, so a node must accept one whose gas limit has a
 * leading zero byte (0x83005208). Rejecting it without an activation height would split the chain, so this test pins
 * that a block carrying it is decoded the way a peer receives it, validated and executed, and that the tx hash is the
 * hash of the bytes received.
 */
class LegacyNonCanonicalGasLimitAcceptanceTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final byte[] GAS_LIMIT_WITH_LEADING_ZERO = {0x00, 0x52, 0x08};
    private static final RskAddress RECEIVER = new RskAddress("0x00000000000000000000000000000000000000aa");

    @ParameterizedTest(name = "accountAbstractionActive={0}")
    @ValueSource(booleans = {false, true})
    void legacyTxWithLeadingZeroGasLimit_isAcceptedAndMinedWithTheHashOfItsRawBytes(boolean accountAbstractionActive) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.hardforkActivationHeights.cardamom1000",
                        ConfigValueFactory.fromAnyRef(accountAbstractionActive ? 0 : -1)));
        World world = new World(config);
        Account sender = new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000L)).build();

        byte[] raw = signedLegacyRaw(sender.getEcKey());
        assertEquals((byte) 0x83, raw[Bytes.indexOf(raw, GAS_LIMIT_WITH_LEADING_ZERO) - 1], "the gas limit is sent as 0x83005208");

        Transaction tx = new ImmutableTransaction(raw);
        assertArrayEquals(GAS_LIMIT_WITH_LEADING_ZERO, tx.getGasLimit());
        assertArrayEquals(raw, tx.getEncoded(), "the tx must re-encode to the bytes received");
        assertArrayEquals(HashUtil.keccak256(raw), tx.getHash().getBytes());
        assertEquals(sender.getAddress(), tx.getSender(new ReceivedTxSignatureCache()));
        tx.verify(new ReceivedTxSignatureCache());

        Block parent = world.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(Collections.singletonList(tx))
                .build(world.getConfig());
        assertEquals(1, block.getTransactionsList().size());

        Block received = new BlockFactory(config.getActivationConfig()).decodeBlock(block.getEncoded());
        assertArrayEquals(raw, received.getTransactionsList().get(0).getEncoded());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(received));

        TransactionReceipt receipt = world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore()).orElseThrow().getReceipt();
        assertTrue(receipt.isSuccessful());
        assertEquals(BigInteger.valueOf(21_000), new BigInteger(1, receipt.getGasUsed()));
        assertArrayEquals(raw, world.getBlockChain().getBestBlock().getTransactionsList().get(0).getEncoded());
    }

    private static byte[] signedLegacyRaw(ECKey key) {
        byte[][] fields = {
                RLP.encodeElement(null),                          // nonce 0
                RLP.encodeElement(new byte[]{0x01}),              // gasPrice 1
                RLP.encodeElement(GAS_LIMIT_WITH_LEADING_ZERO),   // 21000 with a leading zero
                RLP.encodeElement(RECEIVER.getBytes()),
                RLP.encodeElement(new byte[]{0x01}),              // value 1
                RLP.encodeElement(null),                          // no data
        };
        byte[] signingPayload = RLP.encodeList(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                RLP.encodeByte(CHAIN_ID), RLP.encodeElement(null), RLP.encodeElement(null));
        ECKey.ECDSASignature signature = key.sign(HashUtil.keccak256(signingPayload));
        byte v = (byte) (signature.v - Transaction.LOWER_REAL_V + Transaction.CHAIN_ID_INC + CHAIN_ID * 2);
        return RLP.encodeList(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                RLP.encodeByte(v),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r)),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s)));
    }
}
