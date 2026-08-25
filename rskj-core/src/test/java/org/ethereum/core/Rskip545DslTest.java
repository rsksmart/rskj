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
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DSL integration tests for RSKIP-545: Type 4 (EIP-7702 set-code) transactions.
 *
 * <p>Covers on-chain behavior from
 * <a href="https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP545.md">RSKIP-545</a>:
 * <ul>
 *   <li>Type 4 encoding and receipt prefix {@code 0x04}</li>
 *   <li>Authorization list processing (self-auth, external authority, revoke, chainId 0)</li>
 *   <li>Delegation indicator {@code 0xef0100 || address} written to authority code</li>
 *   <li>Effective gas price = min(maxPriorityFeePerGas, maxFeePerGas)</li>
 *   <li>Gas intrinsic cost and execution consumption (PER_EMPTY_ACCOUNT_COST / refund)</li>
 *   <li>Mixed blocks with legacy and Type 4 transactions</li>
 *   <li>Block encode/decode preserves Type 4 transactions</li>
 * </ul>
 *
 * <p>See also {@link Rskip545DeepDslTest}, {@link Rskip545OriginInvariantDslTest},
 * {@link Rskip545HardforkTest}, and {@link Rskip545DelegatedOpcodeTest}.
 */
class Rskip545DslTest {

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(0))
        );

        DslParser parser = DslParser.fromResource("dsl/transaction/rskip545/rskip545Test.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    static Stream<String> type4TxNames() {
        return Stream.of(
                "txType4SelfAuth",
                "txType4WithValue",
                "txType4ChainId0",
                "txType4Revoke",
                "txType4ExternalAuth",
                "txType4Receipt",
                "txMixedType4"
        );
    }

    @ParameterizedTest
    @MethodSource("type4TxNames")
    void type4Transaction_hasCorrectType(String txName) {
        Transaction tx = world.getTransactionByName(txName);

        assertNotNull(tx);
        assertEquals(TransactionType.TYPE_4, tx.getType());
        assertFalse(tx.isRskNamespaceTransaction());
        assertFalse(tx.getAuthorizationList().isEmpty(),
                "Type 4 transaction must carry a non-empty authorization list");
    }

    @ParameterizedTest
    @MethodSource("type4TxNames")
    void type4Transaction_encodingStartsWith0x04(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        byte[] encoded = tx.getEncoded();

        assertEquals((byte) 0x04, encoded[0], "Type 4 tx must start with 0x04");
        assertTrue((encoded[1] & 0xFF) >= 0xc0, "Payload must start with RLP list marker");
    }

    @Test
    void type4Transaction_rawEncodingStartsWith0x04() {
        Transaction tx = world.getTransactionByName("txType4SelfAuth");
        assertEquals((byte) 0x04, tx.getEncodedRaw()[0]);
    }

    @ParameterizedTest
    @MethodSource("type4TxNames")
    void type4Transaction_executesSuccessfully(String txName) {
        TransactionReceipt receipt = world.getTransactionReceiptByName(txName);
        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @ParameterizedTest
    @MethodSource("type4TxNames")
    void type4Transaction_rpcTypeField(String txName) {
        assertEquals("0x4", world.getTransactionByName(txName).getTypeAsHex());
    }

    @Test
    void type4_effectiveGasPrice_isMinOfFeeFields() {
        Transaction tx = world.getTransactionByName("txType4SelfAuth");

        assertEquals(Coin.valueOf(1_000_000_000L), tx.getGasPrice(),
                "Effective gas price must be min(maxPriority, maxFee)");
        assertEquals(Coin.valueOf(1_000_000_000L), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(2_000_000_000L), tx.getMaxFeePerGas());
    }

    // =========================================================================
    // Gas intrinsic cost and consumption (RSKIP-545 gas schedule)
    // =========================================================================

    private static final long SINGLE_AUTH_INTRINSIC =
            GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST;
    private static final long DELEGATION_REPLACEMENT_REFUND =
            GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;

    @Test
    void type4_singleAuth_intrinsicGas_equalsTransactionPlusPerEmptyAccountCost() {
        assertEquals(SINGLE_AUTH_INTRINSIC, intrinsicGas("txType4SelfAuth"),
                "Intrinsic = 21000 + 25000 per RSKIP-545");
        assertEquals(SINGLE_AUTH_INTRINSIC, intrinsicGas("txType4WithValue"));
        assertEquals(SINGLE_AUTH_INTRINSIC, intrinsicGas("txType4ExternalAuth"));
    }

    @Test
    void type4_dualAuth_intrinsicGas_chargesPerTupleUpfront() {
        Account sender = world.getAccountByName("acc1");
        Account receiver = world.getAccountByName("acc2");
        SetCodeAuthorization auth1 = Rskip545TestSupport.createSignedAuthorization(
                sender.getEcKey(), Rskip545TestSupport.DEFAULT_DELEGATE, BigInteger.ONE, (byte) 33);
        SetCodeAuthorization auth2 = Rskip545TestSupport.createSignedAuthorization(
                world.getAccountByName("authority").getEcKey(),
                Rskip545TestSupport.DEFAULT_DELEGATE, BigInteger.ZERO, (byte) 33);

        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId((byte) 33)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(300_000))
                .maxPriorityFeePerGas(Coin.valueOf(1_000_000_000L))
                .maxFeePerGas(Coin.valueOf(2_000_000_000L))
                .receiveAddress(receiver.getAddress())
                .value(Coin.ZERO)
                .data(new byte[0])
                .authorizationList(java.util.List.of(auth1, auth2))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        long intrinsic = tx.transactionCost(
                world.getConfig().getNetworkConstants(),
                world.getConfig().getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());

        assertEquals(GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST, intrinsic);
    }

    @Test
    void type4_firstSelfAuth_onEmptyAuthority_gasUsedEqualsIntrinsic() {
        assertEquals(SINGLE_AUTH_INTRINSIC, gasUsedInBlock("b01", "txType4SelfAuth"),
                "Empty authority: full PER_EMPTY_ACCOUNT_COST kept (no refund)");
    }

    @Test
    void type4_reAuthorization_onDelegatedAuthority_gasUsedReflectsRefund() {
        long firstAuthGas = gasUsedInBlock("b01", "txType4SelfAuth");
        long reAuthGas = gasUsedInBlock("b02", "txType4WithValue");

        assertEquals(DELEGATION_REPLACEMENT_REFUND, firstAuthGas - reAuthGas,
                "Replacing delegation refunds PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST");
        assertEquals(SINGLE_AUTH_INTRINSIC - DELEGATION_REPLACEMENT_REFUND, reAuthGas);
    }

    @Test
    void type4_externalAuth_onEmptyAuthority_gasUsedEqualsIntrinsic() {
        assertEquals(SINGLE_AUTH_INTRINSIC, gasUsedInBlock("b04", "txType4ExternalAuth"));
    }

    static Stream<Arguments> delegatedAuthorityGasCases() {
        return Stream.of(
                Arguments.of("txType4WithValue", "b02"),
                Arguments.of("txType4ChainId0", "b03"),
                Arguments.of("txType4Receipt", "b05"),
                Arguments.of("txType4Revoke", "b07"),
                Arguments.of("txMixedType4", "b06")
        );
    }

    @ParameterizedTest
    @MethodSource("delegatedAuthorityGasCases")
    void type4_onDelegatedAuthority_gasUsedReflectsReplacementRefund(String txName, String blockName) {
        assertEquals(SINGLE_AUTH_INTRINSIC - DELEGATION_REPLACEMENT_REFUND,
                gasUsedInBlock(blockName, txName));
    }

    @ParameterizedTest
    @MethodSource("type4TxNames")
    void type4_executedGasUsed_isPositiveAndWithinGasLimit(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        long used = gasUsedInBlock(blockForTransaction(txName), txName);
        assertTrue(used > 0);
        assertTrue(used <= tx.getGasLimitAsInteger().longValue());
    }

    @Test
    void type4_selfAuth_senderBalanceDecreasesByGasUsedTimesEffectivePrice() {
        Account acc1 = world.getAccountByName("acc1");
        Coin balanceBefore = balanceAtBlockParent("b01", acc1);
        long gasUsed = gasUsedInBlock("b01", "txType4SelfAuth");
        Coin expectedFee = Coin.valueOf(gasUsed).multiply(BigInteger.valueOf(1_000_000_000L));

        assertEquals(balanceBefore.subtract(expectedFee), balanceAtBlock("b01", acc1));
    }

    @Test
    void type4_withValue_gasUsedUnchangedByTransferAmount() {
        assertEquals(gasUsedInBlock("b02", "txType4WithValue"),
                gasUsedInBlock("b03", "txType4ChainId0"),
                "Value transfer does not change Type 4 gas beyond call stipend");
    }

    @Test
    void type4_receiptDTO_gasUsed_matchesCumulativeDelta() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType4SelfAuth");
        Block block = world.getBlockByName("b01");
        long derivedGasUsed = gasUsedInBlock("b01", "txType4SelfAuth");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(
                block, txInfo, world.getBlockTxSignatureCache(), 0, derivedGasUsed);

        assertEquals("0x" + Long.toHexString(derivedGasUsed), dto.getGasUsed());
        assertEquals("0x" + Long.toHexString(receipt.getCumulativeGasLong()), dto.getCumulativeGasUsed());
    }

    @Test
    void mixedBlock_legacyGasUsed_isBaseTransactionCost() {
        assertEquals(GasCost.TRANSACTION, gasUsedInBlock("b06", "txMixedLegacy"));
    }

    @Test
    void mixedBlock_type4GasUsed_reflectsDelegationReplacementRefund() {
        assertEquals(SINGLE_AUTH_INTRINSIC - DELEGATION_REPLACEMENT_REFUND,
                gasUsedInBlock("b06", "txMixedType4"));
    }

    @Test
    void mixedBlock_cumulativeGas_isSumOfPerTxGasUsed() {
        Block block = world.getBlockByName("b06");
        long total = 0;
        for (Transaction tx : block.getTransactionsList()) {
            total += gasUsedInBlock("b06", tx == block.getTransactionsList().get(0)
                    ? "txMixedLegacy" : "txMixedType4");
        }
        TransactionReceipt lastReceipt = world.getTransactionReceiptByName("txMixedType4");
        assertEquals(total, lastReceipt.getCumulativeGasLong());
    }

    // =========================================================================
    // Delegation indicator on authority accounts (RSKIP-545 behavior section)
    // =========================================================================

    @Test
    void selfAuthorization_writesDelegationIndicatorToAuthority() {
        Account acc1 = world.getAccountByName("acc1");
        Block b01 = world.getBlockByName("b01");
        RepositorySnapshot repo = world.getRepositoryLocator().snapshotAt(b01.getHeader());
        byte[] code = repo.getCode(acc1.getAddress());

        assertTrue(DelegationCodeResolver.isDelegatedCode(code),
                "After self-auth, authority must have delegation indicator");
        assertEquals(Rskip545TestSupport.DEFAULT_DELEGATE,
                DelegationCodeResolver.extractDelegatedAddress(code));
    }

    @Test
    void revokeAuthorization_clearsAuthorityCode() {
        Account acc1 = world.getAccountByName("acc1");
        Block b07 = world.getBlockByName("b07");
        RepositorySnapshot repo = world.getRepositoryLocator().snapshotAt(b07.getHeader());
        byte[] code = repo.getCode(acc1.getAddress());

        assertTrue(code == null || java.util.Arrays.equals(
                        SetCodeAuthorizationTransactionExecutor.CODE_FOR_CLEANING_DELEGATED_ADDRESS, code),
                "Revoke clears delegated code from authority (empty or absent)");
    }

    @Test
    void externalAuthorization_delegatesExternalAuthorityNotSender() {
        Account authority = world.getAccountByName("authority");
        Account acc1 = world.getAccountByName("acc1");

        Block b04 = world.getBlockByName("b04");
        RepositorySnapshot repo = world.getRepositoryLocator().snapshotAt(b04.getHeader());
        byte[] authorityCode = repo.getCode(authority.getAddress());

        assertTrue(DelegationCodeResolver.isDelegatedCode(authorityCode),
                "External authority account must receive delegation indicator");
        assertEquals(BigInteger.ONE, repo.getNonce(authority.getAddress()));
        assertEquals(BigInteger.valueOf(7), repo.getNonce(acc1.getAddress()),
                "External auth increments authority nonce; sender only from tx increment");
    }

    @Test
    void selfAuthorization_incrementsAuthorityNonce() {
        Account acc1 = world.getAccountByName("acc1");
        Block b01 = world.getBlockByName("b01");
        RepositorySnapshot repo = world.getRepositoryLocator().snapshotAt(b01.getHeader());

        assertEquals(BigInteger.valueOf(2), repo.getNonce(acc1.getAddress()),
                "Self-auth applies sender increment (+1) then authority increment (+1)");
    }

    // =========================================================================
    // Typed receipt encoding (RSKIP-545: 0x04 || rlp([status, cumulativeGas, bloom, logs]))
    // =========================================================================

    @Test
    void type4Receipt_startsWith0x04Prefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType4Receipt");
        assertEquals((byte) 0x04, receipt.getEncoded()[0]);
    }

    @Test
    void type4Receipt_bodyHasFourFields() {
        byte[] encoded = world.getTransactionReceiptByName("txType4Receipt").getEncoded();
        byte[] body = Arrays.copyOfRange(encoded, 1, encoded.length);
        RLPList fields = RLP.decodeList(body);

        assertEquals(4, fields.size(),
                "Type 4 receipt body: status, cumulativeGas, bloom, logs");
    }

    @Test
    void type4Receipt_survivesEncodeDecode() {
        TransactionReceipt original = world.getTransactionReceiptByName("txType4Receipt");
        TransactionReceipt decoded = new TransactionReceipt(original.getEncoded());

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
        assertArrayEquals(original.getBloomFilter().getData(), decoded.getBloomFilter().getData());
    }

    @Test
    void type4ReceiptDTO_typeMatchesTransaction() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txType4Receipt");
        Block block = world.getBlockByName("b05");
        TransactionInfo txInfo = new TransactionInfo(receipt, block.getHash().getBytes(), 0);

        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo, world.getBlockTxSignatureCache());
        assertEquals("0x4", dto.getType());
    }

    // =========================================================================
    // RPC DTO fields
    // =========================================================================

    @Test
    void type4TransactionResultDTO_exposesTypeAndEffectiveGasPrice() {
        Transaction tx = world.getTransactionByName("txType4SelfAuth");
        Block block = world.getBlockByName("b01");

        TransactionResultDTO dto = new TransactionResultDTO(
                block, 0, tx, false, world.getBlockTxSignatureCache());

        assertEquals("0x4", dto.getType());
        assertNotNull(dto.getGasPrice(),
                "DTO exposes effective gas price via gasPrice until Type 4-specific RPC fields are added");
    }

    // =========================================================================
    // Mixed block: legacy + Type 4
    // =========================================================================

    @Test
    void mixedBlock_containsLegacyAndType4() {
        Block b06 = world.getBlockByName("b06");

        assertEquals(2, b06.getTransactionsList().size());
        assertEquals(TransactionType.LEGACY, b06.getTransactionsList().get(0).getType());
        assertEquals(TransactionType.TYPE_4, b06.getTransactionsList().get(1).getType());
    }

    static Stream<Arguments> mixedReceiptPrefixCases() {
        return Stream.of(
                Arguments.of("txMixedLegacy", -1),
                Arguments.of("txMixedType4", 0x04)
        );
    }

    @ParameterizedTest
    @MethodSource("mixedReceiptPrefixCases")
    void mixedBlock_receiptHasCorrectPrefix(String txName, int expectedFirstByteOrLegacy) {
        byte[] encoded = world.getTransactionReceiptByName(txName).getEncoded();
        if (expectedFirstByteOrLegacy < 0) {
            assertTrue((encoded[0] & 0xFF) >= 0xc0, "Legacy receipt must start with RLP list marker");
        } else {
            assertEquals((byte) expectedFirstByteOrLegacy, encoded[0]);
        }
    }

    // =========================================================================
    // Block encode/decode
    // =========================================================================

    @Test
    void type4Block_survivesEncodeDecode_preservesTypeAndHash() {
        Block original = world.getBlockByName("b01");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        Transaction decodedTx = decoded.getTransactionsList().get(0);
        assertEquals(TransactionType.TYPE_4, decodedTx.getType());
        assertEquals(original.getTransactionsList().get(0).getHash(), decodedTx.getHash());
        assertEquals(1, decodedTx.getAuthorizationList().size());
    }

    @Test
    void mixedBlock_survivesEncodeDecode_preservesAllTypes() {
        Block original = world.getBlockByName("b06");
        BlockFactory blockFactory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = blockFactory.decodeBlock(original.getEncoded());

        assertEquals(TransactionType.LEGACY, decoded.getTransactionsList().get(0).getType());
        assertEquals(TransactionType.TYPE_4, decoded.getTransactionsList().get(1).getType());
    }

    // =========================================================================
    // Activation guard
    // =========================================================================

    @Test
    void type4_blockedWhenRskip545Inactive() {
        Transaction tx = world.getTransactionByName("txType4SelfAuth");
        var activations = world.getConfig().getActivationConfig().forBlock(1);
        var without545 = org.mockito.Mockito.mock(
                org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock.class);
        org.mockito.Mockito.when(without545.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        org.mockito.Mockito.when(without545.isActive(ConsensusRule.RSKIP546)).thenReturn(true);
        org.mockito.Mockito.when(without545.isActive(ConsensusRule.RSKIP545)).thenReturn(false);

        assertTrue(tx.isTypedTransactionNotAllowed(without545),
                "Type 4 must be rejected when RSKIP-545 is inactive");
        assertFalse(tx.isTypedTransactionNotAllowed(activations),
                "Type 4 must be allowed when RSKIP-545 is active in this world");
    }

    // =========================================================================
    // Chain height sanity
    // =========================================================================

    @Test
    void blockchain_reachesExpectedHeight() {
        assertEquals(7, world.getBlockChain().getBestBlock().getNumber());
    }

    @Test
    void bestBlock_isB07() {
        assertEquals(world.getBlockByName("b07"), world.getBlockChain().getBestBlock());
    }

    private static String blockForTransaction(String txName) {
        return switch (txName) {
            case "txType4SelfAuth" -> "b01";
            case "txType4WithValue" -> "b02";
            case "txType4ChainId0" -> "b03";
            case "txType4ExternalAuth" -> "b04";
            case "txType4Receipt" -> "b05";
            case "txType4Revoke" -> "b07";
            case "txMixedType4" -> "b06";
            default -> throw new IllegalArgumentException("Unknown tx: " + txName);
        };
    }

    private static Coin balanceAtBlock(String blockName, Account account) {
        Block block = world.getBlockByName(blockName);
        return world.getRepositoryLocator().snapshotAt(block.getHeader())
                .getBalance(account.getAddress());
    }

    private static Coin balanceAtBlockParent(String blockName, Account account) {
        Block block = world.getBlockByName(blockName);
        Block parent = world.getBlockByName(blockName).getNumber() == 1
                ? world.getBlockChain().getBlockByNumber(0)
                : world.getBlockByHash(block.getParentHash());
        return world.getRepositoryLocator().snapshotAt(parent.getHeader())
                .getBalance(account.getAddress());
    }

    private static long intrinsicGas(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        return tx.transactionCost(
                world.getConfig().getNetworkConstants(),
                world.getConfig().getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());
    }

    private static long gasUsedInBlock(String blockName, String txName) {
        Block block = world.getBlockByName(blockName);
        Transaction target = world.getTransactionByName(txName);
        long prevCumulative = 0;
        for (Transaction tx : block.getTransactionsList()) {
            TransactionInfo info = world.getReceiptStore()
                    .get(tx.getHash().getBytes(), block.getHash().getBytes())
                    .orElseThrow(() -> new IllegalStateException("Missing receipt for " + tx.getHash()));
            long cumulative = info.getReceipt().getCumulativeGasLong();
            if (tx.getHash().equals(target.getHash())) {
                return cumulative - prevCumulative;
            }
            prevCumulative = cumulative;
        }
        throw new IllegalStateException("Transaction " + txName + " not found in block " + blockName);
    }
}
