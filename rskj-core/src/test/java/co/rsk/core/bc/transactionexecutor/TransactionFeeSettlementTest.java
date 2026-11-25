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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.ethereum.crypto.HashUtil.calcNewAddr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Pins how a transaction settles its gas: what the sender gets back, what REMASC is credited and what the executor
 * reports as paid fees. Every expected value is the gas used, worked out by hand from the gas schedule, times the gas
 * price. The sender always ends with its initial balance minus the fee, and REMASC always gains the fee.
 */
class TransactionFeeSettlementTest {

    private static final long GAS_PRICE = 2L;
    private static final long INITIAL_BALANCE = 10_000_000L;
    private static final byte[] NO_DATA = new byte[0];

    /**
     * The code is PUSH1 0 PUSH1 0 SSTORE PUSH1 1 PUSH1 1 SSTORE STOP, run against slot 0 = 1.
     * Gas: intrinsic 21_000 + PUSH1 3 * 4 + clear SSTORE 5_000 + set SSTORE 20_000 = 46_012.
     * Refund: the clear earns 15_000, below the cap of 46_012 / 2 = 23_006, so gas used is 46_012 - 15_000 = 31_012.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void successWithRefundBelowTheCapReturnsTheRefund(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, "6000600055600160015500", true, 100_000L);

        assertNull(settlement.exception);
        settlement.assertCharged(31_012L);
    }

    /**
     * The code is PUSH1 0 PUSH1 0 SSTORE STOP, run against slot 0 = 1.
     * Gas: intrinsic 21_000 + PUSH1 3 * 2 + clear SSTORE 5_000 = 26_006.
     * Refund: the clear earns 15_000, capped at 26_006 / 2 = 13_003, so gas used is 26_006 - 13_003 = 13_003.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void successWithRefundAboveTheCapReturnsTheCappedRefund(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, "600060005500", true, 100_000L);

        assertNull(settlement.exception);
        settlement.assertCharged(13_003L);
    }

    /**
     * The code is PUSH1 0 PUSH1 0 REVERT. Gas: intrinsic 21_000 + PUSH1 3 * 2 = 21_006, and REVERT returns the rest
     * of the 100_000 limit.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void revertReturnsTheUnusedGas(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, "60006000fd", false, 100_000L);

        assertTrue(settlement.revert);
        settlement.assertCharged(21_006L);
    }

    /**
     * The code is PUSH1 0 PUSH1 0 SSTORE INVALID, run against slot 0 = 1. The clear earns a 15_000 refund before the
     * halt, and the halt discards it, so the whole 100_000 limit is charged.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void exceptionalHaltChargesTheGasLimit(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, "6000600055fe", true, 100_000L);

        assertNotNull(settlement.exception);
        settlement.assertCharged(100_000L);
    }

    /**
     * The data 0xdeadbeef matches no Bridge method, so the Bridge declares 23_000 gas and fails. The intrinsic cost is
     * 21_000 + 4 non-zero bytes * 16 = 21_064, and 21_064 + 23_000 = 44_064 fits in the 200_000 limit. The whole limit is
     * charged before and after activation.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void failingPrecompileWithEnoughGasChargesTheGasLimit(boolean rskip692Active) {
        Settlement settlement = runAgainstBridge(rskip692Active, 200_000L);

        assertNotNull(settlement.exception);
        settlement.assertCharged(200_000L);
    }

    /**
     * The same Bridge call with a 40_000 limit covers the 21_064 intrinsic cost but not 44_064, so the Bridge does not
     * run and the whole limit is charged.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void precompileWithoutEnoughGasChargesTheGasLimit(boolean rskip692Active) {
        Settlement settlement = runAgainstBridge(rskip692Active, 40_000L);

        settlement.assertCharged(40_000L);
    }

    /**
     * The code calls an empty account with no value and then halts on INVALID. The call is recorded as an internal
     * transaction, and the failed transaction marks it rejected.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void failedTransactionRejectsItsInternalTransactions(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, callEmptyAccountThen("fe"), false, 100_000L);

        assertNotNull(settlement.exception);
        assertEquals(1, settlement.internalTransactions.size());
        assertTrue(settlement.internalTransactions.get(0).isRejected());
    }

    /**
     * The same call followed by STOP succeeds, and its internal transaction stays accepted.
     */
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void successfulTransactionKeepsItsInternalTransactions(boolean rskip692Active) {
        Settlement settlement = runAgainstContract(rskip692Active, callEmptyAccountThen("00"), false, 100_000L);

        assertNull(settlement.exception);
        assertEquals(1, settlement.internalTransactions.size());
        assertFalse(settlement.internalTransactions.get(0).isRejected());
    }

