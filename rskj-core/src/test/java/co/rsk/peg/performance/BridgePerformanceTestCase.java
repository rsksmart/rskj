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
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.db.BenchmarkedRepository;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.peg.*;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TmpTrieStoreFactory;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

import org.ethereum.vm.program.InternalTransaction;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public abstract class BridgePerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    protected static NetworkParameters networkParameters;
    protected static BridgeConstants bridgeConstants;
    protected static BtcBlockStoreWithCache.Factory btcBlockStoreFactory;

    @BeforeClass
    public static void setupB() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(networkParameters);
    }

    protected static class Helper extends PrecompiledContractPerformanceTestCase.Helper {
        // Federates for bridge regtest constants
        public static final List<ECKey> FEDERATOR_ECKEYS = Arrays.stream(new String[]{
                "federator1",
                "federator2",
                "federator3"
        }).map(generator -> ECKey.fromPrivate(HashUtil.keccak256(generator.getBytes(StandardCharsets.UTF_8)))).collect(Collectors.toList());

        public static ECKey getRandomFederatorECKey() {
            return Helper.FEDERATOR_ECKEYS.get(Helper.randomInRange(0, Helper.FEDERATOR_ECKEYS.size()-1));
        }

        public static Coin randomCoin(Coin base, int min, int max) {
            return base.multiply(randomInRange(min, max));
        }

        public static Coin randomCoinAddition(Coin base, int min, int max) {
            return base.add(Coin.valueOf(randomInRange(min, max),0));
        }

        public static TxBuilder getZeroValueValueTxBuilderFromFedMember() {
            return Helper.getZeroValueTxBuilder(Helper.getRandomFederatorECKey());
        }

        public static TxBuilder getTxBuilderWithInternalTransaction(RskAddress sender){
            return (int index) -> {
                TxBuilder txBuilder = Helper.getZeroValueTxBuilder(new ECKey());
                Transaction tx = txBuilder.build(index);
                InternalTransaction internalTx = new InternalTransaction(
                        tx.getHash().getBytes(),
                        0,
                        0,
                        null,
                        null,
                        null,
                        sender.getBytes(),
                        null,
                        null,
                        null,
                        null
                );
                return internalTx;
            };
        }

        public static BtcBlock generateAndAddBlocks(BtcBlockChain btcBlockChain, int blocksToGenerate) {
            BtcBlock block = btcBlockChain.getChainHead().getHeader();
            int initialHeight = btcBlockChain.getBestChainHeight();
            while ((btcBlockChain.getBestChainHeight() - initialHeight) < blocksToGenerate) {
                block = generateBtcBlock(block);
                btcBlockChain.add(block);
            }
            // Return the last generated block (useful)
            return block;
        }

        public static BtcBlock generateBtcBlock(BtcBlock prevBlock) {
            Sha256Hash merkleRoot = Sha256Hash.wrap(HashUtil.sha256(BigInteger.valueOf(new Random().nextLong()).toByteArray()));
            List<BtcTransaction> txs = Collections.emptyList();
            return generateBtcBlock(prevBlock, txs, merkleRoot);
        }

        public static BtcBlock generateBtcBlock(BtcBlock prevBlock, List<BtcTransaction> txs, Sha256Hash merkleRoot) {
            long nonce = 0;
            boolean verified = false;
            BtcBlock block = null;
            while (!verified) {
                try {
                    block = new BtcBlock(
                            networkParameters,
                            BtcBlock.BLOCK_VERSION_BIP65,
                            prevBlock.getHash(),
                            merkleRoot,
                            prevBlock.getTimeSeconds() + 10,
                            BtcBlock.EASIEST_DIFFICULTY_TARGET,
                            nonce,
                            txs
                    );
                    block.verifyHeader();
                    verified = true;
                } catch (VerificationException e) {
                    nonce++;
                }
            }

            return block;
        }

        public static TxBuilder getZeroValueRandomSenderTxBuilder() {
            return (int executionIndex) -> Helper.buildTx(new ECKey());
        }

        public static BridgeStorageProviderInitializer buildNoopInitializer() {
            return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore btcBlockStore) -> {};
        }
    }

    protected interface BridgeStorageProviderInitializer {
        void initialize(BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore);
    }

    protected interface PostInitCallback {
        void afterInit(EnvironmentBuilder.Environment environment);
    }

    protected ExecutionStats executeAndAverage(
            String name,
            int times,
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            ExecutionStats stats) throws VMException {

        return executeAndAverage(
                name, times, abiEncoder, storageInitializer,
                txBuilder, heightProvider, stats, null
        );
    }

    protected ExecutionStats executeAndAverage(
            String name,
            int times,
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            ExecutionStats stats,
            ResultCallback resultCallback) throws VMException {

        return executeAndAverage(
                name, times, abiEncoder, storageInitializer,
                txBuilder, heightProvider, stats, resultCallback, null
        );
    }

    protected ExecutionStats executeAndAverage(
            String name,
            int times,
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            ExecutionStats stats,
            ResultCallback resultCallback,
            PostInitCallback postInitCallback) throws VMException {

        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder() {
            private Bridge bridge;
            private RepositoryTrackWithBenchmarking benchmarkerTrack;
            private BridgeStorageProvider storageProvider;

            private TrieStore createTrieStore() {
                return TmpTrieStoreFactory.newInstance();
            }

            @Override
            public Environment build(int executionIndex, TxBuilder txBuilder, int height) throws VMException {
                TrieStore trieStore = createTrieStore();
                Trie trie = new Trie(trieStore);
                benchmarkerTrack = new RepositoryTrackWithBenchmarking(trieStore,  trie);
                Repository repository = benchmarkerTrack.startTracking();
                storageProvider = new BridgeStorageProvider(
                        repository,
                        PrecompiledContracts.BRIDGE_ADDR,
                        bridgeConstants,
                        activationConfig.forBlock((long) executionIndex)
                );
                BtcBlockStore btcBlockStore = btcBlockStoreFactory.newInstance(
                        repository,
                        bridgeConstants,
                        storageProvider,
                        activationConfig.forBlock((long) executionIndex)
                );

                storageInitializer.initialize(storageProvider, repository, executionIndex, btcBlockStore);
                repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(Coin.COIN.multiply(21_000_000L)));

                try {
                    storageProvider.save();
                } catch (Exception e) {
                    throw new RuntimeException("Error trying to save the storage after initialization", e);
                }

                repository.commit();
                benchmarkerTrack.commit();

                benchmarkerTrack = new RepositoryTrackWithBenchmarking(trieStore, benchmarkerTrack.getTrie());
                List<LogInfo> logs = new ArrayList<>();

                // TODO: This was commented to make registerBtcCoinbaseTransactionTest & getBtcTransactionConfirmationTest work.
                //  Cache is not being populated.
//                Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
//                        constants.getBridgeConstants().getBtcParams());

                BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                        btcBlockStoreFactory, constants.getBridgeConstants(), activationConfig);

                bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                        bridgeSupportFactory);
                BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
                Blockchain blockchain = blockChainBuilder.ofSize(height);
                Transaction tx = txBuilder.build(executionIndex);
                bridge.init(
                        tx,
                        blockchain.getBestBlock(),
                        benchmarkerTrack,
                        blockChainBuilder.getBlockStore(),
                        null,
                        logs
                );

                // Execute a random method so that bridge support initialization
                // does its initial writes to the repo for e.g. genesis block,
                // federation, etc, etc. and we don't get
                // those recorded in the actual execution.
                boolean oldLocalCall = tx.isLocalCallTransaction();
                tx.setLocalCallTransaction(true);
                bridge.execute(Bridge.GET_FEDERATION_SIZE.encode());
                tx.setLocalCallTransaction(oldLocalCall);
                benchmarkerTrack.getStatistics().clear();


                Environment environment = new Environment() {
                    @Override
                    public PrecompiledContracts.PrecompiledContract getContract() {
                        return bridge;
                    }

                    @Override
                    public BenchmarkedRepository getBenchmarkedRepository() {
                        return benchmarkerTrack;
                    }

                    @Override
                    public void finalise() {
                        benchmarkerTrack.commit();
                    }
                };

                if (postInitCallback != null) {
                    postInitCallback.afterInit(environment);
                }
                return environment;
            }
        };

        return super.executeAndAverage(name, times, environmentBuilder, abiEncoder, txBuilder, heightProvider, stats, resultCallback);
    }
}
