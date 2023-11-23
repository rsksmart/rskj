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

import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.StandardMultisigFederation;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;

@Disabled
class RetiringFederationTest extends BridgePerformanceTestCase {
    private Federation retiringFederation;

    @Test
    void getRetiringFederationAddress() throws VMException {
        executeTestCase(Bridge.GET_RETIRING_FEDERATION_ADDRESS);
    }

    @Test
    void getRetiringFederationSize() throws VMException {
        executeTestCase(Bridge.GET_RETIRING_FEDERATION_SIZE);
    }

    @Test
    void getRetiringFederationThreshold() throws VMException {
        executeTestCase(Bridge.GET_RETIRING_FEDERATION_THRESHOLD);
    }

    @Test
    void getRetiringFederationCreationTime() throws VMException {
        executeTestCase(Bridge.GET_RETIRING_FEDERATION_CREATION_TIME);
    }

    @Test
    void getRetiringFederationCreationBlockNumber() throws VMException {
        executeTestCase(Bridge.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER);
    }

    @Test
    void getRetiringFederatorPublicKey() throws VMException {
        ExecutionStats stats = new ExecutionStats("getRetiringFederatorPublicKey");
        ABIEncoder abiEncoder;
        abiEncoder = (int executionIndex) -> Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, retiringFederation.getBtcPublicKeys().size()-1)});
        executeTestCaseSection(abiEncoder, "getRetiringFederatorPublicKey", true,50, stats);
        abiEncoder = (int executionIndex) -> Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, 10)});
        executeTestCaseSection(abiEncoder, "getRetiringFederatorPublicKey", false,500, stats);

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

    private void executeTestCaseSection(ABIEncoder abiEncoder, String name, boolean present, int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                String.format("%s-%s", name, present ? "present" : "not-present"),
                times, abiEncoder,
                buildInitializer(present),
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(11, 15),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer(boolean present) {
        final int minFederators = 10;
        final int maxFederators = 16;
        Random random = new Random(RetiringFederationTest.class.hashCode());
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            if (present) {
                int numFederators = Helper.randomInRange(minFederators, maxFederators);
                retiringFederation = new StandardMultisigFederation(
                        ActiveFederationTest.getNRandomFederationMembers(numFederators),
                        Instant.ofEpochMilli(random.nextLong()),
                        Helper.randomInRange(1, 10),
                        networkParameters
                );
                provider.setNewFederation(bridgeConstants.getGenesisFederation());
                provider.setOldFederation(retiringFederation);
            } else {
                retiringFederation = null;
            }
        };
    }


}
