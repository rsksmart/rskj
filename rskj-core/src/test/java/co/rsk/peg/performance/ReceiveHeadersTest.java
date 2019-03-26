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
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBlockStore;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Ignore
public class ReceiveHeadersTest extends BridgePerformanceTestCase {
    private BtcBlockStore btcBlockStore;
    private BtcBlock lastBlock;
    private BtcBlock expectedBestBlock;

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

    @Test
    public void receiveHeadersSingleBlock() throws IOException {
        setQuietMode(true);
        System.out.print("Doing a few initial passes... ");
        doReceiveHeaders(100, 1, 0);
        setQuietMode(false);
        System.out.print("Done!\n");

        ExecutionStats stats = new ExecutionStats("receiveHeaders-singleBlock");

        executeAndAverage(
                "receiveHeaders-singleBlock", 2000,
                generateABIEncoder(1, 1, 0),
                buildInitializer(1000, 2000),
                Helper.getZeroValueTxBuilder(Helper.getRandomFederatorECKey()),
                Helper.getRandomHeightProvider(10),
                stats
        );

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void receiveHeaders() throws IOException {
        CombinedExecutionStats stats = new CombinedExecutionStats("receiveHeaders");

        for (int i = 1; i <= 500; i++) {
            stats.add(doReceiveHeaders(10, i, 0));
        }

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void receiveHeadersWithForking() throws IOException {
        setQuietMode(true);
        doReceiveHeaders(100, 1, 0);
        setQuietMode(false);

        CombinedExecutionStats stats = new CombinedExecutionStats("receiveHeaders-withForking");

        for (int numHeaders = 1; numHeaders < 10; numHeaders++) {
            for (int depth = 0; depth <= 10; depth++) {
                stats.add(doReceiveHeaders(50, numHeaders, depth));
            }
        }

        BridgePerformanceTest.addStats(stats);
    }

    private ExecutionStats doReceiveHeaders(int times, int numHeaders, int forkDepth) {
        String name = String.format("receiveHeaders-fork-%d-headers-%d", forkDepth, numHeaders);
        ExecutionStats stats = new ExecutionStats(name);
        int totalHeaders = numHeaders + forkDepth;
        return executeAndAverage(
                name, times,
                generateABIEncoder(totalHeaders, totalHeaders, forkDepth),
                buildInitializer(1000, 2000),
                Helper.getZeroValueTxBuilder(Helper.getRandomFederatorECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    btcBlockStore = new RepositoryBlockStore(new TestSystemProperties(), (Repository) environment.getBenchmarkedRepository(), PrecompiledContracts.BRIDGE_ADDR);
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
                    currentBlock = btcBlockStore.get(lastBlock.getPrevBlockHash()).getHeader();
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

            Object[] headersEncoded = headersToSendToBridge.stream().map(h -> h.bitcoinSerialize()).toArray();

            return Bridge.RECEIVE_HEADERS.encode(new Object[]{headersEncoded});
        };
    }

    private BridgeStorageProviderInitializer buildInitializer(int minBlocks, int maxBlocks) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            btcBlockStore = new RepositoryBlockStore(new TestSystemProperties(), repository, PrecompiledContracts.BRIDGE_ADDR);
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
