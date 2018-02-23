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
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Ignore
public class ActiveFederationTest extends BridgePerformanceTestCase {
    private Federation federation;

    @Test
    public void getFederationAddress() throws IOException {
        executeTestCase(Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    public void getFederationSize() throws IOException {
        executeTestCase(Bridge.GET_FEDERATION_SIZE);
    }

    @Test
    public void getFederationThreshold() throws IOException {
        executeTestCase(Bridge.GET_FEDERATION_THRESHOLD);
    }

    @Test
    public void getFederationCreationTime() throws IOException {
        executeTestCase(Bridge.GET_FEDERATION_CREATION_TIME);
    }

    @Test
    public void getFederationCreationBlockNumber() throws IOException {
        executeTestCase(Bridge.GET_FEDERATION_CREATION_BLOCK_NUMBER);
    }

    @Test
    public void getFederatorPublicKey() throws IOException {
        ExecutionStats stats = new ExecutionStats("getFederatorPublicKey");
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, federation.getBtcPublicKeys().size()-1)});
        executeTestCaseSection(abiEncoder, "getFederatorPublicKey", true,50, stats);
        executeTestCaseSection(abiEncoder, "getFederatorPublicKey", false,500, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCase(CallTransaction.Function fn) {
        ExecutionStats stats = new ExecutionStats(fn.name);
        executeTestCaseSection(fn,true,50, stats);
        executeTestCaseSection(fn,false,500, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCaseSection(CallTransaction.Function fn, boolean genesis, int times, ExecutionStats stats) {
        executeTestCaseSection((int executionIndex) -> fn.encode(), fn.name, genesis, times, stats);
    }

    private void executeTestCaseSection(ABIEncoder abiEncoder, String name, boolean genesis, int times, ExecutionStats stats) {
        executeAndAverage(
                String.format("%s-%s", name, genesis ? "genesis" : "non-genesis"),
                times, abiEncoder,
                buildInitializer(genesis),
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer(boolean genesis) {
        final int minFederators = 10;
        final int maxFederators = 16;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            if (!genesis) {
                int numFederators = Helper.randomInRange(minFederators, maxFederators);
                List<BtcECKey> federatorKeys = new ArrayList<>();
                for (int i = 0; i < numFederators; i++) {
                    federatorKeys.add(new BtcECKey());
                }
                federation = new Federation(
                        federatorKeys,
                        Instant.ofEpochMilli(new Random().nextLong()),
                        Helper.randomInRange(1, 10),
                        networkParameters
                );
                provider.setNewFederation(federation);
            } else {
                federation = bridgeConstants.getGenesisFederation();
            }
        };
    }


}
