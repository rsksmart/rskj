/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;

import co.rsk.bitcoinj.core.AddressFormatException;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.trie.TrieStore;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RskForksBridgeTest {
    private static final ActivationConfig.ForBlock ACTIVATIONS_ALL = ActivationConfigsForTest.all().forBlock(0L);

    private static final ECKey fedECPrivateKey = ECKey.fromPrivate(
        REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKey()
    );

    private RepositoryLocator repositoryLocator;
    private TrieStore trieStore;
    private Repository repository;
    private BlockChainImpl blockChain;
    private Block blockBase;
    private World world;
    private BlockStore blockStore;
    private BridgeSupportFactory bridgeSupportFactory;

    @BeforeEach
    void before() {
        world = new World();
        blockChain = world.getBlockChain();
        blockStore = world.getBlockStore();
        repositoryLocator = world.getRepositoryLocator();
        trieStore = world.getTrieStore();
        repository = world.getRepository();
        bridgeSupportFactory = world.getBridgeSupportFactory();

        BridgeRegTestConstants bridgeRegTestConstants = new BridgeRegTestConstants();

        Genesis genesis = (Genesis) blockChain.getBestBlock();
        co.rsk.core.Coin balance = new co.rsk.core.Coin(new BigInteger("10000000000000000000"));
        repository.addBalance(new RskAddress(fedECPrivateKey.getAddress()), balance);

        co.rsk.core.Coin bridgeBalance = co.rsk.core.Coin.fromBitcoin(bridgeRegTestConstants.getMaxRbtc());
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, bridgeBalance);

        injectGenesisFederationUtxo(repository, bridgeRegTestConstants);

        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        blockBase = buildBlock(genesis);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockBase));
    }

    @Test
    void testNoFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();
        Block blockB1 = buildBlock(blockBase, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB1));
        repository = repositoryLocator.startTrackingAt(blockB1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB2));
        repository = repositoryLocator.startTrackingAt(blockB2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, updateCollectionsTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    @Test
    void testLosingForkBuiltFirst() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, 4L);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockA2 = buildBlock(blockA1, 6L, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB1 = buildBlock(blockBase, 1L, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1, 2L);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, 10L, updateCollectionsTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }


    @Test
    void testWinningForkBuiltFirst() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockB1 = buildBlock(blockBase, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB1));
        repository = repositoryLocator.startTrackingAt(blockB1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1,6L);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB2));
        repository = repositoryLocator.startTrackingAt(blockB2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA1 = buildBlock(blockBase,1);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockA1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA2 = buildBlock(blockA1,1, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockA2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2,12, updateCollectionsTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    @Test
    void testReleaseTxJustInLoosingFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        repository = repositoryLocator.startTrackingAt(blockA1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA2 = buildBlock(blockA1,3);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB1 = buildBlock(blockBase,2);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1,1);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2,6L, updateCollectionsTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);
    }

    @Test
    void testReleaseTxJustInWinningFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, 4L);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        repository = repositoryLocator.startTrackingAt(blockA1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockA2 = buildBlock(blockA1, 5L);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockB1 = buildBlock(blockBase, 1L, releaseTx);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockB2 = buildBlock(blockB1, 1L);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, 10L, updateCollectionsTx);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    private Block buildBlock(Block parent, long difficulty) {
        BlockBuilder blockBuilder = new BlockBuilder(blockChain, bridgeSupportFactory, blockStore).trieStore(trieStore).difficulty(difficulty).parent(parent);
        return blockBuilder.build();
    }

    private Block buildBlock(Block parent, Transaction ... txs) {
        return buildBlock(parent, parent.getDifficulty().asBigInteger().longValue(), txs);
    }

    private Block buildBlock(Block parent, long difficulty, Transaction ... txs) {
        List<Transaction> txList = Arrays.asList(txs);
        BlockBuilder blockBuilder = new BlockBuilder(blockChain, bridgeSupportFactory, blockStore).trieStore(trieStore).difficulty(difficulty).parent(parent).transactions(txList).uncles(new ArrayList<>());
        return blockBuilder.build();
    }

    private void injectGenesisFederationUtxo(Repository repo, BridgeRegTestConstants bridgeRegTestConstants) {
        NetworkParameters btcParams = bridgeRegTestConstants.getBtcParams();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        FederationConstants federationConstants = bridgeRegTestConstants.getFederationConstants();
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        Script outputScript = ScriptBuilder.createOutputScript(genesisFederation.getAddress());
        UTXO utxo = new UTXO(
            Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
            0,
            Coin.valueOf(3, 0),
            0,
            false,
            outputScript
        );

        FederationStorageProviderImpl federationStorageProvider = new FederationStorageProviderImpl(
            new BridgeStorageAccessorImpl(repo)
        );
        federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activations).add(utxo);
        federationStorageProvider.save(btcParams, activations);
    }

    private Transaction buildReleaseTx() throws AddressFormatException {
        long nonce = 0;
        long value = 1000000000000000000L;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(100000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.RELEASE_BTC, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;
    }

    private Transaction buildUpdateCollectionsTx() {
        long nonce = 1;
        long value = 0;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(100000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.UPDATE_COLLECTIONS, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;
    }


    private void assertReleaseTransactionState(ReleaseTransactionState state) throws IOException {
        BridgeState stateForDebugging = callGetStateForDebuggingTx();
        if (ReleaseTransactionState.WAITING_FOR_SELECTION.equals(state)) {
            Assertions.assertEquals(1, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assertions.assertEquals(0, stateForDebugging.getPegoutsWaitingForConfirmations().getEntries(ACTIVATIONS_ALL).size());
            Assertions.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.WAITING_FOR_SIGNATURES.equals(state)) {
            Assertions.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assertions.assertEquals(0, stateForDebugging.getPegoutsWaitingForConfirmations().getEntries(ACTIVATIONS_ALL).size());
            Assertions.assertEquals(1, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS.equals(state)) {
            Assertions.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assertions.assertEquals(1, stateForDebugging.getPegoutsWaitingForConfirmations().getEntries(ACTIVATIONS_ALL).size());
            Assertions.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.NO_TX.equals(state)) {
            Assertions.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assertions.assertEquals(0, stateForDebugging.getPegoutsWaitingForConfirmations().getEntries(ACTIVATIONS_ALL).size());
            Assertions.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        }
    }

    private enum ReleaseTransactionState {
        NO_TX, WAITING_FOR_SIGNATURES, WAITING_FOR_SELECTION, WAITING_FOR_CONFIRMATIONS
    }

    private BridgeState callGetStateForDebuggingTx() throws IOException {
        TestSystemProperties beforeBambooProperties = new TestSystemProperties();
        Transaction rskTx = CallTransaction.createRawTransaction(0,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.GET_STATE_FOR_DEBUGGING.encode(), beforeBambooProperties.getNetworkConstants().getChainId());
        rskTx.sign(new byte[]{});

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                beforeBambooProperties,
                blockStore,
                null,
                new BlockFactory(beforeBambooProperties.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(beforeBambooProperties, world.getBridgeSupportFactory(), world.getBlockTxSignatureCache()),
                world.getBlockTxSignatureCache()
        );
        Repository track = repository.startTracking();
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(rskTx, 0, blockChain.getBestBlock().getCoinbase(), track, blockChain.getBestBlock(), 0)
                .setLocalCall(true);

        executor.executeTransaction();

        ProgramResult res = executor.getResult();

        Object[] result = Bridge.GET_STATE_FOR_DEBUGGING.decodeResult(res.getHReturn());

        ActivationConfig.ForBlock activations = beforeBambooProperties.getActivationConfig().forBlock(blockChain.getBestBlock().getNumber());
        return BridgeState.create(beforeBambooProperties.getNetworkConstants().getBridgeConstants(), (byte[])result[0], activations);
    }
}
