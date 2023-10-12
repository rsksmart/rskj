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

package co.rsk.bridge.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.bridge.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

@Disabled
class RegisterBtcTransactionTest extends BridgePerformanceTestCase {
    private BtcBlock blockWithTx;
    private int blockWithTxHeight;
    private BtcTransaction txToLock;
    private PartialMerkleTree pmtOfLockTx;

    @Test
    void registerBtcTransaction() throws VMException {
        activationConfig = ActivationConfigsForTest.all();

        ExecutionStats stats = new ExecutionStats("registerBtcTransaction");
        registerBtcTransaction_lockSuccess(100, stats);
        registerBtcTransaction_alreadyProcessed(100, stats);
        registerBtcTransaction_notEnoughConfirmations(100, stats);
        registerBtcTransaction_peg_in_to_any_address(100, stats);
        registerBtcTransaction_peg_in_to_any_address_exceed_locking_cap(20, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void registerBtcTransaction_lockSuccess(int times, ExecutionStats stats) throws VMException {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                false,
                false,
                false
        );

        executeAndAverage(
                "registerBtcTransaction-lockSuccess",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void registerBtcTransaction_alreadyProcessed(int times, ExecutionStats stats) throws VMException {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                true,
                false,
                false
        );

        executeAndAverage(
                "registerBtcTransaction-alreadyProcessed",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void registerBtcTransaction_notEnoughConfirmations(int times, ExecutionStats stats) throws VMException {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                1,
                false,
                false,
                false
        );

        executeAndAverage(
                "registerBtcTransaction-notEnoughConfirmations",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void registerBtcTransaction_peg_in_to_any_address(int times, ExecutionStats stats) throws VMException {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                false,
                true,
                false
        );

        executeAndAverage(
                "registerBtcTransaction-peg-in-to-any-address",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    try {
                        BridgeStorageProvider provider = new BridgeStorageProvider((Repository) environment.getBenchmarkedRepository(), PrecompiledContracts.BRIDGE_ADDR, constants.getBridgeConstants(), activationConfig.forBlock(0));
                        Optional<Long> height = provider.getHeightIfBtcTxhashIsAlreadyProcessed(txToLock.getHash());
                        Assertions.assertTrue(height.isPresent());

                        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
                    } catch (IOException e) {
                        Assertions.fail();
                    }
                }
        );
    }

    private void registerBtcTransaction_peg_in_to_any_address_exceed_locking_cap(int times, ExecutionStats stats) throws VMException {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                false,
                true,
                true
        );

        executeAndAverage(
                "registerBtcTransaction-peg-in-to-any-address-exceed-locking-cap",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(1, 10),
                stats,
                (environment, executionResult) -> {
                    try {
                        BridgeStorageProvider provider = new BridgeStorageProvider((Repository) environment.getBenchmarkedRepository(), PrecompiledContracts.BRIDGE_ADDR, constants.getBridgeConstants(), activationConfig.forBlock(0));

                        Optional<Long> height = provider.getHeightIfBtcTxhashIsAlreadyProcessed(txToLock.getHash());
                        Assertions.assertTrue(height.isPresent());

                        Assertions.assertTrue(provider.getPegoutsWaitingForConfirmations().getEntries().size() > 0);
                    } catch (IOException e) {
                        Assertions.fail();
                    }
                }
        );
    }


    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{
                        txToLock.bitcoinSerialize(),
                        blockWithTxHeight,
                        pmtOfLockTx.bitcoinSerialize()
                });
    }

    private BridgeStorageProviderInitializer generateInitializerForLock(
            int minBtcBlocks,
            int maxBtcBlocks,
            int numberOfLockConfirmations,
            boolean markAsAlreadyProcessed,
            boolean isVersion1,
            boolean exceedLockingCap
    ) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
            Repository thisRepository = repository.startTracking();
            BtcBlockStore btcBlockStore = btcBlockStoreFactory.newInstance(
                thisRepository,
                bridgeConstants,
                provider,
                null
            );
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            BtcBlock lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            // Sender and amounts
            BtcECKey from = new BtcECKey();
            Address fromAddress = from.toAddress(networkParameters);
            Coin fromAmount;
            if (exceedLockingCap){
                fromAmount = Coin.CENT.multiply(Helper.randomInRange(10000000, 15000000));
            }
            else {
                fromAmount = Coin.CENT.multiply(Helper.randomInRange(1000, 1500));
            }
            Coin changeAmount = Coin.MILLICOIN.multiply(3);
            Coin lockAmount = fromAmount.subtract(changeAmount).subtract(Coin.MILLICOIN); // 1 millicoin fee simulation

            // Input tx
            BtcTransaction inputTx = new BtcTransaction(networkParameters);
            inputTx.addOutput(fromAmount, fromAddress);

            // Lock tx that uses the input tx
            txToLock = new BtcTransaction(networkParameters);
            txToLock.addInput(inputTx.getOutput(0));
            txToLock.addOutput(lockAmount, bridgeConstants.getGenesisFederation().getAddress());
            txToLock.addOutput(changeAmount, fromAddress);

            ECKey ecKey = new ECKey();
            if (isVersion1) {
                Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(ecKey.getAddress()), Optional.empty());
                txToLock.addOutput(Coin.ZERO, opReturnScript);
            }

            // Signing the input of the lock tx
            Sha256Hash hashForSig = txToLock.hashForSignature(0, inputTx.getOutput(0).getScriptPubKey(), BtcTransaction.SigHash.ALL, false);
            Script scriptSig = new Script(Script.createInputScript(from.sign(hashForSig).encodeToDER(), from.getPubKey()));
            txToLock.getInput(0).setScriptSig(scriptSig);

            pmtOfLockTx = PartialMerkleTree.buildFromLeaves(networkParameters, new byte[]{(byte) 0xff}, Arrays.asList(txToLock.getHash()));
            List<Sha256Hash> hashes = new ArrayList<>();
            Sha256Hash merkleRoot = pmtOfLockTx.getTxnHashAndMerkleRoot(hashes);

            blockWithTx = Helper.generateBtcBlock(lastBlock, Arrays.asList(txToLock), merkleRoot);
            btcBlockChain.add(blockWithTx);
            blockWithTxHeight = btcBlockChain.getBestChainHeight();

            Helper.generateAndAddBlocks(btcBlockChain, numberOfLockConfirmations);

            thisRepository.commit();

            // Marking as already processed
            if (markAsAlreadyProcessed) {
                try {
                    provider.setHeightBtcTxhashAlreadyProcessed(txToLock.getHash(), (long) blockWithTxHeight - 10);
                } catch (IOException e) {
                    throw new RuntimeException("Exception while trying to mark tx as already processed for test");
                }
            }
        };
    }
}
