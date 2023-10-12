/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.bridge.performance;

import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Disabled
class IdentityPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    @Test
    void identity() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("identity");

        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) ->
                EnvironmentBuilder.Environment.withContract(new PrecompiledContracts.Identity());

        doIdentity(environmentBuilder, stats, 2000);

        Assertions.assertTrue(IdentityPerformanceTest.addStats(stats));
    }

    private void doIdentity(EnvironmentBuilder environmentBuilder, ExecutionStats stats, int numCases) throws IOException, VMException {
        ABIEncoder abiEncoder = (int executionIndex) -> new byte[]{};

        executeAndAverage(
                "identity",
                numCases,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                null
        );
    }
}
