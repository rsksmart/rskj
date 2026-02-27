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
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSL Tests for RSKIP543
 *
 * <p>Tests the full RSKIP543 specification:
 * <ul>
 *   <li>Legacy transactions (Type 0x00) — no type prefix, first byte >= 0xc0</li>
 *   <li>Standard EIP-2718 typed transactions (Type 0x01) — single byte prefix</li>
 *   <li>RSK namespace transactions (0x02 || rsk-tx-type || payload)
 *       with rsk-tx-type in [0x00, 0x7f]</li>
 *   <li>Transaction encoding includes correct type prefix(es)</li>
 *   <li>Transaction receipts include correct type encoding</li>
 *   <li>Mixed transaction types coexist in the same block</li>
 *   <li>Contract deployment and interaction via RSK namespace</li>
 *   <li>Backward compatibility with legacy transactions</li>
 * </ul>

 */
class Rskip543DslTest {

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip543",
                        ConfigValueFactory.fromAnyRef(0))
        );

        DslParser parser = DslParser.fromResource("dsl/transaction/rskip543/rskip543Test.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    void legacyTransactionShouldBeTypeLegacy() {
        Transaction tx = world.getTransactionByName("txLegacy");

        assertNotNull(tx);
        assertEquals(TransactionType.LEGACY, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
    }

    @Test
    void legacyTransactionEncodingShouldStartWithRlpListMarker() {
        Transaction tx = world.getTransactionByName("txLegacy");
        byte[] encoded = tx.getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xc0,
                "Legacy transaction encoding should start with RLP list marker (>= 0xc0), got: 0x"
                        + String.format("%02x", encoded[0] & 0xFF));
    }

    @Test
    void legacyTransactionReceiptShouldSucceed() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txLegacy");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void legacyTransactionFullTypeStringShouldBe0x00() {
        Transaction tx = world.getTransactionByName("txLegacy");

        assertEquals("0x00", tx.getFullTypeString());
    }

    @Test
    void type1TransactionShouldBeType1() {
        Transaction tx = world.getTransactionByName("txType1");

        assertNotNull(tx);
        assertEquals(TransactionType.TYPE_1, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
    }

    @Test
    void type1TransactionEncodingShouldStartWith0x01() {
        Transaction tx = world.getTransactionByName("txType1");
        byte[] encoded = tx.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Type 1 transaction encoding should start with 0x01");
        assertTrue((encoded[1] & 0xFF) >= 0xc0,
                "Payload should start with RLP list marker");
    }

    @Test
    void type1TransactionReceiptShouldSucceed() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void type1TransactionFullTypeStringShouldBe0x01() {
        Transaction tx = world.getTransactionByName("txType1");

        assertEquals("0x01", tx.getFullTypeString());
    }

    @Test
    void rskNamespaceTransactionShouldBeType2WithSubtype() {
        Transaction tx = world.getTransactionByName("txRskType3");

        assertNotNull(tx);
        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x03, tx.getRskSubtype());
    }

    @Test
    void rskNamespaceTransactionEncodingShouldHaveTwoBytePrefix() {
        Transaction tx = world.getTransactionByName("txRskType3");
        byte[] encoded = tx.getEncoded();

        assertEquals(0x02, encoded[0], "First byte should be 0x02 (namespace separator)");
        assertEquals(0x03, encoded[1], "Second byte should be rsk-tx-type (0x03)");
        assertTrue((encoded[2] & 0xFF) >= 0xc0,
                "Payload (after two-byte prefix) should start with RLP list marker");
    }

    @Test
    void rskNamespaceTransactionFullTypeStringShouldBeCombined() {
        Transaction tx = world.getTransactionByName("txRskType3");

        assertEquals("0x0203", tx.getFullTypeString());
    }

    @Test
    void rskNamespaceReceiptShouldSucceedAndHaveTwoBytePrefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType3");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());

        byte[] encodedReceipt = receipt.getEncoded();
        assertEquals(0x02, encodedReceipt[0], "Receipt should start with 0x02");
        assertEquals(0x03, encodedReceipt[1], "Receipt should have subtype 0x03 as second byte");
    }

    @Test
    void rskSubtype0x00ShouldWork() {
        Transaction tx = world.getTransactionByName("txRskType0");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x00, tx.getRskSubtype());
        assertEquals("0x0200", tx.getFullTypeString());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType0");
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void rskSubtype0x05ShouldWork() {
        Transaction tx = world.getTransactionByName("txRskType5");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x05, tx.getRskSubtype());
        assertEquals("0x0205", tx.getFullTypeString());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType5");
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void rskSubtype0x7fShouldWork() {
        Transaction tx = world.getTransactionByName("txRskType127");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x7f, tx.getRskSubtype());
        assertEquals("0x027f", tx.getFullTypeString());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType127");
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void multipleRskSubtypesInSameBlockShouldAllSucceed() {
        Block b04 = world.getBlockByName("b04");

        assertNotNull(b04);
        assertEquals(3, b04.getTransactionsList().size(),
                "Block b04 should contain 3 RSK namespace transactions with different subtypes");

        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txRskType0").getStatus());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txRskType5").getStatus());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txRskType127").getStatus());
    }

    @Test
    void contractDeploymentWithRskNamespaceShouldSucceed() {
        Transaction tx = world.getTransactionByName("txDeployRsk");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x0a, tx.getRskSubtype());
        assertTrue(tx.isContractCreation());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txDeployRsk");
        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void contractDeploymentEncodingShouldHaveTwoBytePrefix() {
        Transaction tx = world.getTransactionByName("txDeployRsk");
        byte[] encoded = tx.getEncoded();

        assertEquals(0x02, encoded[0]);
        assertEquals(0x0a, encoded[1]);
        assertEquals("0x020a", tx.getFullTypeString());
    }

    @Test
    void contractInteractionWithRskNamespaceShouldSucceed() {
        Transaction tx = world.getTransactionByName("txCallSetValue");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x0f, tx.getRskSubtype());
        assertEquals("0x020f", tx.getFullTypeString());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txCallSetValue");
        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void mixedTransactionTypesShouldCoexistInSameBlock() {
        Block b07 = world.getBlockByName("b07");

        assertNotNull(b07);
        assertEquals(3, b07.getTransactionsList().size());
    }

    @Test
    void mixedBlockLegacyTransactionShouldWork() {
        Transaction tx = world.getTransactionByName("txMixed1");

        assertEquals(TransactionType.LEGACY, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txMixed1").getStatus());
    }

    @Test
    void mixedBlockType1TransactionShouldWork() {
        Transaction tx = world.getTransactionByName("txMixed2");

        assertEquals(TransactionType.TYPE_1, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txMixed2").getStatus());
    }

    @Test
    void mixedBlockRskNamespaceTransactionShouldWork() {
        Transaction tx = world.getTransactionByName("txMixed3");

        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x11, tx.getRskSubtype());
        assertEquals("0x0211", tx.getFullTypeString());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txMixed3").getStatus());
    }

    @Test
    void rskNamespaceValueTransferToDifferentRecipientShouldWork() {
        Transaction tx = world.getTransactionByName("txRskToAcc3");

        assertNotNull(tx);
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals((byte) 0x01, tx.getRskSubtype());
        assertEquals("0x0201", tx.getFullTypeString());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskToAcc3");
        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void rskNamespaceTransactionShouldSurviveRoundTrip() {
        Transaction original = world.getTransactionByName("txRskType3");
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_2, decoded.getType());
        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals((byte) 0x03, decoded.getRskSubtype());
        assertEquals(original.getNonceAsInteger(), decoded.getNonceAsInteger());
        assertEquals(original.getGasPrice(), decoded.getGasPrice());
        assertEquals(original.getGasLimitAsInteger(), decoded.getGasLimitAsInteger());
    }

    @Test
    void legacyTransactionShouldSurviveRoundTrip() {
        Transaction original = world.getTransactionByName("txLegacy");
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.LEGACY, decoded.getType());
        assertFalse(decoded.isRskNamespaceTransaction());
        assertEquals(original.getNonceAsInteger(), decoded.getNonceAsInteger());
    }

    @Test
    void type1TransactionShouldSurviveRoundTrip() {
        Transaction original = world.getTransactionByName("txType1");
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_1, decoded.getType());
        assertFalse(decoded.isRskNamespaceTransaction());
        assertEquals(original.getNonceAsInteger(), decoded.getNonceAsInteger());
    }

    @Test
    void rskNamespaceTransactionSignatureShouldCoverTypePrefix() {
        Transaction tx = world.getTransactionByName("txRskType3");
        byte[] rawEncoded = tx.getEncodedRaw();

        assertEquals(0x02, rawEncoded[0],
                "Raw encoding should start with 0x02");
        assertEquals(0x03, rawEncoded[1],
                "Raw encoding should have subtype 0x03 as second byte");
    }

    @Test
    void type1TransactionSignatureShouldCoverTypePrefix() {
        Transaction tx = world.getTransactionByName("txType1");
        byte[] rawEncoded = tx.getEncodedRaw();

        assertEquals((byte) 0x01, rawEncoded[0],
                "Raw encoding for Type 1 transaction should start with 0x01");
    }

    @Test
    void legacyTransactionRawEncodingShouldNotHaveTypePrefix() {
        Transaction tx = world.getTransactionByName("txLegacy");
        byte[] rawEncoded = tx.getEncodedRaw();

        assertTrue((rawEncoded[0] & 0xFF) >= 0xc0,
                "Legacy raw encoding should start with RLP list marker, not a type prefix");
    }

    @Test
    void blockchainShouldReachExpectedHeight() {
        assertEquals(8, world.getBlockChain().getBestBlock().getNumber(),
                "Block chain should have 8 blocks after all tests");
    }

    @Test
    void bestBlockShouldBeB08() {
        Block b08 = world.getBlockByName("b08");
        assertEquals(b08, world.getBlockChain().getBestBlock());
    }

    @Test
    void allBlocksShouldBeConnected() {
        for (int i = 1; i <= 8; i++) {
            String blockName = "b0" + i;
            Block block = world.getBlockByName(blockName);
            assertNotNull(block, "Block " + blockName + " should exist");
            assertEquals(i, block.getNumber(), blockName + " should be at height " + i);
        }
    }

    @Test
    void blockWithType1TransactionShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b02");
        assertNotNull(original);

        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(original.getTransactionsList().size(), decoded.getTransactionsList().size(),
                "Decoded block should have same number of transactions");

        Transaction originalTx = original.getTransactionsList().get(0);
        Transaction decodedTx = decoded.getTransactionsList().get(0);

        assertEquals(TransactionType.TYPE_1, decodedTx.getType(),
                "Decoded transaction should preserve Type 1");
        assertFalse(decodedTx.isRskNamespaceTransaction());
        assertEquals(originalTx.getHash(), decodedTx.getHash(),
                "Transaction hash should survive block encode/decode");
    }

    @Test
    void blockWithRskNamespaceTransactionShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b03");
        assertNotNull(original);

        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(original.getTransactionsList().size(), decoded.getTransactionsList().size(),
                "Decoded block should have same number of transactions");

        Transaction decodedTx = decoded.getTransactionsList().get(0);

        assertEquals(TransactionType.TYPE_2, decodedTx.getType(),
                "Decoded transaction should preserve TYPE_2 (RSK namespace prefix)");
        assertTrue(decodedTx.isRskNamespaceTransaction(),
                "Decoded transaction should be recognized as RSK namespace");
        assertEquals((byte) 0x03, decodedTx.getRskSubtype(),
                "Decoded transaction should preserve RSK subtype 0x03");
    }

    @Test
    void blockWithMultipleRskSubtypesShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b04");
        assertNotNull(original);

        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(3, decoded.getTransactionsList().size(),
                "Decoded block should have 3 transactions");

        for (Transaction decodedTx : decoded.getTransactionsList()) {
            assertEquals(TransactionType.TYPE_2, decodedTx.getType());
            assertTrue(decodedTx.isRskNamespaceTransaction());
        }

        assertEquals((byte) 0x00, decoded.getTransactionsList().get(0).getRskSubtype());
        assertEquals((byte) 0x05, decoded.getTransactionsList().get(1).getRskSubtype());
        assertEquals((byte) 0x7f, decoded.getTransactionsList().get(2).getRskSubtype());
    }

    @Test
    void blockWithMixedTransactionTypesShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b07");
        assertNotNull(original);

        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(3, decoded.getTransactionsList().size(),
                "Decoded block should have 3 transactions");

        // Transaction 0: Legacy
        Transaction decodedLegacy = decoded.getTransactionsList().get(0);
        assertEquals(TransactionType.LEGACY, decodedLegacy.getType(),
                "First tx should be LEGACY after decode");
        assertFalse(decodedLegacy.isRskNamespaceTransaction());

        // Transaction 1: Type 1
        Transaction decodedType1 = decoded.getTransactionsList().get(1);
        assertEquals(TransactionType.TYPE_1, decodedType1.getType(),
                "Second tx should be TYPE_1 after decode");
        assertFalse(decodedType1.isRskNamespaceTransaction());

        // Transaction 2: RSK namespace (subtype 0x11)
        Transaction decodedRsk = decoded.getTransactionsList().get(2);
        assertEquals(TransactionType.TYPE_2, decodedRsk.getType(),
                "Third tx should be TYPE_2 (RSK namespace) after decode");
        assertTrue(decodedRsk.isRskNamespaceTransaction());
        assertEquals((byte) 0x11, decodedRsk.getRskSubtype(),
                "Third tx should preserve RSK subtype 0x11");
    }

    @Test
    void blockEncodedTransactionHashesShouldMatchOriginals() {
        Block original = world.getBlockByName("b07");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        List<Transaction> origTxs = original.getTransactionsList();
        List<Transaction> decodedTxs = decoded.getTransactionsList();

        for (int i = 0; i < origTxs.size(); i++) {
            assertEquals(origTxs.get(i).getHash(), decodedTxs.get(i).getHash(),
                    "Transaction " + i + " hash should match after block encode/decode");
        }
    }

    @Test
    void blockWithContractDeploymentShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b05");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(1, decoded.getTransactionsList().size());

        Transaction decodedTx = decoded.getTransactionsList().get(0);
        assertEquals(TransactionType.TYPE_2, decodedTx.getType());
        assertTrue(decodedTx.isRskNamespaceTransaction());
        assertEquals((byte) 0x0a, decodedTx.getRskSubtype());
        assertTrue(decodedTx.isContractCreation(),
                "Contract creation flag should survive block encode/decode");
    }

    @Test
    void legacyReceiptEncodingShouldHaveNoTypePrefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txLegacy");
        byte[] encoded = receipt.getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xc0,
                "Legacy receipt encoding should start with RLP list marker (>= 0xc0), got: 0x"
                        + String.format("%02x", encoded[0] & 0xFF));
    }

    @Test
    void type1ReceiptEncodingShouldStartWith0x01() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Type 1 receipt encoding should start with 0x01");
        assertTrue((encoded[1] & 0xFF) >= 0xc0,
                "Type 1 receipt RLP payload should start with list marker");
    }

    @Test
    void rskNamespaceReceiptEncodingShouldHaveTwoBytePrefixForSubtype0x00() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType0");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "RSK namespace receipt should start with 0x02");
        assertEquals((byte) 0x00, encoded[1],
                "RSK namespace receipt subtype 0x00 should have second byte 0x00");
        assertTrue((encoded[2] & 0xFF) >= 0xc0,
                "Receipt RLP payload should start with list marker");
    }

    @Test
    void rskNamespaceReceiptEncodingShouldHaveTwoBytePrefixForSubtype0x05() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType5");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "RSK namespace receipt should start with 0x02");
        assertEquals((byte) 0x05, encoded[1],
                "RSK namespace receipt subtype 0x05 should have second byte 0x05");
        assertTrue((encoded[2] & 0xFF) >= 0xc0,
                "Receipt RLP payload should start with list marker");
    }

    @Test
    void rskNamespaceReceiptEncodingShouldHaveTwoBytePrefixForSubtype0x7f() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType127");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "RSK namespace receipt should start with 0x02");
        assertEquals((byte) 0x7f, encoded[1],
                "RSK namespace receipt subtype 0x7f should have second byte 0x7f");
        assertTrue((encoded[2] & 0xFF) >= 0xc0,
                "Receipt RLP payload should start with list marker");
    }

    @Test
    void contractDeploymentReceiptEncodingShouldHaveTwoBytePrefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txDeployRsk");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Contract deployment receipt should start with 0x02");
        assertEquals((byte) 0x0a, encoded[1],
                "Contract deployment receipt should have subtype 0x0a as second byte");
    }

    @Test
    void contractInteractionReceiptEncodingShouldHaveTwoBytePrefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCallSetValue");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Contract interaction receipt should start with 0x02");
        assertEquals((byte) 0x0f, encoded[1],
                "Contract interaction receipt should have subtype 0x0f as second byte");
    }

    @Test
    void mixedBlockReceiptEncodingsShouldMatchTransactionTypes() {
        // Legacy receipt — no prefix
        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txMixed1");
        assertTrue((legacyReceipt.getEncoded()[0] & 0xFF) >= 0xc0,
                "Mixed block legacy receipt should start with RLP list marker");

        // Type 1 receipt — 0x01 prefix
        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txMixed2");
        assertEquals((byte) 0x01, type1Receipt.getEncoded()[0],
                "Mixed block Type 1 receipt should start with 0x01");

        // RSK namespace receipt — 0x02 || 0x11 prefix
        TransactionReceipt rskReceipt = world.getTransactionReceiptByName("txMixed3");
        assertEquals((byte) 0x02, rskReceipt.getEncoded()[0],
                "Mixed block RSK namespace receipt should start with 0x02");
        assertEquals((byte) 0x11, rskReceipt.getEncoded()[1],
                "Mixed block RSK namespace receipt should have subtype 0x11");
    }

    @Test
    void legacyReceiptShouldSurviveRoundTrip() {
        TransactionReceipt original = world.getTransactionReceiptByName("txLegacy");
        byte[] encoded = original.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getGasUsed(), decoded.getGasUsed());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
    }

    @Test
    void type1ReceiptShouldSurviveRoundTrip() {
        TransactionReceipt original = world.getTransactionReceiptByName("txType1");
        byte[] encoded = original.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getGasUsed(), decoded.getGasUsed());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
    }

    @Test
    void rskNamespaceReceiptShouldSurviveRoundTrip() {
        TransactionReceipt original = world.getTransactionReceiptByName("txRskType3");
        byte[] encoded = original.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getGasUsed(), decoded.getGasUsed());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
    }

    @Test
    void rskNamespaceBoundarySubtypeReceiptsShouldSurviveRoundTrip() {
        // Subtype 0x00
        TransactionReceipt r0 = world.getTransactionReceiptByName("txRskType0");
        TransactionReceipt d0 = new TransactionReceipt(r0.getEncoded());
        assertArrayEquals(r0.getStatus(), d0.getStatus());

        // Subtype 0x7f
        TransactionReceipt r127 = world.getTransactionReceiptByName("txRskType127");
        TransactionReceipt d127 = new TransactionReceipt(r127.getEncoded());
        assertArrayEquals(r127.getStatus(), d127.getStatus());
    }

    @Test
    void receiptDTOShouldReturnCorrectTypeForLegacy() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txLegacy");
        Block block = world.getBlockByName("b01");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x0", dto.getType(),
                "Legacy receipt DTO type should be 0x0");
    }

    @Test
    void receiptDTOShouldReturnCorrectTypeForType1() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1");
        Block block = world.getBlockByName("b02");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x1", dto.getType(),
                "Type 1 receipt DTO type should be 0x1");
    }

    @Test
    void receiptDTOShouldReturnCombinedTypeForRskNamespace() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType3");
        Block block = world.getBlockByName("b03");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x0203", dto.getType(),
                "RSK namespace receipt DTO (subtype 0x03) type should be 0x0203");
    }

    @Test
    void receiptDTOShouldReturnCombinedTypeForRskSubtype0x00() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType0");
        Block block = world.getBlockByName("b04");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x0200", dto.getType(),
                "RSK namespace receipt DTO (subtype 0x00) type should be 0x0200");
    }

    @Test
    void receiptDTOShouldReturnCombinedTypeForRskSubtype0x7f() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType127");
        Block block = world.getBlockByName("b04");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 2);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x027f", dto.getType(),
                "RSK namespace receipt DTO (subtype 0x7f) type should be 0x027f");
    }

    @Test
    void mixedBlockReceiptDTOTypesShouldMatchTransactionDTOTypes() {
        Block block = world.getBlockByName("b07");

        // Legacy
        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txMixed1");
        TransactionInfo legacyInfo = new TransactionInfo(legacyReceipt, block.getHash().getBytes(), 0);
        TransactionReceiptDTO legacyDTO = new TransactionReceiptDTO(block, legacyInfo, world.getBlockTxSignatureCache());

        // Type 1
        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txMixed2");
        TransactionInfo type1Info = new TransactionInfo(type1Receipt, block.getHash().getBytes(), 1);
        TransactionReceiptDTO type1DTO = new TransactionReceiptDTO(block, type1Info, world.getBlockTxSignatureCache());

        // RSK namespace
        TransactionReceipt rskReceipt = world.getTransactionReceiptByName("txMixed3");
        TransactionInfo rskInfo = new TransactionInfo(rskReceipt, block.getHash().getBytes(), 2);
        TransactionReceiptDTO rskDTO = new TransactionReceiptDTO(block, rskInfo, world.getBlockTxSignatureCache());

        assertEquals("0x0", legacyDTO.getType(), "Mixed block legacy receipt DTO type");
        assertEquals("0x1", type1DTO.getType(), "Mixed block Type 1 receipt DTO type");
        assertEquals("0x0211", rskDTO.getType(), "Mixed block RSK namespace receipt DTO type");

        Transaction legacyTx = world.getTransactionByName("txMixed1");
        Transaction type1Tx = world.getTransactionByName("txMixed2");
        Transaction rskTx = world.getTransactionByName("txMixed3");

        assertEquals(legacyDTO.getType(),
                new TransactionResultDTO(block, 0, legacyTx, false, world.getBlockTxSignatureCache()).getType(),
                "Legacy receipt DTO type should match transaction result DTO type");
        assertEquals(type1DTO.getType(),
                new TransactionResultDTO(block, 1, type1Tx, false, world.getBlockTxSignatureCache()).getType(),
                "Type 1 receipt DTO type should match transaction result DTO type");
        assertEquals(rskDTO.getType(),
                new TransactionResultDTO(block, 2, rskTx, false, world.getBlockTxSignatureCache()).getType(),
                "RSK namespace receipt DTO type should match transaction result DTO type");
    }

    @Test
    void contractDeploymentReceiptDTOShouldHaveCorrectType() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txDeployRsk");
        Block block = world.getBlockByName("b05");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x020a", dto.getType(),
                "Contract deployment receipt DTO type should be 0x020a");
        assertNotNull(dto.getContractAddress(),
                "Contract deployment receipt should have a contract address");
    }

    @Test
    void transactionResultDTOShouldReturnCorrectTypeForLegacy() {
        Transaction tx = world.getTransactionByName("txLegacy");
        Block block = world.getBlockByName("b01");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0", dto.getType(),
                "Legacy transaction DTO type should be 0x0");
    }

    @Test
    void transactionResultDTOShouldReturnCorrectTypeForType1() {
        Transaction tx = world.getTransactionByName("txType1");
        Block block = world.getBlockByName("b02");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x1", dto.getType(),
                "Type 1 transaction DTO type should be 0x1");
    }

    @Test
    void transactionResultDTOShouldReturnCombinedTypeForRskNamespace() {
        Transaction tx = world.getTransactionByName("txRskType3");
        Block block = world.getBlockByName("b03");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0203", dto.getType(),
                "RSK namespace transaction (subtype 0x03) DTO type should be 0x0203, not 0x2");
    }

    @Test
    void transactionResultDTOShouldReturnCombinedTypeForRskSubtype0x00() {
        Transaction tx = world.getTransactionByName("txRskType0");
        Block block = world.getBlockByName("b04");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0200", dto.getType(),
                "RSK namespace transaction (subtype 0x00) DTO type should be 0x0200");
    }

    @Test
    void transactionResultDTOShouldReturnCombinedTypeForRskSubtype0x7f() {
        Transaction tx = world.getTransactionByName("txRskType127");
        Block block = world.getBlockByName("b04");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 2, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x027f", dto.getType(),
                "RSK namespace transaction (subtype 0x7f) DTO type should be 0x027f");
    }

    @Test
    void transactionResultDTOTypeShouldBeConsistentWithReceiptDTO() {
        Transaction tx = world.getTransactionByName("txRskType3");
        Block block = world.getBlockByName("b03");

        TransactionResultDTO resultDTO = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txRskType3");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);
        TransactionReceiptDTO receiptDTO = new TransactionReceiptDTO(
                block, txInfo, world.getBlockTxSignatureCache());

        assertEquals(resultDTO.getType(), receiptDTO.getType(),
                "TransactionResultDTO and TransactionReceiptDTO should report the same type");
    }

    @Test
    void mixedBlockTransactionResultDTOTypesShouldBeCorrect() {
        Block block = world.getBlockByName("b07");

        Transaction legacyTx = world.getTransactionByName("txMixed1");
        Transaction type1Tx = world.getTransactionByName("txMixed2");
        Transaction rskTx = world.getTransactionByName("txMixed3");

        TransactionResultDTO legacyDTO = new TransactionResultDTO(
                block, 0, legacyTx, false, world.getBlockTxSignatureCache());
        TransactionResultDTO type1DTO = new TransactionResultDTO(
                block, 1, type1Tx, false, world.getBlockTxSignatureCache());
        TransactionResultDTO rskDTO = new TransactionResultDTO(
                block, 2, rskTx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0", legacyDTO.getType(), "Legacy tx in mixed block should have type 0x0");
        assertEquals("0x1", type1DTO.getType(), "Type 1 tx in mixed block should have type 0x1");
        assertEquals("0x0211", rskDTO.getType(),
                "RSK namespace tx (subtype 0x11) in mixed block should have type 0x0211");
    }

    @Test
    void explicitType0x00_shouldBeRejectedByTransactionBuilder() {
        Account acc = new AccountBuilder().name("type0test").build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new TransactionBuilder()
                        .sender(acc)
                        .receiver(acc)
                        .value(java.math.BigInteger.valueOf(1000))
                        .gasLimit(java.math.BigInteger.valueOf(21000))
                        .transactionType((byte) 0x00)
                        .build());

        assertTrue(ex.getMessage().contains("Explicit type 0x00 is not allowed"),
                "Error should indicate 0x00 rejection, got: " + ex.getMessage());
    }

    @Test
    void explicitType0x00_shouldBeRejectedByDslProcessor() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip543",
                        ConfigValueFactory.fromAnyRef(0))
        );

        String dsl = "account_new acc1 100000000000000000\n"
                + "transaction_build txType0\n"
                + "    sender acc1\n"
                + "    receiver acc1\n"
                + "    value 1000\n"
                + "    gas 21000\n"
                + "    transactionType 00\n"
                + "    build\n";

        World testWorld = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(testWorld);
        DslParser parser = new DslParser(dsl);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.processCommands(parser),
                "DSL should reject transaction_build with explicit transactionType 00");
        assertTrue(ex.getMessage().contains("Explicit type 0x00 is not allowed"),
                "Error should mention 0x00 is not allowed, got: " + ex.getMessage());
    }

    @Test
    void unknownTypeGreaterThan0x04_shouldBeRejectedByTransactionBuilder() {
        Account acc = new AccountBuilder().name("type5test").build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new TransactionBuilder()
                        .sender(acc)
                        .receiver(acc)
                        .value(java.math.BigInteger.valueOf(1000))
                        .gasLimit(java.math.BigInteger.valueOf(21000))
                        .transactionType((byte) 0x05)
                        .build());

        assertTrue(ex.getMessage().contains("Unknown transaction type"),
                "Error should indicate unknown type, got: " + ex.getMessage());
    }
}
