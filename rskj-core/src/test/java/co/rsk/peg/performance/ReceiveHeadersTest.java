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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Ignore
public class ReceiveHeadersTest extends BridgePerformanceTestCase {
    private BtcBlockStore btcBlockStore;
    private BtcBlock lastBlock;
    private BtcBlock expectedBestBlock;
    private static ScriptBuilderWrapper scriptBuilderWrapper;
    private static BridgeSerializationUtils bridgeSerializationUtils;

    @BeforeClass
    public static void setupA() {
        scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();
        bridgeSerializationUtils = BridgeSerializationUtils.getInstance(scriptBuilderWrapper);
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    // This is here for profiling with any external tools (e.g., visualVM)
    public static void main(String args[]) throws Exception {
        setupA();
        setupB();
        ReceiveHeadersTest test = new ReceiveHeadersTest();
        test.setupCpuTime();

        System.out.println("Ready\n");
        System.in.read();
        System.out.println("Going!\n");
        test.receiveHeadersSingleBlock();

        test.teardownCpuTime();
    }

    @Before
    public void warmup() throws VMException {
        setQuietMode(true);
        System.out.print("Doing a few initial passes... ");
        doReceiveHeaders("warmup", 100, 1, 0);
        setQuietMode(false);
        System.out.print("Done!\n");
    }

    @Test
    public void receiveHeadersSingleBlock() throws VMException {
        BridgePerformanceTest.addStats(doReceiveHeaders("receiveHeaders-singleBlock", 2000, 1, 0));
    }

    @Test
    public void receiveHeadersInterpolation() throws VMException {
        CombinedExecutionStats stats = new CombinedExecutionStats("receiveHeaders-interpolation");

        stats.add(doReceiveHeaders("receiveHeaders-interpolation",1000, 1, 0));
        stats.add(doReceiveHeaders("receiveHeaders-interpolation",1000, 500, 0));

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void receiveHeadersIncremental() throws VMException {
        CombinedExecutionStats stats = new CombinedExecutionStats("receiveHeaders-incremental");

        for (int i = 1; i <= 500; i++) {
            stats.add(doReceiveHeaders("receiveHeaders-incremental",10, i, 0));
        }

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void receiveHeadersWithForking() throws VMException {
        CombinedExecutionStats stats = new CombinedExecutionStats("receiveHeaders-withForking");

        for (int numHeaders = 1; numHeaders < 10; numHeaders++) {
            for (int depth = 0; depth <= 10; depth++) {
                stats.add(doReceiveHeaders("receiveHeaders-withForking", 50, numHeaders, depth));
            }
        }

        BridgePerformanceTest.addStats(stats);
    }

    private ExecutionStats doReceiveHeaders(String caseName, int times, int numHeaders, int forkDepth) throws VMException {
        String name = String.format("%s-forkdepth-%d-headers-%d", caseName, forkDepth, numHeaders);
        ExecutionStats stats = new ExecutionStats(name);
        int totalHeaders = numHeaders + forkDepth;
        return executeAndAverage(
                name,
                times,
                generateABIEncoder(totalHeaders, totalHeaders, forkDepth),
                buildInitializer(1000, 2000),
                Helper.getZeroValueTxBuilder(Helper.getRandomFederatorECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                            (Repository) environment.getBenchmarkedRepository(),
                            PrecompiledContracts.BRIDGE_ADDR,
                            constants.getBridgeConstants(),
                            activationConfig.forBlock(0),
                            bridgeSerializationUtils
                    );

                    btcBlockStore = new RepositoryBtcBlockStoreWithCache(
                        BridgeRegTestConstants.getInstance().getBtcParams(),
                        (Repository) environment.getBenchmarkedRepository(),
                        new HashMap<>(),
                        PrecompiledContracts.BRIDGE_ADDR,
                        bridgeConstants,
                        bridgeStorageProvider,
                        activationConfig.forBlock(0)
                    );
                    Sha256Hash bestBlockHash = null;
                    try {
                        bestBlockHash = btcBlockStore.getChainHead().getHeader().getHash();
                    } catch (BlockStoreException e) {
                        Assert.fail(e.getMessage());
                    }
                    Assert.assertEquals(expectedBestBlock.getHash(), bestBlockHash);
                }
        );
    }

    private ABIEncoder generateABIEncoder(int minBlocks, int maxBlocks, int forkDepth) {
        return (int executionIndex) -> {
            List<BtcBlock> headersToSendToBridge = new ArrayList<>();

            BtcBlock currentBlock = lastBlock;
            int currentDepth = forkDepth;
            while (currentDepth-- > 0) {
                try {
                    currentBlock = btcBlockStore.get(currentBlock.getPrevBlockHash()).getHeader();
                } catch (BlockStoreException e) {
                    throw new RuntimeException("Unexpected error trying to fetch previous block", e);
                }
            }

            int blocksToGenerate = Helper.randomInRange(minBlocks, maxBlocks);
            for (int i = 0; i < blocksToGenerate; i++) {
                currentBlock = Helper.generateBtcBlock(currentBlock);
                headersToSendToBridge.add(currentBlock);
            }

            expectedBestBlock = currentBlock;

            Object[] headersEncoded = headersToSendToBridge.stream().map(h -> h.cloneAsHeader().bitcoinSerialize()).toArray();

            return Bridge.RECEIVE_HEADERS.encode(new Object[]{headersEncoded});
        };
    }

    private BridgeStorageProviderInitializer buildInitializer(int minBlocks, int maxBlocks) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            btcBlockStore = blockStore;
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBlocks, maxBlocks);
            lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
        };
    }


}
