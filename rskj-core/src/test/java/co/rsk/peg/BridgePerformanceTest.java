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

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.test.World;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.*;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;

public class BridgePerformanceTest {
    private static NetworkParameters networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();
    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static BridgeConstants bridgeConstants;

    private boolean oldCpuTimeEnabled;
    private ThreadMXBean thread;

    private class ExecutionTracker {
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

    class Mean {
        private long total = 0;
        private int samples = 0;

        public void add(long value) {
            total += value;
            samples++;
        }

        public long getMean() {
            return total / samples;
        }
    }

    class ExecutionStats {
        public String name;
        public Mean executionTimes;
        public Mean realExecutionTimes;
        public Mean slotsWritten;
        public Mean slotsCleared;

        public ExecutionStats(String name) {
            this.name = name;
            this.executionTimes = new Mean();
            this.realExecutionTimes = new Mean();
            this.slotsWritten = new Mean();
            this.slotsCleared = new Mean();
        }

        @Override
        public String toString() {
            return String.format(
                    "%s\t\tcpu(us): %d\t\treal(us): %d\t\twrt(slots): %d\t\tclr(slots): %d",
                    name,
                    executionTimes.getMean() / 1000,
                    realExecutionTimes.getMean() / 1000,
                    slotsWritten.getMean(),
                    slotsCleared.getMean()
            );
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
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

    private interface BridgeStorageProviderInitializer {
        void initialize(BridgeStorageProvider provider, int executionIndex);
    }

    private interface TxBuilder {
        Transaction build(int executionIndex);
    }

    private interface ABIEncoder {
        byte[] encode(int executionIndex);
    }

    private ExecutionTracker execute(
            ABIEncoder abiEncoder,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder, int executionIndex) {

        ExecutionTracker executionInfo = new ExecutionTracker(thread);

        RepositoryImpl repository = new RepositoryImpl();
        Repository track = repository.startTracking();
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        storageInitializer.initialize(storageProvider, executionIndex);

        try {
            storageProvider.save();
        } catch (Exception e) {
            throw new RuntimeException("Error trying to save the storage after initialization", e);
        }
        track.commit();

        Transaction tx = txBuilder.build(executionIndex);

        List<LogInfo> logs = new ArrayList<>();

        RepositoryTrackWithBenchmarking benchmarkerTrack = new RepositoryTrackWithBenchmarking(repository);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(
                tx,
                world.getBlockChain().getBestBlock(),
                benchmarkerTrack,
                world.getBlockChain().getBlockStore(),
                world.getBlockChain().getReceiptStore(),
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

    private ExecutionStats executeAndAverage(String name,
                                             int times,
                                             ABIEncoder abiEncoder,
                                             BridgeStorageProviderInitializer storageInitializer,
                                             TxBuilder txBuilder) {

        ExecutionStats stats = new ExecutionStats(name);

        for (int i = 0; i < times; i++) {
            System.out.println(String.format("%s %d/%d", name, i, times));

            ExecutionTracker tracker = execute(abiEncoder, storageInitializer, txBuilder, i);

            stats.executionTimes.add(tracker.getExecutionTime());
            stats.realExecutionTimes.add(tracker.getRealExecutionTime());
            stats.slotsWritten.add(tracker.getRepositoryStatistics().getSlotsWritten());
            stats.slotsCleared.add(tracker.getRepositoryStatistics().getSlotsCleared());
        }

        return stats;
    }

    @Test
    public void releaseBtc() throws IOException {
        Mean executionTime = new Mean();
        Mean realExecutionTime = new Mean();

        int minCentsBtc = 5;
        int maxCentsBtc = 100;

        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, int executionIndex) -> {
            Random rnd = new Random();
            ReleaseRequestQueue queue;

            try {
                queue = provider.getReleaseRequestQueue();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release request queue");
            }

            Coin value = Coin.CENT.multiply(rnd.nextInt(maxCentsBtc - minCentsBtc + 1) + minCentsBtc);

            for (int i = 0; i < rnd.nextInt(100 - 10 + 1) + 50; i++) {
                queue.add(new BtcECKey().toAddress(parameters), value);
            }
        };

        final byte[] releaseBtcEncoded = Bridge.RELEASE_BTC.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> releaseBtcEncoded;

        final Random rnd = new Random();
        TxBuilder txBuilder = (int executionIndex) -> {
            long satoshis = Coin.CENT.multiply(rnd.nextInt(maxCentsBtc - minCentsBtc + 1) + minCentsBtc).getValue();
            BigInteger weis = Denomination.satoshisToWeis(BigInteger.valueOf(satoshis));
            ECKey sender = new ECKey();

            return buildSendValueTx(sender, weis);
        };

        ExecutionStats stats = executeAndAverage("releaseBtc", 1000, abiEncoder, storageInitializer, txBuilder);

        System.out.println(stats);
    }

    private Transaction buildSendValueTx(ECKey sender, BigInteger value) {
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
}
