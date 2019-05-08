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

package co.rsk.peg.performance;

import co.rsk.db.BenchmarkedRepository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore
public class IdentityPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    @Test
    public void identity() throws IOException {
        ExecutionStats stats = new ExecutionStats("identity");

        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder() {
            @Override
            public Environment initialize(int executionIndex, Transaction tx, int height) {
                return new Environment(
                        new PrecompiledContracts.Identity(),
                        () -> new BenchmarkedRepository.Statistics()
                );
            }

            @Override
            public void teardown() {
            }
        };

        doIdentity(environmentBuilder, stats, 2000);

        IdentityPerformanceTest.addStats(stats);
    }

    private void doIdentity(EnvironmentBuilder environmentBuilder, ExecutionStats stats, int numCases) throws IOException {
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
