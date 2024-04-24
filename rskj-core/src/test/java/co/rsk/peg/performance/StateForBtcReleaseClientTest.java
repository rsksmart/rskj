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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.federation.FederationTestUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

@Disabled
class StateForBtcReleaseClientTest extends BridgePerformanceTestCase {
    @Test
    void getStateForBtcReleaseClient() throws VMException {
        ExecutionStats stats = new ExecutionStats("getStateForBtcReleaseClient");

        executeAndAverage(
                "getStateForBtcReleaseClient",
                200,
                (int executionIndex) -> Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT.encode(),
                getInitializer(),
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats
        );

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private BridgeStorageProviderInitializer getInitializer() {
        final int minNumTxs = 1;
        final int maxNumTxs = 100;

        final int minNumInputs = 1;
        final int maxNumInputs = 10;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Map<Keccak256, BtcTransaction> txsWaitingForSignatures;
            try {
                txsWaitingForSignatures = provider.getPegoutsWaitingForSignatures();
            } catch (IOException e) {
                throw new RuntimeException("Exception while trying to gather txs waiting for signatures for storage initialization");
            }

            int numTxs = Helper.randomInRange(minNumTxs, maxNumTxs);
            Federation federation = FederationTestUtils.getGenesisFederation(bridgeConstants);
            for (int i = 0; i < numTxs; i++) {
                BtcTransaction releaseTx = new BtcTransaction(networkParameters);

                // Receiver and amounts
                Address toAddress = new BtcECKey().toAddress(networkParameters);
                Coin releaseAmount = Coin.CENT.multiply(Helper.randomInRange(10, 100));

                releaseTx.addOutput(releaseAmount, toAddress);

                // Input generation
                int numInputs = Helper.randomInRange(minNumInputs, maxNumInputs);
                for (int j = 0; j < numInputs; j++) {
                    Coin inputAmount = releaseAmount.divide(numInputs);
                    BtcTransaction inputTx = new BtcTransaction(networkParameters);
                    inputTx.addOutput(inputAmount, federation.getAddress());
                    releaseTx
                            .addInput(inputTx.getOutput(0))
                            .setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
                }


                Keccak256 rskTxHash = TestUtils.generateHash("rskTxHash");
                txsWaitingForSignatures.put(rskTxHash, releaseTx);
            }
        };
    }
}
