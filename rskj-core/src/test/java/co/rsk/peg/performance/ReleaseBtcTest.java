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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.ReleaseRequestQueue;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.ethereum.core.Denomination;
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
public class ReleaseBtcTest extends BridgePerformanceTestCase {
    private final ScriptBuilderWrapper scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();
    private final BridgeSerializationUtils bridgeSerializationUtils = BridgeSerializationUtils.getInstance(scriptBuilderWrapper);

    @Test
    public void releaseBtc() throws VMException {

        ExecutionStats stats = new ExecutionStats("releaseBtc");
        releaseBtc_success(1000, stats);
        releaseBtc_refund(500, stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void releaseBtc_success(int times, ExecutionStats stats) throws VMException {
        int minCentsBtc = 5;
        int maxCentsBtc = 100;
        int queueSizeOriginal = Helper.randomInRange(10, 100);


        BridgeStorageProviderInitializer storageInitializer = generateInitializer(minCentsBtc, maxCentsBtc, queueSizeOriginal);

        TxBuilder txBuilder = (int executionIndex) -> {
            long satoshis = Coin.CENT.multiply(Helper.randomInRange(minCentsBtc, maxCentsBtc)).getValue();
            BigInteger weis = Denomination.satoshisToWeis(BigInteger.valueOf(satoshis));
            ECKey sender = new ECKey();

            return Helper.buildSendValueTx(sender, weis);
        };

        executeAndAverage(
                "releaseBtc_success",
                times,
                getABIEncoder(),
                storageInitializer,
                txBuilder,
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    int sizeQueue = -1;
                    try {
                        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                                (Repository) environment.getBenchmarkedRepository(),
                                PrecompiledContracts.BRIDGE_ADDR,
                                constants.getBridgeConstants(),
                                activationConfig.forBlock(0),
                                bridgeSerializationUtils
                        );
                        ReleaseRequestQueue queue = bridgeStorageProvider.getReleaseRequestQueue();
                        sizeQueue = queue.getEntries().size();
                    } catch (IOException e) {
                        Assert.fail();
                    }
                    Assert.assertEquals(queueSizeOriginal + 1, sizeQueue);
                }
        );
    }

    private void releaseBtc_refund(int times, ExecutionStats stats) throws VMException {
        int minCentsBtc = 6;
        int maxCentsBtc = 10;
        int queueSizeOriginal = Helper.randomInRange(10, 100);

        BridgeStorageProviderInitializer storageInitializer = generateInitializer(minCentsBtc, maxCentsBtc, queueSizeOriginal);

        TxBuilder txBuilder = (int executionIndex) -> {
            long satoshis = Coin.CENT.divide(Helper.randomInRange(minCentsBtc, maxCentsBtc)).getValue();
            BigInteger weis = Denomination.satoshisToWeis(BigInteger.valueOf(satoshis));
            ECKey sender = new ECKey();

            return Helper.buildSendValueTx(sender, weis);
        };

        executeAndAverage(
                "releaseBtc_refund",
                times,
                getABIEncoder(),
                storageInitializer,
                txBuilder,
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                        int sizeQueue = -1;
                        try {
                            BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                                    (Repository) environment.getBenchmarkedRepository(),
                                    PrecompiledContracts.BRIDGE_ADDR,
                                    constants.getBridgeConstants(),
                                    activationConfig.forBlock(0),
                                    bridgeSerializationUtils
                            );
                            ReleaseRequestQueue queue = bridgeStorageProvider.getReleaseRequestQueue();
                            sizeQueue = queue.getEntries().size();
                        } catch (IOException e) {
                            Assert.fail();
                        }
                       Assert.assertEquals(queueSizeOriginal, sizeQueue);
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.RELEASE_BTC.encode();
    }

    private BridgeStorageProviderInitializer generateInitializer(int minCentsBtc, int maxCentsBtc, int releaseQueueSize) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            ReleaseRequestQueue queue;

            final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

            try {
                queue = provider.getReleaseRequestQueue();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release request queue");
            }

            for (int i = 0; i < releaseQueueSize; i++) {
                Coin value = Coin.CENT.multiply(Helper.randomInRange(minCentsBtc, maxCentsBtc));
                queue.add(new BtcECKey().toAddress(parameters), value, null);
            }
        };
    }
}
