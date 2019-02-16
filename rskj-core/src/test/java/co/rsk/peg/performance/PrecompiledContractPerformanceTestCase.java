/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.config.TestSystemProperties;
import co.rsk.db.BenchmarkedRepository;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.vm.VMPerformanceTest;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.Random;

public abstract class PrecompiledContractPerformanceTestCase {
    protected static TestSystemProperties config;

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
            // Everything is expressed in nanoseconds
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
    public static void setup() throws Exception {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
    }

    @AfterClass
    public static void printStatsIfNotInSuite() throws Exception {
        if (!PrecompiledContractPerformanceTest.isRunning()) {
            PrecompiledContractPerformanceTest.printStats();
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
        public static int randomInRange(int min, int max) {
            return new Random().nextInt(max - min + 1) + min;
        }

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

        public static TxBuilder getZeroValueTxBuilder(ECKey sender) {
            return (int executionIndex) -> buildTx(sender);
        }

        public static HeightProvider getRandomHeightProvider(int max) {
            return (int executionIndex) -> new Random().nextInt(max);
        }

        public static HeightProvider getRandomHeightProvider(int min, int max) {
            return (int executionIndex) -> randomInRange(min, max);
        }
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

    protected interface EnvironmentBuilder {
        class Environment {
            PrecompiledContracts.PrecompiledContract contract;
            BenchmarkedRepository benchmarkedRepository;

            public Environment(
                    PrecompiledContracts.PrecompiledContract contract,
                    BenchmarkedRepository benchmarkedRepository) {

                this.contract = contract;
                this.benchmarkedRepository = benchmarkedRepository;
            }
        }

        Environment initialize(int executionIndex);
        void teardown();
    }

    private ExecutionTracker execute(
            EnvironmentBuilder testEnvironment,
            ABIEncoder abiEncoder,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            int executionIndex) {

        ExecutionTracker executionInfo = new ExecutionTracker(thread);

        // Initialize the environment, obtaining a fresh contract ready for execution
        EnvironmentBuilder.Environment environment = testEnvironment.initialize(executionIndex);

        executionInfo.startTimer();
        environment.contract.execute(abiEncoder.encode(executionIndex));
        executionInfo.endTimer();

        testEnvironment.teardown();

        executionInfo.setRepositoryStatistics(environment.benchmarkedRepository.getStatistics());

        return executionInfo;
    }

    protected ExecutionStats executeAndAverage(String name,
           int times,
           EnvironmentBuilder environmentBuilder,
           ABIEncoder abiEncoder,
           TxBuilder txBuilder,
           HeightProvider heightProvider,
           ExecutionStats stats) {

        for (int i = 0; i < times; i++) {
            System.out.println(String.format("%s %d/%d", name, i+1, times));

            ExecutionTracker tracker = execute(environmentBuilder, abiEncoder, txBuilder, heightProvider, i);

            stats.executionTimes.add(tracker.getExecutionTime());
            stats.realExecutionTimes.add(tracker.getRealExecutionTime());
            stats.slotsWritten.add(tracker.getRepositoryStatistics().getSlotsWritten());
            stats.slotsCleared.add(tracker.getRepositoryStatistics().getSlotsCleared());
        }

        return stats;
    }
}
