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
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.TestUtils;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Disabled
class ActiveFederationTest extends BridgePerformanceTestCase {
    public static List<FederationMember> getNRandomFederationMembers(int n) {
        List<FederationMember> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
        }
        return result;
    }

    private Federation federation;

    @Test
    void getFederationAddress() throws IOException, VMException {
        executeTestCase(Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    void getFederationSize() throws IOException, VMException {
        executeTestCase(Bridge.GET_FEDERATION_SIZE);
    }

    @Test
    void getFederationThreshold() throws IOException, VMException {
        executeTestCase(Bridge.GET_FEDERATION_THRESHOLD);
    }

    @Test
    void getFederationCreationTime() throws IOException, VMException {
        executeTestCase(Bridge.GET_FEDERATION_CREATION_TIME);
    }

    @Test
    void getFederationCreationBlockNumber() throws IOException, VMException {
        executeTestCase(Bridge.GET_FEDERATION_CREATION_BLOCK_NUMBER);
    }

    @Test
    void getFederatorPublicKey() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("getFederatorPublicKey");
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.GET_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, federation.getBtcPublicKeys().size()-1)});
        executeTestCaseSection(abiEncoder, "getFederatorPublicKey", true,50, stats);
        executeTestCaseSection(abiEncoder, "getFederatorPublicKey", false,500, stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void executeTestCase(CallTransaction.Function fn) throws VMException {
        ExecutionStats stats = new ExecutionStats(fn.name);
        executeTestCaseSection(fn,true,50, stats);
        executeTestCaseSection(fn,false,500, stats);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void executeTestCaseSection(CallTransaction.Function fn, boolean genesis, int times, ExecutionStats stats) throws VMException {
        executeTestCaseSection((int executionIndex) -> fn.encode(), fn.name, genesis, times, stats);
    }

    private void executeTestCaseSection(ABIEncoder abiEncoder, String name, boolean genesis, int times, ExecutionStats stats) throws VMException {
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

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            if (!genesis) {
                int numFederators = Helper.randomInRange(minFederators, maxFederators);
                List<FederationMember> members = getNRandomFederationMembers(numFederators);

                federation = new Federation(
                        members,
                        Instant.ofEpochMilli(TestUtils.generateLong(String.valueOf(executionIndex))),
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
