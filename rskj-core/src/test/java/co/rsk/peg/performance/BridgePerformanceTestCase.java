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
import co.rsk.config.BridgeConstants;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageConfiguration;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.Trie;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.TrieStorePoolOnMemory;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public abstract class BridgePerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    protected static NetworkParameters networkParameters;
    protected static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setupB() throws Exception {
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
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
                            BtcBlock.BLOCK_VERSION_BIP66,
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
            return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {};
        }
    }

    protected interface BridgeStorageProviderInitializer {
        void initialize(BridgeStorageProvider provider, Repository repository, int executionIndex);
    }

    protected ExecutionStats executeAndAverage(
            String name,
            int times,
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            ExecutionStats stats) {
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
            ResultCallback resultCallback) {

        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder() {
            private Bridge bridge;
            private RepositoryTrackWithBenchmarking benchmarkerTrack;

            private RepositoryImpl createRepositoryImpl() {
                return new RepositoryImpl(new Trie(true), new HashMapDB(), new TrieStorePoolOnMemory());
            }

            @Override
            public Environment initialize(int executionIndex) {
                RepositoryImpl repository = createRepositoryImpl();
                Repository track = repository.startTracking();
                BridgeStorageConfiguration bridgeStorageConfigurationAtThisHeight = BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(executionIndex));
                BridgeStorageProvider storageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,bridgeStorageConfigurationAtThisHeight);

                storageInitializer.initialize(storageProvider, track, executionIndex);

                try {
                    storageProvider.save();
                } catch (Exception e) {
                    throw new RuntimeException("Error trying to save the storage after initialization", e);
                }
                track.commit();

                Transaction tx = txBuilder.build(executionIndex);

                List<LogInfo> logs = new ArrayList<>();

                benchmarkerTrack = new RepositoryTrackWithBenchmarking(repository);

                bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
                Blockchain blockchain = BlockChainBuilder.ofSize(heightProvider.getHeight(executionIndex));
                bridge.init(
                        tx,
                        blockchain.getBestBlock(),
                        benchmarkerTrack,
                        blockchain.getBlockStore(),
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

                return new Environment(
                        bridge,
                        benchmarkerTrack
                );
            }

            @Override
            public void teardown() {
                benchmarkerTrack.commit();
            }
        };

        return super.executeAndAverage(name, times, environmentBuilder, abiEncoder, txBuilder, heightProvider, stats, resultCallback);
    }
}
