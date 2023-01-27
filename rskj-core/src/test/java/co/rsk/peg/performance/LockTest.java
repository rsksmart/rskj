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

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

// Everything related to locking that is not
// registerBtcTransaction, which has its own
// test case given its complexity
@Disabled
class LockTest extends BridgePerformanceTestCase {
    private Sha256Hash randomHashInMap;

    @Test
    void getMinimumLockTxValue() throws IOException, VMException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();
        ExecutionStats stats = new ExecutionStats("getMinimumLockTxValue");
        executeAndAverage("getMinimumLockTxValue", 1000, abiEncoder, Helper.buildNoopInitializer(), Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10), stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    void isBtcTxHashAlreadyProcessed() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("isBtcTxHashAlreadyProcessed");
        isBtcTxHashAlreadyProcessed_yes(100, stats);
        isBtcTxHashAlreadyProcessed_no(100, stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void isBtcTxHashAlreadyProcessed_yes(int times, ExecutionStats stats) throws VMException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.IS_BTC_TX_HASH_ALREADY_PROCESSED.encode(ByteUtil.toHexString(randomHashInMap.getBytes()));
        executeAndAverage("isBtcTxHashAlreadyProcessed-yes", times, abiEncoder, buildInitializer(),
                Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
    }

    private void isBtcTxHashAlreadyProcessed_no(int times, ExecutionStats stats) throws VMException {
        Sha256Hash hash = Sha256Hash.of(BigInteger.valueOf(TestUtils.generateLong(String.valueOf(times))).toByteArray());
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.IS_BTC_TX_HASH_ALREADY_PROCESSED.encode(new Object[]{ByteUtil.toHexString(hash.getBytes())});
        executeAndAverage("isBtcTxHashAlreadyProcessed-no", times, abiEncoder, buildInitializer(),
                Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);
    }

    @Test
    void getBtcTxHashProcessedHeight() throws VMException {
        ExecutionStats stats = new ExecutionStats("getBtcTxHashProcessedHeight");
        getBtcTxHashProcessedHeight_processed(100, stats);
        getBtcTxHashProcessedHeight_notProcessed(100, stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void getBtcTxHashProcessedHeight_processed(int times, ExecutionStats stats) throws VMException {
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_TX_HASH_PROCESSED_HEIGHT.encode(new Object[]{ByteUtil.toHexString(randomHashInMap.getBytes())});
        executeAndAverage("getBtcTxHashProcessedHeight-processed", times, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10), stats);
    }

    private void getBtcTxHashProcessedHeight_notProcessed(int times, ExecutionStats stats) throws VMException {
        Sha256Hash hash = Sha256Hash.of(BigInteger.valueOf(TestUtils.generateLong(String.valueOf(times))).toByteArray());
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_BTC_TX_HASH_PROCESSED_HEIGHT.encode(new Object[]{ByteUtil.toHexString(hash.getBytes())});
        executeAndAverage("getBtcTxHashProcessedHeight-notProcessed", times, abiEncoder, buildInitializer(), Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10), stats);
    }

    private BridgeStorageProviderInitializer buildInitializer() {
        final int minHashes = 100;
        final int maxHashes = 10000;
        final int minHeight = 1000;
        final int maxHeight = 2000;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            int hashesToGenerate = Helper.randomInRange(minHashes, maxHashes);
            int randomHashIndex = Helper.randomInRange(0, hashesToGenerate-1);
            Random rnd = new Random(provider.hashCode());
            for (int i = 0; i < hashesToGenerate; i++) {
                Sha256Hash hash = Sha256Hash.of(BigInteger.valueOf(rnd.nextLong()).toByteArray());
                long height = Helper.randomInRange(minHeight, maxHeight);

                try {
                    provider.setHeightBtcTxhashAlreadyProcessed(hash, height);
                } catch (IOException e) {
                    throw new RuntimeException("Exception trying to gather hashes already processed for benchmarking");
                }
                if (i == randomHashIndex) {
                    randomHashInMap = hash;
                }
            }
        };
    }
}
