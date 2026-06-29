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
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep RSKIP-545 DSL integration tests mapped to the specification sections in
 * <a href="https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP545.md">RSKIP-545</a>.
 *
 */
class Rskip545DeepDslTest {

    private static final RskAddress DELEGATE_03 =
            new RskAddress("0x0000000000000000000000000000000000000003");
    private static final RskAddress DELEGATE_04 =
            new RskAddress("0x0000000000000000000000000000000000000004");
    private static final RskAddress DELEGATE_05 =
            new RskAddress("0x0000000000000000000000000000000000000005");
    private static final RskAddress PRECOMPILE_01 =
            new RskAddress("0x0000000000000000000000000000000000000001");

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

        DslParser parser = DslParser.fromResource("dsl/transaction/rskip545/rskip545DeepTest.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    // =========================================================================
    // Behavior: multiple tuples, same authority — last valid wins
    // =========================================================================

    @Test
    void multiAuthLastWins_persistsLastDelegateAddress() {
        Account sponsor = world.getAccountByName("sponsor");
        RepositorySnapshot repo = repoAt("b01");

        byte[] code = repo.getCode(sponsor.getAddress());
        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(DELEGATE_04, DelegationCodeResolver.extractDelegatedAddress(code),
                "Last valid tuple must win when multiple apply to the same authority");
    }

    @Test
    void multiAuthLastWins_incrementsAuthorityNonceTwice() {
        Account sponsor = world.getAccountByName("sponsor");
        assertEquals(BigInteger.valueOf(3), repoAt("b01").getNonce(sponsor.getAddress()),
                "Two successful tuples increment authority nonce twice after sender increment");
    }

    @Test
    void multiAuthLastWins_carriesTwoAuthorizationTuples() {
        Transaction tx = world.getTransactionByName("txMultiAuthLastWins");
        assertEquals(2, tx.getAuthorizationList().size());
    }

    // =========================================================================
    // Behavior: invalid tuple skipped, valid tuple still applied
    // =========================================================================

    @Test
    void validInvalidAuth_appliesValidTupleDespiteInvalid() {
        Account sponsor = world.getAccountByName("sponsor");
        byte[] code = repoAt("b02").getCode(sponsor.getAddress());

        assertEquals(DELEGATE_03, DelegationCodeResolver.extractDelegatedAddress(code));
        assertEquals(BigInteger.valueOf(5), repoAt("b02").getNonce(sponsor.getAddress()));
    }

    @Test
    void validInvalidAuth_chargesUpfrontForBothTuplesRegardlessOfValidity() {
        assertEquals(GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST,
                intrinsicGas("txValidInvalidAuth"),
                "Intrinsic gas charges PER_EMPTY_ACCOUNT_COST per tuple even if one fails");
        assertEquals(GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST,
                intrinsicGas("txMultiAuthLastWins"));
    }

    // =========================================================================
    // Behavior: dual authority batching
    // =========================================================================

    @Test
    void dualAuthority_delegatesBothAccounts() {
        Account sponsor = world.getAccountByName("sponsor");
        Account acc3 = world.getAccountByName("acc3");
        RepositorySnapshot repo = repoAt("b03");

        assertTrue(DelegationCodeResolver.isDelegatedCode(repo.getCode(sponsor.getAddress())));
        assertTrue(DelegationCodeResolver.isDelegatedCode(repo.getCode(acc3.getAddress())));
        assertEquals(DELEGATE_03, DelegationCodeResolver.extractDelegatedAddress(
                repo.getCode(acc3.getAddress())));
    }

    @Test
    void dualAuthority_incrementsBothAuthorityNonces() {
        assertEquals(BigInteger.valueOf(7), repoAt("b03").getNonce(
                world.getAccountByName("sponsor").getAddress()));
        assertEquals(BigInteger.ONE, repoAt("b03").getNonce(
                world.getAccountByName("acc3").getAddress()));
    }

    // =========================================================================
    // No precompiles: delegate target is treated as empty code
    // =========================================================================

    @Test
    void precompileDelegate_writesIndicatorToPrecompileAddress() {
        Account acc3 = world.getAccountByName("acc3");
        byte[] code = repoAt("b04").getCode(acc3.getAddress());

        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(PRECOMPILE_01, DelegationCodeResolver.extractDelegatedAddress(code));
    }

    @Test
    void precompileDelegate_callSucceedsWithoutExecutingPrecompile() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPrecompileDelegate");
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus(),
                "CALL to account delegated to precompile must succeed with empty execution");
    }

    // =========================================================================
    // Execution phase after authorization
    // =========================================================================

    @Test
    void valueTransfer_creditsReceiverAfterAuthorization() {
        assertEquals(Coin.valueOf(5000), repoAt("b05").getBalance(
                world.getAccountByName("acc2").getAddress()));
    }

    // =========================================================================
    // Gas: calldata intrinsic cost
    // =========================================================================

    @Test
    void withCalldata_intrinsicGasExceedsEmptyPayload() {
        Transaction empty = world.getTransactionByName("txValueTransfer");
        Transaction withData = world.getTransactionByName("txWithCalldata");

        long emptyIntrinsic = empty.transactionCost(
                world.getConfig().getNetworkConstants(),
                world.getConfig().getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());
        long dataIntrinsic = withData.transactionCost(
                world.getConfig().getNetworkConstants(),
                world.getConfig().getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());

        assertTrue(dataIntrinsic > emptyIntrinsic,
                "Non-zero calldata must increase Type 4 intrinsic gas");
        assertEquals(4, withData.getData().length, "deadbeef = 4 bytes");
    }

    @Test
    void withCalldata_executedGasUsedWithinLimit() {
        long used = gasUsedInBlock("b06", "txWithCalldata");
        assertTrue(used > 0);
        assertTrue(used <= world.getTransactionByName("txWithCalldata").getGasLimitAsInteger().longValue());
    }

    // =========================================================================
    // Spec invariants (unit-level checks on isolated world)
    // =========================================================================

    @Test
    void delegationIndicator_is23Bytes() {
        byte[] code = DelegationCodeResolver.createDelegatedCode(DELEGATE_03);
        assertEquals(23, code.length);
        assertArrayEquals(DelegationCodeResolver.DELEGATION_PREFIX,
                new byte[]{code[0], code[1], code[2]});
    }

    @Test
    void authorizationSigningHash_usesMagicPrefix() {
        ECKey key = new ECKey();
        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                key, DELEGATE_03, BigInteger.ZERO, Constants.REGTEST_CHAIN_ID);

        byte[] expectedPayload = ByteUtil.merge(
                new byte[]{0x05},
                RLP.encodeList(
                        RLP.encodeBigInteger(BigInteger.valueOf(Constants.REGTEST_CHAIN_ID)),
                        RLP.encodeElement(DELEGATE_03.getBytes()),
                        RLP.encodeElement(BigInteger.ZERO.toByteArray())
                )
        );
        assertArrayEquals(HashUtil.keccak256(expectedPayload), auth.getSigningHash());
    }

    @Test
    void builder_rejectsEmptyAuthorizationList() {
        assertThrows(Exception.class, () -> Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(Constants.REGTEST_CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(100_000))
                .maxPriorityFeePerGas(Coin.valueOf(1_000_000_000L))
                .maxFeePerGas(Coin.valueOf(2_000_000_000L))
                .receiveAddress(DELEGATE_03)
                .value(Coin.ZERO)
                .data(new byte[0])
                .authorizationList(List.of())
                .build());
    }

    @Test
    void parser_rejectsNullDestination() {
        byte[][] fields = new byte[][]{
                RLP.encodeByte(Constants.REGTEST_CHAIN_ID),
                RLP.encodeElement(BigInteger.ZERO.toByteArray()),
                RLP.encodeCoinNonNullZero(Coin.valueOf(1_000_000_000L)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(2_000_000_000L)),
                RLP.encodeElement(BigInteger.valueOf(100_000).toByteArray()),
                RLP.encodeRskAddress(RskAddress.nullAddress()),
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeElement(new byte[0]),
                RLP.encodeList(),
                Rskip545TestSupport.defaultAuthListBytes(),
                RLP.encodeElement(null),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        };
        byte[] raw = Rskip545TestSupport.buildRawType4Bytes(fields);
        assertThrows(Exception.class, () -> Transaction.fromRaw(raw));
    }

    @Test
    void chainedDelegation_resolvesOnlyFirstHop() {
        World isolated = new World(world.getConfig());

        Account impl = new AccountBuilder(isolated).name("impl")
                .code(Hex.decode("60006000f3")).build();
        byte[] chainCode = DelegationCodeResolver.createDelegatedCode(impl.getAddress());
        Account chain = new AccountBuilder(isolated).name("chain").code(chainCode).build();
        byte[] nestedCode = DelegationCodeResolver.createDelegatedCode(chain.getAddress());
        Account nested = new AccountBuilder(isolated).name("nested").code(nestedCode).build();

        RepositorySnapshot repo = isolated.getRepositoryLocator()
                .snapshotAt(isolated.getBlockChain().getBestBlock().getHeader());

        byte[] resolved = repo.getCode(nested.getAddress());
        assertTrue(DelegationCodeResolver.isDelegatedCode(resolved));
        assertEquals(chain.getAddress(), DelegationCodeResolver.extractDelegatedAddress(resolved));
        assertNotEquals(impl.getAddress(), DelegationCodeResolver.extractDelegatedAddress(resolved),
                "Must not follow a second delegation hop at code-read time");
    }

    @Test
    void authRejectedWhenAuthorityHasContractCode() {
        SetCodeAuthorizationTransactionExecutor executor = new SetCodeAuthorizationTransactionExecutor();
        ECKey contractKey = new ECKey();
        RskAddress contractAuthority = new RskAddress(contractKey.getAddress());
        byte[] contractBytecode = Hex.decode("60006000f3");

        World isolated = new World(world.getConfig());
        RepositorySnapshot base = isolated.getRepositoryLocator()
                .snapshotAt(isolated.getBlockChain().getBestBlock().getHeader());
        var track = base.startTracking();
        track.createAccount(contractAuthority);
        track.saveCode(contractAuthority, contractBytecode);
        track.setNonce(contractAuthority, BigInteger.ZERO);
        track.commit();

        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                contractKey, DELEGATE_03, BigInteger.ZERO, Constants.REGTEST_CHAIN_ID);

        assertThrows(IllegalStateException.class, () ->
                executor.processAuthorizationTuple(
                        track, BigInteger.valueOf(Constants.REGTEST_CHAIN_ID), auth),
                "Behavior step 4: authority with non-delegated contract code must fail");
    }

    @Test
    void delegatedAccount_canOriginateType4Transaction() {
        Account acc3 = world.getAccountByName("acc3");
        Transaction tx = world.getTransactionByName("txDelegatedOrigin");
        assertEquals(acc3.getAddress(), tx.getSender(world.getBlockTxSignatureCache()),
                "Delegated EOA (0xef0100||addr) may originate transactions per RSKIP-545");
        assertNotNull(world.getTransactionReceiptByName("txDelegatedOrigin"));
    }

    // =========================================================================
    // Behavior: delegation not rolled back on execution failure
    // =========================================================================

    @Test
    void revertExecution_delegationIndicatorPersists() {
        Account sponsor = world.getAccountByName("sponsor");
        Account revertImpl = world.getAccountByName("revertImpl");
        RepositorySnapshot repo = repoAt("b09");

        byte[] code = repo.getCode(sponsor.getAddress());
        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(revertImpl.getAddress(), DelegationCodeResolver.extractDelegatedAddress(code),
                "Delegation must persist after REVERT even when execution fails");
    }

    @Test
    void revertExecution_receiptStatusIsFailed() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txAuthThenRevert");
        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt.getStatus(),
                "Delegated execution that REVERTs must produce a failed receipt");
    }

    @Test
    void revertExecution_authorityNonceStillIncremented() {
        Account sponsor = world.getAccountByName("sponsor");
        assertEquals(BigInteger.valueOf(16), repoAt("b09").getNonce(sponsor.getAddress()),
                "Successful auth tuple must increment authority nonce even if execution reverts");
    }

    // =========================================================================
    // Gas: triple tuple upfront cost regardless of validity
    // =========================================================================

    @Test
    void tripleAuthTuple_chargesUpfrontForAllThree() {
        assertEquals(GasCost.TRANSACTION + 3 * GasCost.PER_EMPTY_ACCOUNT_COST,
                intrinsicGas("txTripleAuthGas"));
    }

    @Test
    void tripleAuthTuple_appliesOnlyValidTuple() {
        Account sponsor = world.getAccountByName("sponsor");
        byte[] code = repoAt("b08").getCode(sponsor.getAddress());
        assertEquals(DELEGATE_03, DelegationCodeResolver.extractDelegatedAddress(code));
        assertEquals(BigInteger.valueOf(14), repoAt("b08").getNonce(sponsor.getAddress()));
    }

    // =========================================================================
    // Gas: minimum gas limit rejection
    // =========================================================================

    @Test
    void minimumGasLimit_belowIntrinsicCost_rejectedByExecutor() {
        World isolated = new World(world.getConfig());
        Account sender = new AccountBuilder(isolated).name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L)).build();
        Account receiver = new AccountBuilder(isolated).name("receiver").build();

        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                sender.getEcKey(), DELEGATE_03, BigInteger.ONE, Constants.REGTEST_CHAIN_ID);

        Transaction tx = Rskip545TestSupport.unsignedType4WithAuthorizations(
                receiver.getAddress(),
                BigInteger.valueOf(45_999),
                List.of(auth));
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block genesis = isolated.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(isolated.getBlockChain(), null, isolated.getBlockStore())
                .trieStore(isolated.getTrieStore())
                .parent(genesis)
                .transactions(Collections.singletonList(tx))
                .build();

        assertTrue(block.getTransactionsList().isEmpty(),
                "Type 4 tx with gas below intrinsic cost must be dropped during block execution");
    }

    @Test
    void minimumGasLimit_atIntrinsicCost_accepted() {
        long intrinsic = GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST;
        World isolated = new World(world.getConfig());
        Account sender = new AccountBuilder(isolated).name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L)).build();
        Account receiver = new AccountBuilder(isolated).name("receiver").build();

        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                sender.getEcKey(), DELEGATE_03, BigInteger.ONE, Constants.REGTEST_CHAIN_ID);

        Transaction tx = Rskip545TestSupport.unsignedType4WithAuthorizations(
                receiver.getAddress(),
                BigInteger.valueOf(intrinsic),
                List.of(auth));
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block genesis = isolated.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(isolated.getBlockChain(), null, isolated.getBlockStore())
                .trieStore(isolated.getTrieStore())
                .parent(genesis)
                .transactions(Collections.singletonList(tx))
                .build();

        assertEquals(1, block.getTransactionsList().size(),
                "Type 4 tx with gas equal to intrinsic cost must be included");
    }

    @Test
    void activation_requiresRskip543And546And545() {
        Transaction tx = world.getTransactionByName("txMultiAuthLastWins");
        var allInactive = org.mockito.Mockito.mock(
                org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock.class);
        org.mockito.Mockito.when(allInactive.isActive(ConsensusRule.RSKIP543)).thenReturn(false);
        assertTrue(tx.isTypedTransactionNotAllowed(allInactive));

        var only545 = org.mockito.Mockito.mock(
                org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock.class);
        org.mockito.Mockito.when(only545.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        org.mockito.Mockito.when(only545.isActive(ConsensusRule.RSKIP546)).thenReturn(true);
        org.mockito.Mockito.when(only545.isActive(ConsensusRule.RSKIP545)).thenReturn(false);
        assertTrue(tx.isTypedTransactionNotAllowed(only545));
    }

    // =========================================================================
    // Duplicate authorization tuples
    // =========================================================================

    @Test
    void duplicateAuthTuple_carriesTwoIdenticalAuthorizations() {
        Transaction tx = world.getTransactionByName("txDuplicateAuth");
        assertEquals(2, tx.getAuthorizationList().size());
        SetCodeAuthorization first = tx.getAuthorizationList().get(0);
        SetCodeAuthorization second = tx.getAuthorizationList().get(1);
        assertEquals(first.getChainId(), second.getChainId());
        assertEquals(first.getAddress(), second.getAddress());
        assertArrayEquals(first.getNonce(), second.getNonce());
        assertEquals(first.getSignature().getR(), second.getSignature().getR());
        assertEquals(first.getSignature().getS(), second.getSignature().getS());
    }

    @Test
    void duplicateAuthTuple_appliesOnlyFirstTuple() {
        Account sponsor = world.getAccountByName("sponsor");
        byte[] code = repoAt("b10").getCode(sponsor.getAddress());
        assertEquals(DELEGATE_03, DelegationCodeResolver.extractDelegatedAddress(code),
                "Duplicate tuples must apply only the first successful authorization");
        assertEquals(BigInteger.valueOf(18), repoAt("b10").getNonce(sponsor.getAddress()),
                "Only the first tuple increments authority nonce");
    }

    @Test
    void duplicateAuthTuple_chargesUpfrontGasForBothTuples() {
        assertEquals(GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST,
                intrinsicGas("txDuplicateAuth"),
                "Duplicate tuples still incur PER_EMPTY_ACCOUNT_COST each");
    }

    @Test
    void duplicateAuthTuple_outerTransactionSucceeds() {
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txDuplicateAuth").getStatus());
    }

    // =========================================================================
    // Invalid-first ordering
    // =========================================================================

    @Test
    void invalidFirstAuth_appliesSecondValidTuple() {
        Account sponsor = world.getAccountByName("sponsor");
        byte[] code = repoAt("b11").getCode(sponsor.getAddress());
        assertEquals(DELEGATE_05, DelegationCodeResolver.extractDelegatedAddress(code),
                "Second valid tuple must apply when the first is invalid");
        assertEquals(BigInteger.valueOf(20), repoAt("b11").getNonce(sponsor.getAddress()));
    }

    @Test
    void invalidFirstAuth_chargesUpfrontGasForBothTuples() {
        assertEquals(GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST,
                intrinsicGas("txInvalidFirstAuth"));
    }

    @Test
    void invalidFirstAuth_outerTransactionSucceeds() {
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txInvalidFirstAuth").getStatus());
    }

    // =========================================================================
    // Universal chain ID (0)
    // =========================================================================

    @Test
    void chainIdZeroAuth_tupleUsesZeroChainId() {
        Transaction tx = world.getTransactionByName("txChainIdZeroAuth");
        assertEquals(BigInteger.ZERO, tx.getAuthorizationList().get(0).getChainId(),
                "Authorization tuple must carry chain ID 0 (universal)");
    }

    @Test
    void chainIdZeroAuth_delegationAppliedSuccessfully() {
        Account sponsor = world.getAccountByName("sponsor");
        byte[] code = repoAt("b12").getCode(sponsor.getAddress());
        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(DELEGATE_03, DelegationCodeResolver.extractDelegatedAddress(code));
        assertEquals(BigInteger.valueOf(22), repoAt("b12").getNonce(sponsor.getAddress()));
    }

    @Test
    void chainIdZeroAuth_outerTransactionSucceeds() {
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txChainIdZeroAuth").getStatus());
    }

    @Test
    void blockchain_reachesExpectedHeight() {
        assertEquals(12, world.getBlockChain().getBestBlock().getNumber());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RepositorySnapshot repoAt(String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
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
                    .orElseThrow();
            long cumulative = info.getReceipt().getCumulativeGasLong();
            if (tx.getHash().equals(target.getHash())) {
                return cumulative - prevCumulative;
            }
            prevCumulative = cumulative;
        }
        throw new IllegalStateException("Tx not in block: " + txName);
    }
}
