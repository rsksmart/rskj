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
import org.ethereum.core.*;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.ethereum.crypto.HashUtil.calcNewAddr;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class PrecompiledContractExceptionActivationTest {

    /**
     * The data 0xdeadbeef matches no Bridge method, so the Bridge declares 23_000 gas and
     * then fails. Intrinsic cost = 21_000 + 4 non-zero data bytes * 16 = 21_064.
     * Before activation: status 1, gasUsed 23_000 + 21_064 = 44_064, fee 200_000 (the full gas limit).
     * After activation: status 0, gasUsed 200_000, fee 200_000.
     */
    // RSKIP-692 test case 1
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void bridgeCallThatThrowsBehavesPerActivation(boolean rskip692Active) {
        TestSystemProperties config = configWith(GenesisActivations.withRule(new TestSystemProperties().getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active));
        TransactionExecutorFactory transactionExecutorFactory = newTransactionExecutorFactory(config);

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        track.commit();

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(200_000L))
                .destination(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(Hex.decode("deadbeef"))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block block = childOfGenesis(config, track, tx);
        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertNotNull(executor.getResult().getException());

        TransactionReceipt receipt = executor.getReceipt();
        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());

        Assertions.assertEquals(Coin.valueOf(200_000L), executor.getPaidFees(), "the fee is the full gas limit in both cases");
        if (rskip692Active) {
            Assertions.assertFalse(receipt.isSuccessful());
            Assertions.assertEquals(BigInteger.valueOf(200_000L), reportedGasUsed);
        } else {
            Assertions.assertTrue(receipt.isSuccessful(), "pre-activation, the legacy SUCCESS status must be preserved");
            Assertions.assertEquals(BigInteger.valueOf(44_064L), reportedGasUsed);
        }
    }

    /**
     * A transaction without an authorization list sends 0xdeadbeef to the Bridge with gas
     * limit 40_000. It covers the intrinsic cost of 21_000 + 4 * 16 = 21_064, but not 21_064 + 23_000 = 44_064,
     * so the Bridge does not execute. Before and after activation: status 0, gasUsed = fee = 40_000, no state
     * change and no log.
     */
    // RSKIP-692 test case 8
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void directCallWithInsufficientGasChargesGasLimitRegardlessOfActivation(boolean rskip692Active) {
        TestSystemProperties config = configWith(GenesisActivations.withRule(new TestSystemProperties().getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active));
        TransactionExecutorFactory transactionExecutorFactory = newTransactionExecutorFactory(config);

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        track.commit();

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(40_000L))
                .destination(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(Hex.decode("deadbeef"))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block block = childOfGenesis(config, track, tx);
        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());

        TransactionReceipt receipt = executor.getReceipt();
        Assertions.assertFalse(receipt.isSuccessful());
        Assertions.assertEquals(BigInteger.valueOf(40_000L), new BigInteger(1, receipt.getGasUsed()));
        Assertions.assertEquals(Coin.valueOf(40_000L), executor.getPaidFees());
        Assertions.assertEquals(Coin.valueOf(6_000_000L - 40_000L), track.getBalance(sender.getAddress()));
        Assertions.assertEquals(BigInteger.ONE, track.getNonce(sender.getAddress()));
        Assertions.assertTrue(receipt.getLogInfoList().isEmpty());
        Assertions.assertFalse(track.isExist(PrecompiledContracts.BRIDGE_ADDR));
    }

    // RSKIP-692 test case 3
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidOpcodeChargesFullGasLimitRegardlessOfActivation(boolean rskip692Active) {
        final byte[] INVALID_OPCODE_CODE = Hex.decode("fe");

        TestSystemProperties baseConfig = new TestSystemProperties();
        ActivationConfig activationConfig = GenesisActivations.withRule(baseConfig.getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active);

        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), blockTxSignatureCache);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, blockTxSignatureCache),
                blockTxSignatureCache
        );

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        RskAddress contractAddress = new RskAddress(new co.rsk.core.RskAddress(calcNewAddr(sender.getAddress().getBytes(), BigInteger.ZERO.toByteArray())).getBytes());
        track.createAccount(contractAddress);
        track.saveCode(contractAddress, INVALID_OPCODE_CODE);
        track.commit();

        long gasPrice = 1L;
        BigInteger gasLimit = BigInteger.valueOf(100_000L);

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .gasLimit(gasLimit)
                .destination(contractAddress)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(new byte[0])
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), new ArrayList<>(), 1, null);

        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertNotNull(executor.getResult().getException());

        TransactionReceipt receipt = executor.getReceipt();
        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());
        Coin expectedFullFee = Coin.valueOf(gasLimit.longValueExact() * gasPrice);

        Assertions.assertFalse(receipt.isSuccessful());
        Assertions.assertEquals(gasLimit, reportedGasUsed);
        Assertions.assertEquals(expectedFullFee, executor.getPaidFees());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void outOfGasLoopChargesFullGasLimitRegardlessOfActivation(boolean rskip692Active) {
        // JUMPDEST; PUSH1 0x00; JUMP -- an infinite loop back to itself.
        // Consumes gas every iteration until the tx's budget runs out, producing
        // a genuine OutOfGasException rather than an invalid-opcode exception.
        final byte[] INFINITE_LOOP_CODE = Hex.decode("5b600056");

        TestSystemProperties baseConfig = new TestSystemProperties();
        ActivationConfig activationConfig = GenesisActivations.withRule(baseConfig.getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active);

        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), blockTxSignatureCache);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, blockTxSignatureCache),
                blockTxSignatureCache
        );

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        RskAddress contractAddress = new RskAddress(calcNewAddr(sender.getAddress().getBytes(), BigInteger.ZERO.toByteArray()));
        track.createAccount(contractAddress);
        track.saveCode(contractAddress, INFINITE_LOOP_CODE);
        track.commit();

        long gasPrice = 1L;
        BigInteger gasLimit = BigInteger.valueOf(100_000L); // enough to cover basicTxCost, not enough to loop forever

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .gasLimit(gasLimit)
                .destination(contractAddress)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(new byte[0])
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), new ArrayList<>(), 1, null);

        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertNotNull(executor.getResult().getException());
        Assertions.assertTrue(
                executor.getResult().getException() instanceof Program.OutOfGasException
                        || executor.getResult().getException().getMessage() != null && executor.getResult().getException().getMessage().toLowerCase().contains("gas"),
                "expected a genuine out-of-gas failure, got: " + executor.getResult().getException()
        );

        TransactionReceipt receipt = executor.getReceipt();
        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());
        Coin expectedFullFee = Coin.valueOf(gasLimit.longValueExact() * gasPrice);

        // Identical regardless of RSKIP692: the activation does not change an exceptional halt in the EVM.
        Assertions.assertFalse(receipt.isSuccessful());
        Assertions.assertEquals(gasLimit, reportedGasUsed);
        Assertions.assertEquals(expectedFullFee, executor.getPaidFees());
    }

    /**
     * The contract code is PUSH1 0x00 PUSH1 0x00 REVERT (0x60006000fd).
     * Gas by hand: intrinsic 21_000 (no data) + PUSH1 3 + PUSH1 3 + REVERT 0 (zero-size memory, no expansion) = 21_006.
     * REVERT returns the unused gas, so gasUsed and fee are 21_006 whatever the activation state.
     */
    // RSKIP-692 test case 4
    @ParameterizedTest(name = "rskip692Active={0}")
    @ValueSource(booleans = {true, false})
    void revertReturnsUnusedGasRegardlessOfActivation(boolean rskip692Active) {
        TestSystemProperties config = configWith(GenesisActivations.withRule(new TestSystemProperties().getActivationConfig(), ConsensusRule.RSKIP692, rskip692Active));
        TransactionExecutorFactory transactionExecutorFactory = newTransactionExecutorFactory(config);

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        RskAddress contractAddress = new RskAddress(calcNewAddr(sender.getAddress().getBytes(), BigInteger.ZERO.toByteArray()));
        track.createAccount(contractAddress);
        track.saveCode(contractAddress, Hex.decode("60006000fd"));
        track.commit();

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(100_000L))
                .destination(contractAddress)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(new byte[0])
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block block = childOfGenesis(config, track, tx);
        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertTrue(executor.getResult().isRevert());

        TransactionReceipt receipt = executor.getReceipt();
        Assertions.assertFalse(receipt.isSuccessful());
        Assertions.assertEquals(BigInteger.valueOf(21_006L), new BigInteger(1, receipt.getGasUsed()));
        Assertions.assertEquals(Coin.valueOf(21_006L), executor.getPaidFees());
    }

    private static TestSystemProperties configWith(ActivationConfig activationConfig) {
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

    private static Block childOfGenesis(TestSystemProperties config, Repository track, Transaction tx) {
        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        return blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), new ArrayList<>(), 1, null);
    }
}
