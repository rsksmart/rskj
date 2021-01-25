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
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.util.MaxSizeHashMap;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

@Ignore
public class LockWhitelistTest extends BridgePerformanceTestCase {
    private LockWhitelist lockWhitelist;

    private static final ECKey authorizedWhitelistChanger = ECKey.fromPrivate(Hex.decode("3890187a3071327cee08467ba1b44ed4c13adb2da0d5ffcc0563c371fa88259c"));

    @Test
    public void getLockWhitelistSize() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("getLockWhitelistSize");
        executeTestCase((int executionIndex) -> Bridge.GET_LOCK_WHITELIST_SIZE.encode(), "getLockWhitelistSize", 200, stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    public void getLockWhitelistAddress() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("getLockWhitelistAddress");
        executeTestCase(
                (int executionIndex) -> Bridge.GET_LOCK_WHITELIST_ADDRESS.encode(new Object[]{Helper.randomInRange(0, lockWhitelist.getSize()-1)}),
                "getLockWhitelistAddress",
                200,
                stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    public void addLockWhitelistAddress() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("addLockWhitelistAddress");
        executeTestCase(
                (int executionIndex) -> {
                    String address = new BtcECKey().toAddress(networkParameters).toBase58();
                    BigInteger value = BigInteger.valueOf(Helper.randomCoin(Coin.COIN, 1, 30).getValue());
                    return Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{address, value});
                },
                "addLockWhitelistAddress",
                200,
                stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    public void removeLockWhitelistAddress() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("removeLockWhitelistAddress");
        executeTestCase(
                (int executionIndex) -> {
                    String address = lockWhitelist.getAddresses().get(Helper.randomInRange(0, lockWhitelist.getSize()-1)).toBase58();
                    return Bridge.REMOVE_LOCK_WHITELIST_ADDRESS.encode(new Object[]{address});
                },
                "removeLockWhitelistAddress",
                200,
                stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    public void setLockWhitelistDisableBlockDelay() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("setLockWhitelistDisableBlockDelay");
        executeTestCase(
                (int executionIndex) -> {
                    BigInteger disableBlockDelay = BigInteger.valueOf(Helper.randomInRange(10000, Integer.MAX_VALUE));
                    return Bridge.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY.encode(new Object[]{disableBlockDelay});
                },
                "setLockWhitelistDisableBlockDelay",
                200,
                stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void executeTestCase(ABIEncoder abiEncoder, String name, int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                name,
                times,
                abiEncoder,
                buildInitializer(),
                (int executionIndex) -> Helper.buildTx(authorizedWhitelistChanger),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer() {
        final int minSize = 10;
        final int maxSize = 100;
        final int minBtcBlocks = 500;
        final int maxBtcBlocks = 1000;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            BtcBlockStore btcBlockStore = new RepositoryBtcBlockStoreWithCache(
                BridgeRegTestConstants.getInstance().getBtcParams(),
                repository.startTracking(),
                new MaxSizeHashMap<>(RepositoryBtcBlockStoreWithCache.MAX_SIZE_MAP_STORED_BLOCKS, true),
                PrecompiledContracts.BRIDGE_ADDR,
                null,
                null,
                null
            );
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            lockWhitelist = provider.getLockWhitelist();

            int size = Helper.randomInRange(minSize, maxSize);
            for (int i = 0; i < size; i++) {
                Address address = new BtcECKey().toAddress(networkParameters);
                Coin value = Helper.randomCoin(Coin.COIN, 1, 30);
                lockWhitelist.put(address, new OneOffWhiteListEntry(address, value));
            }
        };
    }
}
