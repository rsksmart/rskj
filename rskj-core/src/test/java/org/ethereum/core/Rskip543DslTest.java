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
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DSL Tests for RSKIP543
 *
 * <p>Tests the full RSKIP543 specification:
 * <ul>
 *   <li>Legacy transactions (Type 0x00) — no type prefix, first byte >= 0xc0</li>
 *   <li>Standard EIP-2718 typed transactions (Type 0x01) — single byte prefix</li>
 *   <li>Transaction encoding includes correct type prefix</li>
 *   <li>Transaction receipts include correct type encoding</li>
 *   <li>Mixed legacy + Type 1 transactions coexist in the same block</li>
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
        assertFalse(tx.getTypePrefix().isRskNamespace());
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
        assertFalse(tx.getTypePrefix().isRskNamespace());
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
    void mixedTransactionTypesShouldCoexistInSameBlock() {
        Block b03 = world.getBlockByName("b03");

        assertNotNull(b03);
        assertEquals(2, b03.getTransactionsList().size());
    }

    @Test
    void mixedBlockLegacyTransactionShouldWork() {
        Transaction tx = world.getTransactionByName("txMixed1");

        assertEquals(TransactionType.LEGACY, tx.getType());
        assertFalse(tx.getTypePrefix().isRskNamespace());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txMixed1").getStatus());
    }

    @Test
    void mixedBlockType1TransactionShouldWork() {
        Transaction tx = world.getTransactionByName("txMixed2");

        assertEquals(TransactionType.TYPE_1, tx.getType());
        assertFalse(tx.getTypePrefix().isRskNamespace());
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txMixed2").getStatus());
    }

    @Test
    void legacyTransactionShouldSurviveEncodeDecode() {
        Transaction original = world.getTransactionByName("txLegacy");
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.LEGACY, decoded.getType());
        assertFalse(decoded.getTypePrefix().isRskNamespace());
        assertEquals(original.getNonceAsInteger(), decoded.getNonceAsInteger());
    }

    @Test
    void type1TransactionShouldSurviveEncodeDecode() {
        Transaction original = world.getTransactionByName("txType1");
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_1, decoded.getType());
        assertFalse(decoded.getTypePrefix().isRskNamespace());
        assertEquals(original.getNonceAsInteger(), decoded.getNonceAsInteger());
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
        assertEquals(3, world.getBlockChain().getBestBlock().getNumber(),
                "Block chain should have 3 blocks after all tests");
    }

    @Test
    void bestBlockShouldBeB03() {
        Block b03 = world.getBlockByName("b03");
        assertEquals(b03, world.getBlockChain().getBestBlock());
    }

    @Test
    void allBlocksShouldBeConnected() {
        for (int i = 1; i <= 3; i++) {
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
        assertFalse(decodedTx.getTypePrefix().isRskNamespace());
        assertEquals(originalTx.getHash(), decodedTx.getHash(),
                "Transaction hash should survive block encode/decode");
    }

    @Test
    void blockWithMixedTransactionTypesShouldSurviveEncodeDecode() {
        Block original = world.getBlockByName("b03");
        assertNotNull(original);

        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(2, decoded.getTransactionsList().size(),
                "Decoded block should have 2 transactions");

        Transaction decodedLegacy = decoded.getTransactionsList().get(0);
        assertEquals(TransactionType.LEGACY, decodedLegacy.getType(),
                "First tx should be LEGACY after decode");
        assertFalse(decodedLegacy.getTypePrefix().isRskNamespace());

        Transaction decodedType1 = decoded.getTransactionsList().get(1);
        assertEquals(TransactionType.TYPE_1, decodedType1.getType(),
                "Second tx should be TYPE_1 after decode");
        assertFalse(decodedType1.getTypePrefix().isRskNamespace());
    }

    @Test
    void blockEncodedTransactionHashesShouldMatchOriginals() {
        Block original = world.getBlockByName("b03");
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
    void mixedBlockReceiptEncodingsShouldMatchTransactionTypes() {
        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txMixed1");
        assertTrue((legacyReceipt.getEncoded()[0] & 0xFF) >= 0xc0,
                "Mixed block legacy receipt should start with RLP list marker");

        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txMixed2");
        assertEquals((byte) 0x01, type1Receipt.getEncoded()[0],
                "Mixed block Type 1 receipt should start with 0x01");
    }

    @Test
    void legacyReceiptShouldSurviveEncodeDecode() {
        TransactionReceipt original = world.getTransactionReceiptByName("txLegacy");
        byte[] encoded = original.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getGasUsed(), decoded.getGasUsed());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
    }

    @Test
    void type1ReceiptShouldSurviveEncodeDecode() {
        TransactionReceipt original = world.getTransactionReceiptByName("txType1");
        byte[] encoded = original.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
        // RSKIP-546 Type 1 receipt body omits per-tx gasUsed; it is not recoverable from RLP alone.
        assertArrayEquals(new byte[0], decoded.getGasUsed());
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
    void mixedBlockReceiptDTOTypesShouldMatchTransactionDTOTypes() {
        Block block = world.getBlockByName("b03");

        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txMixed1");
        TransactionInfo legacyInfo = new TransactionInfo(legacyReceipt, block.getHash().getBytes(), 0);
        TransactionReceiptDTO legacyDTO = new TransactionReceiptDTO(block, legacyInfo, world.getBlockTxSignatureCache());

        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txMixed2");
        TransactionInfo type1Info = new TransactionInfo(type1Receipt, block.getHash().getBytes(), 1);
        TransactionReceiptDTO type1DTO = new TransactionReceiptDTO(block, type1Info, world.getBlockTxSignatureCache());

        assertEquals("0x0", legacyDTO.getType(), "Mixed block legacy receipt DTO type");
        assertEquals("0x1", type1DTO.getType(), "Mixed block Type 1 receipt DTO type");

        Transaction legacyTx = world.getTransactionByName("txMixed1");
        Transaction type1Tx = world.getTransactionByName("txMixed2");

        assertEquals(legacyDTO.getType(),
                new TransactionResultDTO(block, 0, legacyTx, false, world.getBlockTxSignatureCache()).getType(),
                "Legacy receipt DTO type should match transaction result DTO type");
        assertEquals(type1DTO.getType(),
                new TransactionResultDTO(block, 1, type1Tx, false, world.getBlockTxSignatureCache()).getType(),
                "Type 1 receipt DTO type should match transaction result DTO type");
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
    void mixedBlockTransactionResultDTOTypesShouldBeCorrect() {
        Block block = world.getBlockByName("b03");

        Transaction legacyTx = world.getTransactionByName("txMixed1");
        Transaction type1Tx = world.getTransactionByName("txMixed2");

        TransactionResultDTO legacyDTO = new TransactionResultDTO(
                block, 0, legacyTx, false, world.getBlockTxSignatureCache());
        TransactionResultDTO type1DTO = new TransactionResultDTO(
                block, 1, type1Tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0", legacyDTO.getType(), "Legacy tx in mixed block should have type 0x0");
        assertEquals("0x1", type1DTO.getType(), "Type 1 tx in mixed block should have type 0x1");
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

        assertTrue(ex.getMessage().contains("transaction type not supported"),
                "Error should indicate type not supported, got: " + ex.getMessage());
    }

    @Test
    void explicitType0x00_shouldBeRejectedByDslProcessor() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip543",
                        ConfigValueFactory.fromAnyRef(0))
        );

        String dsl = """
                account_new acc1 100000000000000000
                transaction_build txType0
                    sender acc1
                    receiver acc1
                    value 1000
                    gas 21000
                    transactionType 00
                    build
                """;

        World testWorld = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(testWorld);
        DslParser parser = new DslParser(dsl);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.processCommands(parser),
                "DSL should reject transaction_build with explicit transactionType 00");
        assertTrue(ex.getMessage().contains("transaction type not supported"),
                "Error should mention type not supported, got: " + ex.getMessage());
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

        assertTrue(ex.getMessage().contains("transaction type not supported"),
                "Error should indicate type not supported, got: " + ex.getMessage());
    }
}