    // PUSH1 0 (out size) PUSH1 0 (out offset) PUSH1 0 (in size) PUSH1 0 (in offset) PUSH1 0 (value)
    // PUSH20 0x11..11 GAS CALL POP, then the given ending
    private static String callEmptyAccountThen(String ending) {
        return "6000600060006000600073" + "1111111111111111111111111111111111111111" + "5af150" + ending;
    }

    private static Settlement runAgainstContract(boolean rskip692Active, String code, boolean slotZeroSet, long gasLimit) {
        TestSystemProperties config = configWith(rskip692Active);
        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(INITIAL_BALANCE));
        RskAddress contract = new RskAddress(calcNewAddr(sender.getAddress().getBytes(), BigInteger.ZERO.toByteArray()));
        track.createAccount(contract);
        track.saveCode(contract, Hex.decode(code));
        if (slotZeroSet) {
            track.addStorageRow(contract, DataWord.ZERO, DataWord.ONE);
        }
        track.commit();

        return run(config, track, sender, contract, NO_DATA, gasLimit);
    }

    private static Settlement runAgainstBridge(boolean rskip692Active, long gasLimit) {
        TestSystemProperties config = configWith(rskip692Active);
        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(INITIAL_BALANCE));
        track.commit();

        return run(config, track, sender, PrecompiledContracts.BRIDGE_ADDR, Hex.decode("deadbeef"), gasLimit);
    }

    private static Settlement run(TestSystemProperties config, Repository track, Account sender, RskAddress destination, byte[] data, long gasLimit) {
        assertTrue(config.isRemascEnabled(), "fees are expected to go to REMASC");

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.valueOf(GAS_PRICE))
                .gasLimit(BigInteger.valueOf(gasLimit))
                .destination(destination)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(data)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), new ArrayList<>(), 1, null);

        Coin remascBefore = track.getBalance(PrecompiledContracts.REMASC_ADDR);
        Coin coinbaseBefore = track.getBalance(block.getCoinbase());

        TransactionExecutor executor = newTransactionExecutorFactory(config).newInstance(tx, 0, block.getCoinbase(), track, block, 0L);
        assertTrue(executor.executeTransaction());

        return new Settlement(
                track.getBalance(sender.getAddress()),
                track.getBalance(PrecompiledContracts.REMASC_ADDR).subtract(remascBefore),
                track.getBalance(block.getCoinbase()).subtract(coinbaseBefore),
                executor.getPaidFees(),
                executor.getResult().getException(),
                executor.getResult().isRevert(),
                executor.getResult().getInternalTransactions());
    }

    private static TestSystemProperties configWith(boolean rskip692Active) {
        ActivationConfig activationConfig = GenesisActivations.withRule(new TestSystemProperties().getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active);
        TestSystemProperties config = spy(new TestSystemProperties());
        doReturn(activationConfig).when(config).getActivationConfig();
        return config;
    }

    private static TransactionExecutorFactory newTransactionExecutorFactory(TestSystemProperties config) {
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), blockTxSignatureCache);
        return new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, blockTxSignatureCache),
                blockTxSignatureCache
        );
    }

    private static final class Settlement {
        private final Coin senderBalance;
        private final Coin remascCredit;
        private final Coin coinbaseCredit;
        private final Coin paidFees;
        private final Exception exception;
        private final boolean revert;
        private final List<InternalTransaction> internalTransactions;

        private Settlement(Coin senderBalance, Coin remascCredit, Coin coinbaseCredit, Coin paidFees, Exception exception,
                           boolean revert, List<InternalTransaction> internalTransactions) {
            this.senderBalance = senderBalance;
            this.remascCredit = remascCredit;
            this.coinbaseCredit = coinbaseCredit;
            this.paidFees = paidFees;
            this.exception = exception;
            this.revert = revert;
            this.internalTransactions = internalTransactions;
        }

        private void assertCharged(long gasUsed) {
            Coin fee = Coin.valueOf(gasUsed * GAS_PRICE);
            assertEquals(fee, paidFees, "paid fees");
            assertEquals(Coin.valueOf(INITIAL_BALANCE - gasUsed * GAS_PRICE), senderBalance, "sender balance");
            assertEquals(fee, remascCredit, "REMASC credit");
            assertEquals(Coin.ZERO, coinbaseCredit, "coinbase credit");
        }
    }
}
