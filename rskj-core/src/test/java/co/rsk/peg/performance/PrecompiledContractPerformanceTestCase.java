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

import co.rsk.db.BenchmarkedRepository;
import co.rsk.db.RepositoryTrackWithBenchmarking;
import co.rsk.vm.VMPerformanceTest;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class PrecompiledContractPerformanceTestCase {
    protected static Constants constants;
    protected static ActivationConfig activationConfig;

    private boolean oldCpuTimeEnabled;
    private ThreadMXBean thread;
    private boolean quiet;

    protected void setQuietMode(boolean quiet) {
        this.quiet = quiet;
    }

    protected boolean isInQuietMode() {
        return quiet;
    }

    private void printLine(String line) {
        if (!this.isInQuietMode()) {
            System.out.println(line);
        }
    }

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

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.genesis();
    }

    @AfterAll
    static void printStatsIfNotInSuite() throws Exception {
        if (!PrecompiledContractPerformanceTest.isRunning()) {
            PrecompiledContractPerformanceTest.printStats();
        }
    }

    @BeforeEach
    void setupCpuTime() {
        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) {
            throw new RuntimeException("Thread CPU time not supported");
        }
        ;

        oldCpuTimeEnabled = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
    }

    @AfterEach
    void teardownCpuTime() {
        thread.setThreadCpuTimeEnabled(oldCpuTimeEnabled);
    }

    @AfterEach
    void forceGC() {
        long sm = Runtime.getRuntime().freeMemory();
        VMPerformanceTest.forceGc();
        long em = Runtime.getRuntime().freeMemory();
        printLine(String.format("GC - free mem before: %d, after: %d", sm, em));
    }

    protected static class Helper {
        public static int randomInRange(int min, int max) {
            return TestUtils.generateInt(Helper.class.toString(), max - min + 1) + min;
        }

        public static Transaction buildTx(ECKey sender) {
            return buildSendValueTx(sender, BigInteger.ZERO);
        }

        public static Transaction buildSendValueTx(ECKey sender, BigInteger value) {
            byte[] gasPrice = Hex.decode("00");
            byte[] gasLimit = Hex.decode("00");

            Transaction tx = Transaction.builder()
                    .gasPrice(gasPrice)
                    .gasLimit(gasLimit)
                    .destination(sender.getAddress())
                    .value(value)
                    .build();
            tx.sign(sender.getPrivKeyBytes());
            tx.setLocalCallTransaction(true);
            // Force caching the sender to avoid outliers in the gas estimation
            tx.getSender();

            return tx;
        }

        public static TxBuilder getZeroValueTxBuilder(ECKey sender) {
            return (int executionIndex) -> buildTx(sender);
        }

        public static HeightProvider getRandomHeightProvider(int max) {
            return (int executionIndex) -> TestUtils.generateInt(PrecompiledContractPerformanceTest.class.toString() + max, max);
        }

        public static HeightProvider getRandomHeightProvider(int min, int max) {
            return (int executionIndex) -> randomInRange(min, max);
        }

        public static Block getMockBlock(long blockNumber) {
            Block block = mock(Block.class);
            when(block.getNumber()).thenReturn(blockNumber);
            return block;
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
        interface Environment {
            PrecompiledContracts.PrecompiledContract getContract();

            BenchmarkedRepository getBenchmarkedRepository();

            void finalise();

            static Environment withContract(PrecompiledContracts.PrecompiledContract contract) {
                return new Environment() {
                    @Override
                    public PrecompiledContracts.PrecompiledContract getContract() {
                        return contract;
                    }

                    @Override
                    public BenchmarkedRepository getBenchmarkedRepository() {
                        return () -> new BenchmarkedRepository.Statistics();
                    }

                    @Override
                    public void finalise() {
                        // No finalisation
                    }
                };
            }
        }

        Environment build(int executionIndex, TxBuilder txBuilder, int height) throws VMException;
    }

    protected interface ResultCallback {
        void callback(EnvironmentBuilder.Environment environment, byte[] callResult);
    }

    private ExecutionTracker execute(
            EnvironmentBuilder environmentBuilder,
            ABIEncoder abiEncoder,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            int executionIndex,
            ResultCallback resultCallback) throws VMException {

        ExecutionTracker executionInfo = new ExecutionTracker(thread);

        // Initialize the environment, obtaining a fresh contract ready for execution
        EnvironmentBuilder.Environment environment = environmentBuilder.build(
                executionIndex,
                txBuilder,
                heightProvider.getHeight(executionIndex)
        );

        executionInfo.startTimer();
        byte[] executionResult = environment.getContract().execute(abiEncoder.encode(executionIndex));
        executionInfo.endTimer();

        environment.finalise();

        if (resultCallback != null) {
            resultCallback.callback(environment, executionResult);
        }

        executionInfo.setRepositoryStatistics(environment.getBenchmarkedRepository().getStatistics());

        return executionInfo;
    }

    protected ExecutionStats executeAndAverage(
            String name,
            int times,
            EnvironmentBuilder environmentBuilder,
            ABIEncoder abiEncoder,
            TxBuilder txBuilder,
            HeightProvider heightProvider,
            ExecutionStats stats,
            ResultCallback resultCallback) throws VMException {

        for (int i = 0; i < times; i++) {
            printLine(String.format("%s %d/%d", name, i + 1, times));

            ExecutionTracker tracker = execute(environmentBuilder, abiEncoder, txBuilder, heightProvider, i, resultCallback);

            stats.executionTimes.add(tracker.getExecutionTime());
            stats.realExecutionTimes.add(tracker.getRealExecutionTime());
            stats.slotsWritten.add(tracker.getRepositoryStatistics().getSlotsWritten());
            stats.slotsCleared.add(tracker.getRepositoryStatistics().getSlotsCleared());
        }

        return stats;
    }
}
