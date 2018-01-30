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
import co.rsk.core.commons.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.PegTestUtils;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

@Ignore
public class StateForBtcReleaseClientTest extends BridgePerformanceTestCase {
    @Test
    public void getStateForBtcReleaseClient() {
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

        BridgePerformanceTest.addStats(stats);
    }

    private BridgeStorageProviderInitializer getInitializer() {
        final int minNumTxs = 1;
        final int maxNumTxs = 100;

        final int minNumInputs = 1;
        final int maxNumInputs = 10;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            Map<Keccak256, BtcTransaction> txsWaitingForSignatures;
            try {
                txsWaitingForSignatures = provider.getRskTxsWaitingForSignatures();
            } catch (IOException e) {
                throw new RuntimeException("Exception while trying to gather txs waiting for signatures for storage initialization");
            }

            int numTxs = Helper.randomInRange(minNumTxs, maxNumTxs);
            for (int i = 0; i < numTxs; i++) {
                BtcTransaction releaseTx = new BtcTransaction(networkParameters);

                Federation federation = bridgeConstants.getGenesisFederation();

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


                Keccak256 rskTxHash = new Keccak256(HashUtil.keccak256(BigInteger.valueOf(new Random().nextLong()).toByteArray()));
                txsWaitingForSignatures.put(rskTxHash, releaseTx);
            }
        };
    }
}
