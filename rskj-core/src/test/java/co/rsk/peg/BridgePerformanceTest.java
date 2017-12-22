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
        private final ThreadMXBean thread;
        private long startTime, endTime;
        private long startRealTime, endRealTime;

        public ExecutionTracker(ThreadMXBean thread) {
            this.thread = thread;
        }

        public void startTimer() {
            startTime = thread.getCurrentThreadCpuTime();
            startRealTime = System.currentTimeMillis();
        }

        public void endTimer() {
            endTime = thread.getCurrentThreadCpuTime();
            endRealTime = System.currentTimeMillis();
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
        void initialize(BridgeStorageProvider provider);
    }

    private interface TxBuilder {
        Transaction build();
    }

    private ExecutionTracker execute(
            byte[] encodedABI,
            BridgeStorageProviderInitializer storageInitializer,
            TxBuilder txBuilder) throws IOException {

        ExecutionTracker executionInfo = new ExecutionTracker(thread);

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        storageInitializer.initialize(storageProvider);

        storageProvider.save();
        track.commit();

        Transaction tx = txBuilder.build();

        List<LogInfo> logs = new ArrayList<>();

        track = repository.startTracking();
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(
                tx,
                world.getBlockChain().getBestBlock(),
                track,
                world.getBlockChain().getBlockStore(),
                world.getBlockChain().getReceiptStore(),
                logs
        );

        executionInfo.startTimer();
        bridge.execute(encodedABI);
        executionInfo.endTimer();

        track.commit();

        return executionInfo;
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

    @Test
    public void releaseBtc() throws IOException {
        ExecutionTracker tracker = execute(
                Bridge.RELEASE_BTC.encode(),
                (BridgeStorageProvider provider) -> {},
                () -> buildSendValueTx(
                        new ECKey(),
                        Denomination.satoshisToWeis(BigInteger.valueOf(Coin.COIN.multiply(2).getValue()))
                )
        );

        System.out.println(tracker.getExecutionTime());
        System.out.println(tracker.getRealExecutionTime());
    }
}
