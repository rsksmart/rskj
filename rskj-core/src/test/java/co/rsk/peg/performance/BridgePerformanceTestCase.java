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
import co.rsk.config.TestSystemProperties;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.vm.VMPerformanceTest;
import org.ethereum.config.blockchain.regtest.RegTestConfig;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.spongycastle.util.encoders.Hex;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public abstract class BridgePerformanceTestCase {
    protected static NetworkParameters networkParameters;
    protected static BridgeConstants bridgeConstants;
    private static TestSystemProperties config;

    private boolean oldCpuTimeEnabled;
    private ThreadMXBean thread;

    protected class ExecutionTracker {
        private static final long MILLION = 1_000_000;

        private final ThreadMXBean thread;
        private long startTime, endTime;
        private long startRealTime, endRealTime;
        private RepositoryTrackWithBenchmarking.Statistics repositoryStatistics;

        public ExecutionTracker(ThreadMXBean thread) {
            this.thread = thread;
        }

        public void startTimer() {
            startTime = thread.getCurrentThreadCpuTime();
            startRealTime = System.currentTimeMillis() * MILLION;
        }

        public void endTimer() {
            endTime = thread.getCurrentThreadCpuTime();
            endRealTime = System.currentTimeMillis() * MILLION;
        }

        public void setRepositoryStatistics(RepositoryTrackWithBenchmarking.Statistics statistics) {
            this.repositoryStatistics = statistics;
        }

        public RepositoryTrackWithBenchmarking.Statistics getRepositoryStatistics() {
            return this.repositoryStatistics;
        }

        public long getExecutionTime() {
            return endTime - startTime;
        }

        public long getRealExecutionTime() {
            return endRealTime - startRealTime;
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @AfterClass
    public static void printStatsIfNotInSuite() throws Exception {
        if (!BridgePerformanceTest.isRunning()) {
            BridgePerformanceTest.printStats();
        }
    }

    @Before
    public void setupCpuTime() {
        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) {
            throw new RuntimeException("Thread CPU time not supported");
        };

        oldCpuTimeEnabled = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
    }

    @After
    public void teardownCpuTime() {
        thread.setThreadCpuTimeEnabled(oldCpuTimeEnabled);
    }

    @After
    public void forceGC() {
        long sm = Runtime.getRuntime().freeMemory();
        VMPerformanceTest.forceGc();
        long em = Runtime.getRuntime().freeMemory();
        System.out.println(String.format("GC - free mem before: %d, after: %d", sm, em));
    }

    protected static class Helper {
        public static Transaction buildTx(ECKey sender) {
            return buildSendValueTx(sender, BigInteger.ZERO);
        }

        public static Transaction buildSendValueTx(ECKey sender, BigInteger value) {
            byte[] gasPrice = Hex.decode("00");
            byte[] gasLimit = Hex.decode("00");

            Transaction tx = new Transaction(
                    null,
                    gasPrice,
                    gasLimit,
                    sender.getAddress(),
                    value.toByteArray(),
                    null);
            tx.sign(sender.getPrivKeyBytes());

            return tx;
        }

        public static int randomInRange(int min, int max) {
            return new Random().nextInt(max - min + 1) + min;
        }

        public static Coin randomCoin(Coin base, int min, int max) {
            return base.multiply(randomInRange(min, max));
        }

        public static HeightProvider getRandomHeightProvider(int max) {
            return (int executionIndex) -> new Random().nextInt(max);
        }

        public static HeightProvider getRandomHeightProvider(int min, int max) {
            return (int executionIndex) -> randomInRange(min, max);
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
            return (int executionIndex) -> Helper.buildSendValueTx(new ECKey(), BigInteger.ZERO);
        }

        public static BridgeStorageProviderInitializer buildNoopInitializer() {
            return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {};
        }
    }

    protected interface BridgeStorageProviderInitializer {
        void initialize(BridgeStorageProvider provider, Repository repository, int executionIndex);
    }

    protected interface TxBuilder {
        Transaction build(int executionIndex);
    }

    protected interface ABIEncoder {
        byte[] encode(int executionIndex);
    }

    protected interface HeightProvider {
        int getHeight(int executionIndex);
    }

    private ExecutionTracker execute(
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder, HeightProvider heightProvider, int executionIndex) {

        ExecutionTracker executionInfo = new ExecutionTracker(thread);

        RepositoryImpl repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);

        storageInitializer.initialize(storageProvider, track, executionIndex);

        try {
            storageProvider.save();
        } catch (Exception e) {
            throw new RuntimeException("Error trying to save the storage after initialization", e);
        }
        track.commit();

        Transaction tx = txBuilder.build(executionIndex);

        List<LogInfo> logs = new ArrayList<>();

        RepositoryTrackWithBenchmarking benchmarkerTrack = new RepositoryTrackWithBenchmarking(config, repository);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Blockchain blockchain = BlockChainBuilder.ofSizeWithNoTransactionPoolCleaner(heightProvider.getHeight(executionIndex));
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
        bridge.execute(Bridge.GET_FEDERATION_SIZE.encode());
        benchmarkerTrack.getStatistics().clear();

        executionInfo.startTimer();
        bridge.execute(abiEncoder.encode(executionIndex));
        executionInfo.endTimer();

        benchmarkerTrack.commit();

        executionInfo.setRepositoryStatistics(benchmarkerTrack.getStatistics());

        return executionInfo;
    }

    protected ExecutionStats executeAndAverage(String name,
             int times,
             ABIEncoder abiEncoder,
             BridgeStorageProviderInitializer storageInitializer,
             TxBuilder txBuilder,
             HeightProvider heightProvider,
             ExecutionStats stats) {

        long sm = Runtime.getRuntime().freeMemory();

        for (int i = 0; i < times; i++) {
            System.out.println(String.format("%s %d/%d", name, i+1, times));

            ExecutionTracker tracker = execute(abiEncoder, storageInitializer, txBuilder, heightProvider, i);

            stats.executionTimes.add(tracker.getExecutionTime());
            stats.realExecutionTimes.add(tracker.getRealExecutionTime());
            stats.slotsWritten.add(tracker.getRepositoryStatistics().getSlotsWritten());
            stats.slotsCleared.add(tracker.getRepositoryStatistics().getSlotsCleared());
        }

        return stats;
    }
}
