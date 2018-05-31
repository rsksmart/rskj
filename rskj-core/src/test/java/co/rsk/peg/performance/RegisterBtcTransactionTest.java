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

package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBlockStore;
import co.rsk.peg.Whitelist.OneOffWhiteListEntry;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Ignore
public class RegisterBtcTransactionTest extends BridgePerformanceTestCase {
    private BtcBlock blockWithTx;
    private int blockWithTxHeight;
    private BtcTransaction txToLock;
    private PartialMerkleTree pmtOfLockTx;

    @Test
    public void registerBtcTransaction() {
        ExecutionStats stats = new ExecutionStats("registerBtcTransaction");
        registerBtcTransaction_lockSuccess(100, stats);
        registerBtcTransaction_alreadyProcessed(100, stats);
        registerBtcTransaction_notEnoughConfirmations(100, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void registerBtcTransaction_lockSuccess(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                false
        );

        executeAndAverage("registerBtcTransaction-lockSuccess", times, getABIEncoder(), storageInitializer, Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);

    }

    private void registerBtcTransaction_alreadyProcessed(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                20,
                true
        );

        executeAndAverage("registerBtcTransaction-alreadyProcessed", times, getABIEncoder(), storageInitializer, Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
    }

    private void registerBtcTransaction_notEnoughConfirmations(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForLock(
                1000,
                2000,
                1,
                false
        );

        executeAndAverage("registerBtcTransaction-notEnoughConfirmations", times, getABIEncoder(), storageInitializer, Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{
                        txToLock.bitcoinSerialize(),
                        blockWithTxHeight,
                        pmtOfLockTx.bitcoinSerialize()
                });
    }

    private BridgeStorageProviderInitializer generateInitializerForLock(int minBtcBlocks, int maxBtcBlocks, int numberOfLockConfirmations, boolean markAsAlreadyProcessed) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            BtcBlockStore btcBlockStore = new RepositoryBlockStore(new TestSystemProperties(), repository, PrecompiledContracts.BRIDGE_ADDR);
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
            Coin fromAmount = Coin.CENT.multiply(Helper.randomInRange(10, 100));
            Coin lockAmount = fromAmount.divide(Helper.randomInRange(2, 10));
            Coin changeAmount = fromAmount.subtract(lockAmount).subtract(Coin.MILLICOIN); // 1 millicoin fee simulation

            // Whitelisting sender
            provider.getLockWhitelist().put(fromAddress, new OneOffWhiteListEntry(fromAddress, lockAmount));

            // Input tx
            BtcTransaction inputTx = new BtcTransaction(networkParameters);
            inputTx.addOutput(fromAmount, fromAddress);

            // Lock tx that uses the input tx
            txToLock = new BtcTransaction(networkParameters);
            txToLock.addInput(inputTx.getOutput(0));
            txToLock.addOutput(lockAmount, bridgeConstants.getGenesisFederation().getAddress());
            txToLock.addOutput(changeAmount, fromAddress);

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

            // Marking as already processed
            if (markAsAlreadyProcessed) {
                try {
                    provider.getBtcTxHashesAlreadyProcessed().put(txToLock.getHash(), (long) blockWithTxHeight - 10);
                } catch (IOException e) {
                    throw new RuntimeException("Exception while trying to mark tx as already processed for test");
                }
            }
        };
    }


}
