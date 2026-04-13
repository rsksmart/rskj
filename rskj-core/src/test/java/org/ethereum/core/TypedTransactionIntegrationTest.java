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
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.TransactionsMessage;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for typed transactions (RSKIP-543/546) covering transport encoding,
 * hash consistency, mining, receipt format, and receipt trie correctness.
 *
 * <p>Consolidates tests for:
 * <ul>
 *   <li>Block body encoding via {@link BlockTxCodec} (typed wrapping as RLP byte strings)</li>
 *   <li>P2P relay via {@link TransactionsMessage}</li>
 *   <li>Transaction hash consistency across construction, encoding, and mining paths</li>
 *   <li>End-to-end Type 2 (EIP-1559) mining and receipt verification</li>
 *   <li>Receipt trie root correctness with mixed typed/legacy transactions</li>
 * </ul>
 */
class TypedTransactionIntegrationTest {

    private static final byte CHAIN_ID = 33;
    private static final Coin GAS_PRICE = Coin.valueOf(10_000_000_000L);
    private static final Coin INITIAL_BALANCE = Coin.valueOf(1_000_000_000_000_000_000L);

    private TestSystemProperties config;
    private World world;
    private Account sender;
    private Account receiver;

    @BeforeEach
    void setup() {
        config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip126",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(0))
        );
        world = new World(config);
        sender = new AccountBuilder(world).name("sender").balance(INITIAL_BALANCE).build();
        receiver = new AccountBuilder(world).name("receiver").balance(Coin.ZERO).build();
    }

    // =========================================================================
    // Block body encoding: typed txs must be wrapped as RLP byte strings
    // =========================================================================

    @Test
    void legacyTransaction_isNotWrappedInBlockBody() {
        Transaction legacy = buildLegacy(0);
        byte[] encoded = BlockTxCodec.encodeTransaction(legacy);

        assertTrue((encoded[0] & 0xFF) >= 0xC0,
                "Legacy tx in block body must be raw RLP list, first byte: 0x"
                        + String.format("%02x", encoded[0] & 0xFF));
    }

    @Test
    void type1Transaction_isWrappedAsRlpByteStringInBlockBody() {
        Transaction type1 = buildType1(0);
        byte[] encoded = BlockTxCodec.encodeTransaction(type1);

        assertTrue((encoded[0] & 0xFF) >= 0x80 && (encoded[0] & 0xFF) <= 0xBF,
                "Type 1 tx must be wrapped as RLP byte string in block body, first byte: 0x"
                        + String.format("%02x", encoded[0] & 0xFF));
        int contentOffset = computeRlpStringContentOffset(encoded);
        assertEquals((byte) 0x01, encoded[contentOffset],
                "Unwrapped Type 1 payload must start with 0x01");
    }

    @Test
    void type2Transaction_isWrappedAsRlpByteStringInBlockBody() {
        Transaction type2 = buildType2(0);
        byte[] encoded = BlockTxCodec.encodeTransaction(type2);

        assertTrue((encoded[0] & 0xFF) >= 0x80 && (encoded[0] & 0xFF) <= 0xBF,
                "Type 2 tx must be wrapped as RLP byte string in block body");
        int contentOffset = computeRlpStringContentOffset(encoded);
        assertEquals((byte) 0x02, encoded[contentOffset],
                "Unwrapped Type 2 payload must start with 0x02");
    }

    @Test
    void rskNamespaceTransaction_isWrappedAsRlpByteStringInBlockBody() {
        Transaction rsk = buildRskNamespace(0, (byte) 0x03);
        byte[] encoded = BlockTxCodec.encodeTransaction(rsk);

        assertTrue((encoded[0] & 0xFF) >= 0x80 && (encoded[0] & 0xFF) <= 0xBF,
                "RSK namespace tx must be wrapped as RLP byte string in block body");
    }

    // =========================================================================
    // BlockTxCodec encode / decode
    // =========================================================================

    @Test
    void legacyTransaction_encodesAndDecodesViaBlockTxCodec() {
        assertBlockTxCodecEncodeDecode(List.of(buildLegacy(0)));
    }

    @Test
    void type1Transaction_encodesAndDecodesViaBlockTxCodec() {
        assertBlockTxCodecEncodeDecode(List.of(buildType1(0)));
    }

    @Test
    void type2Transaction_encodesAndDecodesViaBlockTxCodec() {
        assertBlockTxCodecEncodeDecode(List.of(buildType2(0)));
    }

    @Test
    void rskNamespaceTransaction_encodesAndDecodesViaBlockTxCodec() {
        assertBlockTxCodecEncodeDecode(List.of(buildRskNamespace(0, (byte) 0x03)));
    }

    @Test
    void mixedBlock_allTransactionTypesEncodeAndDecodeViaBlockTxCodec() {
        List<Transaction> txs = List.of(
                buildLegacy(0),
                buildType1(1),
                buildType2(2),
                buildRskNamespace(3, (byte) 0x05)
        );
        assertBlockTxCodecEncodeDecode(txs);
    }

    @Test
    void type2Transaction_preservesMaxFeeFields_afterEncodeAndDecode() {
        Transaction original = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(21_000))
                .maxPriorityFeePerGas(Coin.valueOf(5_000_000_000L))
                .maxFeePerGas(Coin.valueOf(10_000_000_000L))
                .destination(receiver.getAddress().getBytes())
                .value(Coin.ZERO)
                .build();
        original.sign(sender.getEcKey().getPrivKeyBytes());

        byte[] blockBodyEncoded = BlockTxCodec.encodeTransactions(List.of(original));
        RLPList decoded = RLP.decodeList(blockBodyEncoded);
        List<Transaction> decodedTxs = BlockTxCodec.decodeTransactions(decoded);

        Transaction decoded0 = decodedTxs.get(0);
        assertEquals(Coin.valueOf(5_000_000_000L), decoded0.getMaxPriorityFeePerGas(),
                "maxPriorityFeePerGas must be preserved after BlockTxCodec encode/decode");
        assertEquals(Coin.valueOf(10_000_000_000L), decoded0.getMaxFeePerGas(),
                "maxFeePerGas must be preserved after BlockTxCodec encode/decode");
    }

    // =========================================================================
    // TransactionsMessage relay encode / decode (P2P broadcast)
    // =========================================================================

    @Test
    void type2Transaction_encodesAndDecodesViaTransactionsMessage() {
        Transaction type2 = buildType2(0);
        TransactionsMessage original = new TransactionsMessage(List.of(type2));
        byte[] encoded = original.getEncoded();

        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        TransactionsMessage decoded = (TransactionsMessage) Message.create(blockFactory, paramsList);

        assertNotNull(decoded);
        assertEquals(1, decoded.getTransactions().size());

        Transaction decodedTx = decoded.getTransactions().get(0);
        assertEquals(TransactionType.TYPE_2, decodedTx.getType(),
                "Decoded Type 2 tx must have TYPE_2 type");
        assertFalse(decodedTx.getTypePrefix().isRskNamespace(),
                "Standard Type 2 must NOT be RSK namespace");
        assertArrayEquals(type2.getHash().getBytes(), decodedTx.getHash().getBytes(),
                "Type 2 tx hash must be identical after TransactionsMessage encode/decode");
    }

    @Test
    void mixedTransactionsMessage_allTypesEncodeAndDecode() {
        List<Transaction> txs = List.of(
                buildLegacy(0),
                buildType1(1),
                buildType2(2),
                buildRskNamespace(3, (byte) 0x07)
        );

        TransactionsMessage original = new TransactionsMessage(txs);
        byte[] encoded = original.getEncoded();

        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        TransactionsMessage decoded = (TransactionsMessage) Message.create(blockFactory, paramsList);

        assertNotNull(decoded);
        assertEquals(4, decoded.getTransactions().size());

        assertEquals(TransactionType.LEGACY, decoded.getTransactions().get(0).getType());
        assertEquals(TransactionType.TYPE_1, decoded.getTransactions().get(1).getType());
        assertEquals(TransactionType.TYPE_2, decoded.getTransactions().get(2).getType());
        assertFalse(decoded.getTransactions().get(2).getTypePrefix().isRskNamespace());
        assertTrue(decoded.getTransactions().get(3).getTypePrefix().isRskNamespace());

        for (int i = 0; i < txs.size(); i++) {
            assertArrayEquals(txs.get(i).getHash().getBytes(),
                    decoded.getTransactions().get(i).getHash().getBytes(),
                    "Hash mismatch for transaction at index " + i);
        }
    }

    // =========================================================================
    // Hash consistency: builder -> raw bytes -> decode
    // =========================================================================

    @Test
    void legacyTransaction_hashConsistentAcrossBuilderAndRawDecode() {
        assertHashConsistencyBuilderToRaw(buildLegacy(0));
    }

    @Test
    void type1Transaction_hashConsistentAcrossBuilderAndRawDecode() {
        assertHashConsistencyBuilderToRaw(buildType1(0));
    }

    @Test
    void type2Transaction_hashConsistentAcrossBuilderAndRawDecode() {
        assertHashConsistencyBuilderToRaw(buildType2(0));
    }

    @Test
    void rskNamespaceTransaction_hashConsistentAcrossBuilderAndRawDecode() {
        assertHashConsistencyBuilderToRaw(buildRskNamespace(0, (byte) 0x03));
    }

    // =========================================================================
    // Hash consistency: builder -> BlockTxCodec encode/decode
    // =========================================================================

    @Test
    void legacyTransaction_hashConsistentViaBlockTxCodec() {
        assertHashConsistencyViaBlockTxCodec(buildLegacy(0));
    }

    @Test
    void type1Transaction_hashConsistentViaBlockTxCodec() {
        assertHashConsistencyViaBlockTxCodec(buildType1(0));
    }

    @Test
    void type2Transaction_hashConsistentViaBlockTxCodec() {
        assertHashConsistencyViaBlockTxCodec(buildType2(0));
    }

    @Test
    void rskNamespaceTransaction_hashConsistentViaBlockTxCodec() {
        assertHashConsistencyViaBlockTxCodec(buildRskNamespace(0, (byte) 0x05));
    }

    @Test
    void mixedTransactions_allHashesConsistentViaBlockTxCodec() {
        List<Transaction> txs = List.of(
                buildLegacy(0),
                buildType1(1),
                buildType2(2),
                buildRskNamespace(3, (byte) 0x07)
        );

        byte[] blockBodyEncoded = BlockTxCodec.encodeTransactions(txs);
        RLPList txList = RLP.decodeList(blockBodyEncoded);
        List<Transaction> decoded = BlockTxCodec.decodeTransactions(txList);

        assertEquals(txs.size(), decoded.size());
        for (int i = 0; i < txs.size(); i++) {
            assertArrayEquals(txs.get(i).getHash().getBytes(), decoded.get(i).getHash().getBytes(),
                    "Hash mismatch for transaction at index " + i + " (" + txs.get(i).getType() + ")");
        }
    }

    // =========================================================================
    // Hash consistency: builder -> mine -> retrieve from block
    // =========================================================================

    @Test
    void legacyTransaction_hashConsistentAfterMiningAndBlockRetrieval() {
        assertHashConsistencyAfterMining(buildLegacy(0));
    }

    @Test
    void type1Transaction_hashConsistentAfterMiningAndBlockRetrieval() {
        assertHashConsistencyAfterMining(buildType1(0));
    }

    @Test
    void type2Transaction_hashConsistentAfterMiningAndBlockRetrieval() {
        assertHashConsistencyAfterMining(buildType2(0));
    }

    // =========================================================================
    // Hash stability and distinctness
    // =========================================================================

    @Test
    void type2Transaction_multipleEncodeDecodePassesProduceSameHash() {
        Transaction tx = buildType2(0);

        Transaction decoded1 = new ImmutableTransaction(tx.getEncoded());
        Transaction decoded2 = new ImmutableTransaction(decoded1.getEncoded());
        Transaction decoded3 = new ImmutableTransaction(decoded2.getEncoded());

        assertArrayEquals(tx.getHash().getBytes(), decoded1.getHash().getBytes(),
                "Hash must match after 1st encode/decode");
        assertArrayEquals(tx.getHash().getBytes(), decoded2.getHash().getBytes(),
                "Hash must match after 2nd encode/decode");
        assertArrayEquals(tx.getHash().getBytes(), decoded3.getHash().getBytes(),
                "Hash must match after 3rd encode/decode");
    }

    @Test
    void type2Transaction_hashDependsOnTypePrefixByte() {
        Transaction standard = buildType2(0);
        Transaction rskNamespace = buildRskNamespace(0, (byte) 0x02);

        assertFalse(Arrays.equals(standard.getHash().getBytes(), rskNamespace.getHash().getBytes()),
                "Standard Type 2 and RSK-namespace Type 2 with same nonce must have different hashes");
    }

    // =========================================================================
    // End-to-end Type 2 mining and receipts
    // =========================================================================

    @Test
    void type2Transaction_isStandardNotRskNamespace() {
        Transaction tx = buildType2(0);

        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction(), "Standard Type 2 must NOT be RSK namespace");
    }

    @Test
    void type2TransactionShouldMineAndSucceed() {
        Transaction tx = buildType2(0);
        mineBlock(world.getBlockChain().getBestBlock(), tx);

        TransactionInfo info = getReceiptInfo(tx);
        assertNotNull(info, "Receipt should be stored after mining");

        TransactionReceipt receipt = info.getReceipt();
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus(),
                "Type 2 transaction should succeed");
    }

    @Test
    void type2ReceiptShouldHaveCumulativeGas() {
        Transaction tx = buildType2(0);
        mineBlock(world.getBlockChain().getBestBlock(), tx);

        TransactionInfo info = getReceiptInfo(tx);
        assertNotNull(info);

        long cumulativeGas = info.getReceipt().getCumulativeGasLong();
        assertEquals(21_000L, cumulativeGas,
                "Cumulative gas for a simple value transfer should be 21000");
    }

    @Test
    void type2WithEmptyAccessListShouldMine() {
        Transaction tx = buildType2(0, GAS_PRICE, GAS_PRICE, 21_000, 0);
        Block block = mineBlock(world.getBlockChain().getBestBlock(), tx);

        assertNotNull(block);
        assertEquals(1, block.getTransactionsList().size());
        assertEquals(tx.getHash(), block.getTransactionsList().get(0).getHash());
    }

    // =========================================================================
    // Receipt format after mining
    // =========================================================================

    @Test
    void legacyReceiptHasSixFieldFormat() {
        Transaction tx = buildLegacy(0);
        mineBlock(world.getBlockChain().getBestBlock(), tx);

        TransactionInfo info = getReceiptInfo(tx);
        byte[] encoded = info.getReceipt().getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xC0,
                "Legacy receipt must be a plain RLP list (no type prefix), first byte: 0x"
                        + String.format("%02x", encoded[0] & 0xFF));
    }

    @Test
    void type1ReceiptHasFourFieldFormatWithPrefix() {
        Transaction tx = buildType1(0);
        mineBlock(world.getBlockChain().getBestBlock(), tx);

        TransactionInfo info = getReceiptInfo(tx);
        byte[] encoded = info.getReceipt().getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Type 1 receipt must start with 0x01 prefix");
        assertTrue((encoded[1] & 0xFF) >= 0xC0,
                "Type 1 receipt body must be an RLP list");
    }

    @Test
    void type2ReceiptHasFourFieldFormatWithPrefix() {
        Transaction tx = buildType2(0);
        mineBlock(world.getBlockChain().getBestBlock(), tx);

        TransactionInfo info = getReceiptInfo(tx);
        byte[] encoded = info.getReceipt().getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Type 2 receipt must start with 0x02 prefix");
        assertTrue((encoded[1] & 0xFF) >= 0xC0,
                "Type 2 receipt body must be an RLP list");
    }

    // =========================================================================
    // Receipt trie root correctness
    // =========================================================================

    @Test
    void mixedBlock_receiptTrieRootMatchesBlockHeader() {
        Transaction legacyTx = buildLegacy(0);
        Transaction type1Tx = buildType1(1);
        Transaction type2Tx = buildType2(2);

        Block parent = world.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(List.of(legacyTx, type1Tx, type2Tx))
                .build();

        world.getBlockChain().tryToConnect(block);

        assertEquals(block.getHash(), world.getBlockChain().getBestBlock().getHash(),
                "Mixed block must connect successfully");

        List<TransactionReceipt> receipts = new ArrayList<>();
        byte[] blockHash = block.getHash().getBytes();
        for (Transaction tx : block.getTransactionsList()) {
            TransactionInfo info = world.getReceiptStore()
                    .get(tx.getHash().getBytes(), blockHash)
                    .orElseThrow(() -> new AssertionError("Missing receipt for tx: " + tx.getHash()));
            receipts.add(info.getReceipt());
        }

        byte[] recomputedRoot = BlockHashesHelper.calculateReceiptsTrieRoot(receipts, true);

        assertArrayEquals(block.getHeader().getReceiptsRoot(), recomputedRoot,
                "Receipt trie root from block header must match root recomputed from stored receipts");
    }

    @Test
    void mixedBlock_receiptsHaveCorrectEncodingFormat() {
        Transaction legacyTx = buildLegacy(0);
        Transaction type1Tx = buildType1(1);
        Transaction type2Tx = buildType2(2);

        Block parent = world.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(List.of(legacyTx, type1Tx, type2Tx))
                .build();

        world.getBlockChain().tryToConnect(block);

        byte[] blockHash = block.getHash().getBytes();

        TransactionInfo legacyInfo = world.getReceiptStore()
                .get(legacyTx.getHash().getBytes(), blockHash).orElseThrow();
        TransactionInfo type1Info = world.getReceiptStore()
                .get(type1Tx.getHash().getBytes(), blockHash).orElseThrow();
        TransactionInfo type2Info = world.getReceiptStore()
                .get(type2Tx.getHash().getBytes(), blockHash).orElseThrow();

        assertTrue((legacyInfo.getReceipt().getEncoded()[0] & 0xFF) >= 0xC0);
        assertEquals((byte) 0x01, type1Info.getReceipt().getEncoded()[0]);
        assertEquals((byte) 0x02, type2Info.getReceipt().getEncoded()[0]);
    }

    @Test
    void receiptEncodingsAreStableAcrossLoadFromStore() {
        Transaction type2Tx = buildType2(0);
        Block parent = world.getBlockChain().getBestBlock();
        Block block = mineBlock(parent, type2Tx);

        TransactionInfo info1 = world.getReceiptStore()
                .get(type2Tx.getHash().getBytes(), block.getHash().getBytes()).orElseThrow();
        TransactionInfo info2 = world.getReceiptStore()
                .get(type2Tx.getHash().getBytes(), block.getHash().getBytes()).orElseThrow();

        assertArrayEquals(info1.getReceipt().getEncoded(), info2.getReceipt().getEncoded(),
                "Receipt encoding must be deterministic across multiple loads from store");
    }

    // =========================================================================
    // Helpers: transaction builders
    // =========================================================================

    private Transaction buildLegacy(int nonce) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(receiver.getAddress().getBytes())
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private Transaction buildType1(int nonce) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(receiver.getAddress().getBytes())
                .value(Coin.valueOf(1))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private Transaction buildType2(int nonce) {
        return buildType2(nonce, GAS_PRICE, GAS_PRICE, 21_000, 1);
    }

    private Transaction buildType2(int nonce, Coin maxPriorityFeePerGas, Coin maxFeePerGas,
                                    long gasLimit, long value) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.valueOf(nonce))
                .gasLimit(BigInteger.valueOf(gasLimit))
                .maxPriorityFeePerGas(maxPriorityFeePerGas)
                .maxFeePerGas(maxFeePerGas)
                .destination(receiver.getAddress().getBytes())
                .value(Coin.valueOf(value))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private Transaction buildRskNamespace(int nonce, byte subtype) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(receiver.getAddress().getBytes())
                .value(Coin.valueOf(1))
                .rskSubtype(subtype)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    // =========================================================================
    // Helpers: mining and receipts
    // =========================================================================

    private Block mineBlock(Block parent, Transaction... txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(Arrays.asList(txs))
                .build();
        world.getBlockChain().tryToConnect(block);
        return block;
    }

    private TransactionInfo getReceiptInfo(Transaction tx) {
        return world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElse(null);
    }

    // =========================================================================
    // Helpers: assertions
    // =========================================================================

    private void assertBlockTxCodecEncodeDecode(List<Transaction> originals) {
        byte[] blockBodyEncoded = BlockTxCodec.encodeTransactions(originals);
        RLPList txList = RLP.decodeList(blockBodyEncoded);
        List<Transaction> decoded = BlockTxCodec.decodeTransactions(txList);

        assertEquals(originals.size(), decoded.size());
        for (int i = 0; i < originals.size(); i++) {
            assertArrayEquals(originals.get(i).getEncoded(), decoded.get(i).getEncoded(),
                    "Encoded bytes must be identical after encode/decode at index " + i);
            assertArrayEquals(originals.get(i).getHash().getBytes(), decoded.get(i).getHash().getBytes(),
                    "Hash must be identical after encode/decode at index " + i);
        }
    }

    private void assertHashConsistencyBuilderToRaw(Transaction original) {
        byte[] encoded = original.getEncoded();
        Transaction decoded = new ImmutableTransaction(encoded);

        assertArrayEquals(original.getHash().getBytes(), decoded.getHash().getBytes(),
                "Hash must be identical after builder -> raw encode -> ImmutableTransaction decode");
        assertArrayEquals(encoded, decoded.getEncoded(),
                "Encoded bytes must be stable after encode/decode");
    }

    private void assertHashConsistencyViaBlockTxCodec(Transaction original) {
        byte[] blockBodyEncoded = BlockTxCodec.encodeTransactions(List.of(original));
        RLPList txList = RLP.decodeList(blockBodyEncoded);
        List<Transaction> decoded = BlockTxCodec.decodeTransactions(txList);

        assertEquals(1, decoded.size());
        assertArrayEquals(original.getHash().getBytes(), decoded.get(0).getHash().getBytes(),
                "Hash must be identical after BlockTxCodec encode/decode for type: " + original.getType());
    }

    private void assertHashConsistencyAfterMining(Transaction original) {
        Block block = mineBlock(world.getBlockChain().getBestBlock(), original);
        Block minedBlock = world.getBlockChain().getBestBlock();
        Transaction fromBlock = minedBlock.getTransactionsList().get(0);

        assertArrayEquals(original.getHash().getBytes(), fromBlock.getHash().getBytes(),
                "Hash must be identical when retrieved from mined block");
    }

    /**
     * Returns the byte offset of the content in an RLP-encoded byte string.
     * For strings with length < 56: {@code [0x80 + len, content...]}.
     * For strings with length >= 56: {@code [0xB7 + lenOfLen, len..., content...]}.
     */
    private static int computeRlpStringContentOffset(byte[] rlpString) {
        int first = rlpString[0] & 0xFF;
        if (first <= 0xB7) {
            return 1;
        }
        int lenOfLen = first - 0xB7;
        return 1 + lenOfLen;
    }
}
