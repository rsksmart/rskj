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
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.PendingFederation;
import co.rsk.peg.utils.PegUtils;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class PendingFederationTest extends BridgePerformanceTestCase {
    private PendingFederation pendingFederation;
    private final BridgeSerializationUtils bridgeSerializationUtils = PegUtils.getInstance().getBridgeSerializationUtils();

    @Test
    public void getPendingFederationHash() throws VMException {
        executeTestCase(Bridge.GET_PENDING_FEDERATION_HASH);
    }

    @Test
    public void getPendingFederationSize() throws VMException {
        executeTestCase(Bridge.GET_PENDING_FEDERATION_SIZE);
    }

    @Test
    public void getPendingFederatorPublicKey() throws VMException {
        ExecutionStats stats = new ExecutionStats("getPendingFederatorPublicKey");
        ABIEncoder abiEncoder;
        abiEncoder = (int executionIndex) -> Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, pendingFederation.getBtcPublicKeys().size()-1)});
        executeTestCaseSection(abiEncoder, "getPendingFederatorPublicKey", true,200, stats);
        abiEncoder = (int executionIndex) -> Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY.encode(new Object[]{Helper.randomInRange(0, 10)});
        executeTestCaseSection(abiEncoder, "getPendingFederatorPublicKey", false,200, stats);

        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void executeTestCase(CallTransaction.Function fn) throws VMException {
        ExecutionStats stats = new ExecutionStats(fn.name);
        executeTestCaseSection(fn,true,200, stats);
        executeTestCaseSection(fn,false,200, stats);

        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
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
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer(boolean present) {
        final int minFederators = 10;
        final int maxFederators = 16;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            if (present) {
                int numFederators = Helper.randomInRange(minFederators, maxFederators);
                pendingFederation = new PendingFederation(ActiveFederationTest.getNRandomFederationMembers(numFederators), bridgeSerializationUtils);
                provider.setPendingFederation(pendingFederation);
            } else {
                pendingFederation = null;
            }
        };
    }
}
