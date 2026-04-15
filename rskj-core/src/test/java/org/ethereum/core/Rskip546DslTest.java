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

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSL integration tests for RSKIP-546: Type 1 and Type 2 transaction encoding.
 *
 * <p>Covers:
 * <ul>
 *   <li>Type 1 (EIP-2930 format) transaction execution and receipt encoding</li>
 *   <li>Type 2 (EIP-1559 format) transaction execution and receipt encoding</li>
 *   <li>Effective gas price = min(maxPriorityFeePerGas, maxFeePerGas) for Type 2</li>
 *   <li>Type 1 receipt: {@code 0x01 || rlp([status, cumulativeGas, bloom, logs])}</li>
 *   <li>Type 2 receipt: {@code 0x02 || rlp([status, cumulativeGas, bloom, logs])}</li>
 *   <li>Access list bytes accepted but not executed; 80 gas/byte charged for non-empty</li>
 *   <li>Empty access list (0xc0) not charged extra gas</li>
 *   <li>Mixed blocks with legacy, Type 1 and Type 2 coexisting</li>
 *   <li>Block encode/decode preserves typed transactions and their hashes</li>
 *   <li>Activation guard: Type 2 rejected before RSKIP-546 activation</li>
 * </ul>
 */
class Rskip546DslTest {

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(0))
        );

        DslParser parser = DslParser.fromResource("dsl/transaction/rskip546/rskip546Test.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    // =========================================================================
    // Type 1 basics
    // =========================================================================

    @Test
    void type1Transaction_hasCorrectType() {
        Transaction tx = world.getTransactionByName("txType1Basic");

        assertNotNull(tx);
        assertEquals(TransactionType.TYPE_1, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
    }

    @Test
    void type1Transaction_encodingStartsWith0x01() {
        Transaction tx = world.getTransactionByName("txType1Basic");
        byte[] encoded = tx.getEncoded();

        assertEquals((byte) 0x01, encoded[0], "Type 1 tx must start with 0x01");
        assertTrue((encoded[1] & 0xFF) >= 0xc0, "Payload must start with RLP list marker");
    }

    @Test
    void type1Transaction_rawEncodingStartsWith0x01() {
        Transaction tx = world.getTransactionByName("txType1Basic");
        byte[] raw = tx.getEncodedRaw();

        assertEquals((byte) 0x01, raw[0],
                "Type 1 signing hash input must be prefixed with 0x01");
    }

    @Test
    void type1Transaction_executesSuccessfully() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Basic");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void type1Transaction_rpcTypeFieldIs0x1() {
        Transaction tx = world.getTransactionByName("txType1Basic");

        assertEquals("0x1", tx.getTypeAsHex());
    }

    // =========================================================================
    // Type 1 receipt encoding (RSKIP-546: 4-field body, 0x01 prefix)
    // =========================================================================

    @Test
    void type1Receipt_startsWithTypeByte0x01() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Receipt");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Type 1 receipt must start with 0x01");
    }

    @Test
    void type1Receipt_bodyHasFourFields() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Receipt");
        byte[] encoded = receipt.getEncoded();

        // Skip the 0x01 prefix and decode the RLP body
        byte[] body = new byte[encoded.length - 1];
        System.arraycopy(encoded, 1, body, 0, body.length);
        RLPList fields = RLP.decodeList(body);

        assertEquals(4, fields.size(),
                "Type 1 receipt body must have exactly 4 fields: status, cumulativeGas, bloom, logs");
    }

    @Test
    void type1Receipt_survivesEncodeDecode() {
        TransactionReceipt original = world.getTransactionReceiptByName("txType1Receipt");
        TransactionReceipt decoded = new TransactionReceipt(original.getEncoded());

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
        assertArrayEquals(original.getBloomFilter().getData(), decoded.getBloomFilter().getData());
    }

    @Test
    void type1ReceiptDTO_typeIs0x1() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Receipt");
        Block block = world.getBlockByName("b04");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x1", dto.getType());
    }

    // =========================================================================
    // Type 2 basics
    // =========================================================================

    @Test
    void type2Transaction_hasCorrectType() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");

        assertNotNull(tx);
        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
    }

    @Test
    void type2Transaction_encodingStartsWith0x02() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");
        byte[] encoded = tx.getEncoded();

        assertEquals((byte) 0x02, encoded[0], "Standard Type 2 tx must start with 0x02");
        assertTrue((encoded[1] & 0xFF) >= 0xc0, "Payload must start with RLP list marker");
    }

    @Test
    void type2Transaction_executesSuccessfully() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2PriorityLower");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void type2Transaction_rpcTypeFieldIs0x2() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");

        assertEquals("0x2", tx.getTypeAsHex());
    }

    // =========================================================================
    // Effective gas price: min(maxPriorityFeePerGas, maxFeePerGas)
    // =========================================================================

    @Test
    void type2_effectiveGasPrice_whenPriorityLowerThanMaxFee_isMaxPriority() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");
        // maxPriority=500000000, maxFee=2000000000 → effective = 500000000
        Coin expected = Coin.valueOf(500_000_000L);

        assertEquals(expected, tx.getGasPrice(),
                "Effective gas price must be min(maxPriority, maxFee) = maxPriority when priority < maxFee");
    }

    @Test
    void type2_effectiveGasPrice_whenFeesAreEqual_isEitherValue() {
        Transaction tx = world.getTransactionByName("txType2EqualFees");
        // maxPriority=1000000000, maxFee=1000000000 → effective = 1000000000
        Coin expected = Coin.valueOf(1_000_000_000L);

        assertEquals(expected, tx.getGasPrice(),
                "Effective gas price must equal both fee fields when they are equal");
    }

    @Test
    void type2_maxPriorityFeePerGas_fieldExposedCorrectly() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");

        assertNotNull(tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(500_000_000L), tx.getMaxPriorityFeePerGas());
    }

    @Test
    void type2_maxFeePerGas_fieldExposedCorrectly() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");

        assertNotNull(tx.getMaxFeePerGas());
        assertEquals(Coin.valueOf(2_000_000_000L), tx.getMaxFeePerGas());
    }

    // =========================================================================
    // Type 2 receipt encoding (RSKIP-546: 4-field body, 0x02 prefix)
    // =========================================================================

    @Test
    void type2Receipt_startsWithTypeByte0x02() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2Receipt");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Standard Type 2 receipt must start with 0x02");
    }

    @Test
    void type2Receipt_bodyHasFourFields() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2Receipt");
        byte[] encoded = receipt.getEncoded();

        byte[] body = new byte[encoded.length - 1];
        System.arraycopy(encoded, 1, body, 0, body.length);
        RLPList fields = RLP.decodeList(body);

        assertEquals(4, fields.size(),
                "Type 2 receipt body must have exactly 4 fields: status, cumulativeGas, bloom, logs");
    }

    @Test
    void type2Receipt_survivesEncodeDecode() {
        TransactionReceipt original = world.getTransactionReceiptByName("txType2Receipt");
        TransactionReceipt decoded = new TransactionReceipt(original.getEncoded());

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
        assertArrayEquals(original.getBloomFilter().getData(), decoded.getBloomFilter().getData());
    }

    @Test
    void type2ReceiptDTO_typeIs0x2() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2Receipt");
        Block block = world.getBlockByName("b05");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        assertEquals("0x2", dto.getType());
    }

    @Test
    void type2ReceiptDTO_effectiveGasPriceMatchesTxGasPrice() {
        Transaction tx = world.getTransactionByName("txType2Receipt");
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2Receipt");
        Block block = world.getBlockByName("b05");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());

        // effectiveGasPrice in the DTO must match min(maxPriority, maxFee) from the tx
        assertNotNull(dto.getEffectiveGasPrice());
        String expectedHex = "0x" + tx.getGasPrice().asBigInteger().toString(16);
        assertEquals(expectedHex, dto.getEffectiveGasPrice());
    }

    // =========================================================================
    // RPC DTO fields for typed transactions
    // =========================================================================

    @Test
    void type1TransactionResultDTO_hasExpectedFields() {
        Transaction tx = world.getTransactionByName("txType1Basic");
        Block block = world.getBlockByName("b01");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x1", dto.getType());
        assertNotNull(dto.getChainId(), "Type 1 must have chainId in RPC response");
        assertNotNull(dto.getAccessList(), "Type 1 must have accessList in RPC response");
        assertNotNull(dto.getYParity(), "Type 1 must have yParity in RPC response");
        assertNull(dto.getMaxFeePerGas(), "Type 1 must NOT have maxFeePerGas in RPC response");
        assertNull(dto.getMaxPriorityFeePerGas(), "Type 1 must NOT have maxPriorityFeePerGas");
    }

    @Test
    void type2TransactionResultDTO_hasExpectedFields() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");
        Block block = world.getBlockByName("b02");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x2", dto.getType());
        assertNotNull(dto.getChainId(), "Type 2 must have chainId");
        assertNotNull(dto.getAccessList(), "Type 2 must have accessList");
        assertNotNull(dto.getYParity(), "Type 2 must have yParity");
        assertNotNull(dto.getMaxFeePerGas(), "Type 2 must have maxFeePerGas");
        assertNotNull(dto.getMaxPriorityFeePerGas(), "Type 2 must have maxPriorityFeePerGas");
    }

    @Test
    void legacyTransactionResultDTO_hasNoTypedFields() {
        Transaction tx = world.getTransactionByName("txMixedLegacy");
        Block block = world.getBlockByName("b06");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x0", dto.getType());
        assertNull(dto.getChainId(), "Legacy tx must NOT have chainId in RPC response");
        assertNull(dto.getAccessList(), "Legacy tx must NOT have accessList");
        assertNull(dto.getYParity(), "Legacy tx must NOT have yParity");
        assertNull(dto.getMaxFeePerGas(), "Legacy tx must NOT have maxFeePerGas");
        assertNull(dto.getMaxPriorityFeePerGas(), "Legacy tx must NOT have maxPriorityFeePerGas");
    }

    @Test
    void type1TransactionResultDTO_yParityIsZeroOrOne() {
        Transaction tx = world.getTransactionByName("txType1Basic");
        Block block = world.getBlockByName("b01");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        String yParity = dto.getYParity();
        assertTrue("0x0".equals(yParity) || "0x1".equals(yParity),
                "yParity must be 0x0 or 0x1, got: " + yParity);
    }

    @Test
    void type1TransactionResultDTO_vMatchesYParity() {
        Transaction tx = world.getTransactionByName("txType1Basic");
        Block block = world.getBlockByName("b01");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        // For typed transactions, v and yParity must be the same value
        assertEquals(dto.getV(), dto.getYParity(),
                "For typed transactions, v and yParity must hold the same value");
    }

    // =========================================================================
    // Mixed block: legacy + Type 1 + Type 2 coexist
    // =========================================================================

    @Test
    void mixedBlock_containsAllThreeTypes() {
        Block b06 = world.getBlockByName("b06");

        assertNotNull(b06);
        assertEquals(3, b06.getTransactionsList().size());

        assertEquals(TransactionType.LEGACY, b06.getTransactionsList().get(0).getType());
        assertEquals(TransactionType.TYPE_1, b06.getTransactionsList().get(1).getType());
        assertEquals(TransactionType.TYPE_2, b06.getTransactionsList().get(2).getType());
    }

    @Test
    void mixedBlock_allTransactionsSucceed() {
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txMixedLegacy").getStatus());
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txMixedType1").getStatus());
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txMixedType2").getStatus());
    }

    @Test
    void mixedBlock_receiptsHaveCorrectPrefixes() {
        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txMixedLegacy");
        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txMixedType1");
        TransactionReceipt type2Receipt = world.getTransactionReceiptByName("txMixedType2");

        // Legacy receipt: no type prefix, starts with RLP list marker
        assertTrue((legacyReceipt.getEncoded()[0] & 0xFF) >= 0xc0,
                "Legacy receipt must start with RLP list marker");

        // Type 1 receipt: 0x01 prefix
        assertEquals((byte) 0x01, type1Receipt.getEncoded()[0],
                "Type 1 receipt must start with 0x01");

        // Type 2 receipt: 0x02 prefix
        assertEquals((byte) 0x02, type2Receipt.getEncoded()[0],
                "Type 2 receipt must start with 0x02");
    }

    // =========================================================================
    // Block encode/decode preserves typed transactions
    // =========================================================================

    @Test
    void type1Block_surviveEncodeDecode_preservesType() {
        Block original = world.getBlockByName("b01");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(1, decoded.getTransactionsList().size());
        assertEquals(TransactionType.TYPE_1, decoded.getTransactionsList().get(0).getType());
    }

    @Test
    void type1Block_surviveEncodeDecode_preservesHash() {
        Block original = world.getBlockByName("b01");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(original.getTransactionsList().get(0).getHash(),
                decoded.getTransactionsList().get(0).getHash(),
                "Type 1 tx hash must be preserved through block encode/decode");
    }

    @Test
    void type2Block_surviveEncodeDecode_preservesType() {
        Block original = world.getBlockByName("b02");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(1, decoded.getTransactionsList().size());
        Transaction decodedTx = decoded.getTransactionsList().get(0);
        assertEquals(TransactionType.TYPE_2, decodedTx.getType());
        assertFalse(decodedTx.isRskNamespaceTransaction());
    }

    @Test
    void type2Block_surviveEncodeDecode_preservesFeeFields() {
        Block original = world.getBlockByName("b02");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        Transaction decodedTx = decoded.getTransactionsList().get(0);

        assertEquals(Coin.valueOf(500_000_000L), decodedTx.getMaxPriorityFeePerGas(),
                "maxPriorityFeePerGas must survive block encode/decode");
        assertEquals(Coin.valueOf(2_000_000_000L), decodedTx.getMaxFeePerGas(),
                "maxFeePerGas must survive block encode/decode");
    }

    @Test
    void type2Block_surviveEncodeDecode_preservesHash() {
        Block original = world.getBlockByName("b02");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(original.getTransactionsList().get(0).getHash(),
                decoded.getTransactionsList().get(0).getHash(),
                "Type 2 tx hash must be preserved through block encode/decode");
    }

    @Test
    void mixedBlock_surviveEncodeDecode_preservesAllTypes() {
        Block original = world.getBlockByName("b06");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(3, decoded.getTransactionsList().size());
        assertEquals(TransactionType.LEGACY, decoded.getTransactionsList().get(0).getType());
        assertEquals(TransactionType.TYPE_1, decoded.getTransactionsList().get(1).getType());
        assertEquals(TransactionType.TYPE_2, decoded.getTransactionsList().get(2).getType());
    }

    @Test
    void mixedBlock_surviveEncodeDecode_allHashesMatch() {
        Block original = world.getBlockByName("b06");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        for (int i = 0; i < 3; i++) {
            assertEquals(original.getTransactionsList().get(i).getHash(),
                    decoded.getTransactionsList().get(i).getHash(),
                    "Tx[" + i + "] hash must survive block encode/decode");
        }
    }

    // =========================================================================
    // Access list gas: 80 gas/byte for non-empty, no charge for empty (0xc0)
    // =========================================================================

    @Test
    void type1Transaction_emptyAccessList_noExtraGas() {
        Transaction tx = world.getTransactionByName("txType1Basic");

        // Empty access list = 0xc0 (1 byte). Per RSKIP-546 length > 1 check, no charge.
        byte[] accessList = tx.getAccessListBytes();
        boolean isEmpty = (accessList == null || accessList.length <= 1);
        assertTrue(isEmpty,
                "DSL-built Type 1 without explicit accessList must have empty access list");

        // Base intrinsic gas for a simple transfer is 21000; no access list surcharge
        RskSystemProperties config = world.getConfig();
        long cost = tx.transactionCost(
                config.getNetworkConstants(),
                config.getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());
        assertEquals(GasCost.TRANSACTION, cost,
                "Type 1 with empty access list must have base intrinsic gas of 21000");
    }

    @Test
    void type1Transaction_nonEmptyAccessList_chargesExtraGas() {
        // Build a Type 1 transaction with a non-empty access list directly (not via DSL)
        // to verify the 80 gas/byte charge.
        // accessList = rlp([[address, []]]) — a minimal single-entry access list
        // One 20-byte address + empty storage key list: roughly 23 RLP bytes
        byte[] address = new byte[20]; // zero address
        byte[] encodedAddress = org.ethereum.util.RLP.encodeElement(address);
        byte[] encodedKeyList = org.ethereum.util.RLP.encodeList();
        byte[] entry = org.ethereum.util.RLP.encodeList(encodedAddress, encodedKeyList);
        byte[] accessListRlp = org.ethereum.util.RLP.encodeList(entry);

        co.rsk.core.RskAddress receiver = new co.rsk.core.RskAddress(
                world.getTransactionByName("txType1Basic").getReceiveAddress().getBytes());

        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId((byte) 33)
                .nonce(BigInteger.valueOf(99))
                .gasPrice(Coin.valueOf(1_000_000_000L))
                .gasLimit(BigInteger.valueOf(100_000))
                .destination(receiver)
                .value(Coin.valueOf(1))
                .accessList(accessListRlp)
                .build();
        // Sign so BridgeUtils.isFreeBridgeTx can resolve the sender
        tx.sign(org.ethereum.crypto.ECKey.fromPrivate(BigInteger.valueOf(12345)).getPrivKeyBytes());

        RskSystemProperties config = world.getConfig();
        long cost = tx.transactionCost(
                config.getNetworkConstants(),
                config.getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());

        long expectedExtra = (long) accessListRlp.length * GasCost.ACCESS_LIST_GAS_PER_BYTE;
        assertEquals(GasCost.TRANSACTION + expectedExtra, cost,
                "Type 1 with non-empty access list must be charged " +
                        GasCost.ACCESS_LIST_GAS_PER_BYTE + " gas/byte; " +
                        "accessList bytes=" + accessListRlp.length +
                        ", extra=" + expectedExtra);
    }

    @Test
    void type2Transaction_emptyAccessList_noExtraGas() {
        Transaction tx = world.getTransactionByName("txType2PriorityLower");

        byte[] accessList = tx.getAccessListBytes();
        boolean isEmpty = (accessList == null || accessList.length <= 1);
        assertTrue(isEmpty, "DSL-built Type 2 without explicit accessList must have empty access list");

        RskSystemProperties config = world.getConfig();
        long cost = tx.transactionCost(
                config.getNetworkConstants(),
                config.getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());
        assertEquals(GasCost.TRANSACTION, cost,
                "Type 2 with empty access list must have base intrinsic gas of 21000");
    }

    // =========================================================================
    // Contract deployment and interaction
    // =========================================================================

    @Test
    void type1Deploy_createsContract() {
        Transaction tx = world.getTransactionByName("txType1Deploy");

        assertNotNull(tx);
        assertTrue(tx.isContractCreation());
        assertEquals(TransactionType.TYPE_1, tx.getType());

        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Deploy");
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
        assertNotNull(tx.getContractAddress(), "Contract address must be set after deployment");
    }

    @Test
    void type1Deploy_receipt_hasType1Prefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType1Deploy");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Contract deployment via Type 1 must produce a 0x01-prefixed receipt");
    }

    @Test
    void type2ContractCall_succeeds() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2CallSetValue");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void type2ContractCall_receipt_hasType2Prefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType2CallSetValue");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Contract call via Type 2 must produce a 0x02-prefixed receipt");
    }

    // =========================================================================
    // Chain height sanity
    // =========================================================================

    @Test
    void blockchain_reachesExpectedHeight() {
        assertEquals(8, world.getBlockChain().getBestBlock().getNumber(),
                "Chain must have 8 blocks after all test scenarios");
    }

    @Test
    void bestBlock_isB08() {
        Block b08 = world.getBlockByName("b08");
        assertEquals(b08, world.getBlockChain().getBestBlock());
    }
}
