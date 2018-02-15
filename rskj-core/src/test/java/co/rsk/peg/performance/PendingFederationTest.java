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
import co.rsk.peg.PendingFederation;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Ignore
public class PendingFederationTest extends BridgePerformanceTestCase {
    private PendingFederation pendingFederation;

    @Test
    public void getPendingFederationHash() throws IOException {
        executeTestCase(Bridge.GET_PENDING_FEDERATION_HASH);
    }

    @Test
    public void getPendingFederationSize() throws IOException {
        executeTestCase(Bridge.GET_PENDING_FEDERATION_SIZE);
    }

    @Test
    public void getPendingFederatorPublicKey() throws IOException {
        ExecutionStats stats = new ExecutionStats("getPendingFederatorPublicKey");
        ABIEncoder abiEncoder;
        abiEncoder = (int executionIndex) -> Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, pendingFederation.getBtcPublicKeys().size()-1)});
        executeTestCaseSection(abiEncoder, "getPendingFederatorPublicKey", true,200, stats);
        abiEncoder = (int executionIndex) -> Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, 10)});
        executeTestCaseSection(abiEncoder, "getPendingFederatorPublicKey", false,200, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCase(CallTransaction.Function fn) {
        ExecutionStats stats = new ExecutionStats(fn.name);
        executeTestCaseSection(fn,true,200, stats);
        executeTestCaseSection(fn,false,200, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCaseSection(CallTransaction.Function fn, boolean genesis, int times, ExecutionStats stats) {
        executeTestCaseSection((int executionIndex) -> fn.encode(), fn.name, genesis, times, stats);
    }

    private void executeTestCaseSection(ABIEncoder abiEncoder, String name, boolean present, int times, ExecutionStats stats) {
        executeAndAverage(
                String.format("%s-%s", name, present ? "present" : "not-present"),
                times, abiEncoder,
                buildInitializer(present),
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer(boolean present) {
        final int minFederators = 10;
        final int maxFederators = 16;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            if (present) {
                int numFederators = Helper.randomInRange(minFederators, maxFederators);
                List<BtcECKey> federatorKeys = new ArrayList<>();
                for (int i = 0; i < numFederators; i++) {
                    federatorKeys.add(new BtcECKey());
                }
                pendingFederation = new PendingFederation(federatorKeys);
                provider.setPendingFederation(pendingFederation);
            } else {
                pendingFederation = null;
            }
        };
    }


}
