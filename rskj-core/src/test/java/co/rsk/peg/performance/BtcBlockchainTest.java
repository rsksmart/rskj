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

import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBlockStore;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

@Ignore
public class BtcBlockchainTest extends BridgePerformanceTestCase {
    @Test
    public void getBtcBlockchainBestChainHeight() throws IOException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.encode();
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainBestChainHeight");
        executeAndAverage("getBtcBlockchainBestChainHeight", 200, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcBlockchainInitialBlockHeight() throws IOException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT.encode();
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainInitialBlockHeight");
        executeAndAverage("getBtcBlockchainInitialBlockHeight", 200, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcBlockchainBlockHashAtDepth() throws IOException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH.encode(new Object[]{BigInteger.ZERO});
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainBlockHashAtDepth");
        executeAndAverage("getBtcBlockchainBlockHashAtDepth", 200, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcBlockchainBlockLocator() throws IOException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode();
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainBlockLocator");
        executeAndAverage("getBtcBlockchainBlockLocator", 200, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
        BridgePerformanceTest.addStats(stats);
    }

    private BridgeStorageProviderInitializer buildInitializer() {
        final int minBtcBlocks = 1000;
        final int maxBtcBlocks = 2000;

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
            Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
        };
    }


}
