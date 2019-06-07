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

package co.rsk.peg.performance.eth.precompiled;

import co.rsk.peg.performance.CombinedExecutionStats;
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Optional;

@Ignore
public class Ripempd160PerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    @Test
    public void Ripempd160() {
        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) ->
                EnvironmentBuilder.Environment.withContract(new PrecompiledContracts.Ripempd160());
        warmUp(environmentBuilder);

        CombinedExecutionStats stats = new CombinedExecutionStats("Ripempd160");

        stats.add(doRipempd160(environmentBuilder, 100, new byte[]{}));

        stats.add(doRipempd160(environmentBuilder, 100, new byte[20]));

        stats.add(doRipempd160(environmentBuilder, 100, new byte[200]));

        stats.add(doRipempd160(environmentBuilder, 100, new byte[200000]));

        EthPrecompiledPerformanceTest.addStats(stats);
    }

    private void warmUp(EnvironmentBuilder environmentBuilder) {
        // Get rid of outliers by executing some cases beforehand
        setQuietMode(true);
        System.out.print("Doing an initial pass... ");
        doRipempd160(environmentBuilder, 100, new byte[]{});
        System.out.print("Done!\n");
        setQuietMode(false);
    }

    private ExecutionStats doRipempd160(EnvironmentBuilder environmentBuilder, int numCases, byte[] params) {
        ABIEncoder abiEncoder = (int executionIndex) -> params;

        String testName = String.format("Ripempd160 %d", params.length);
        ExecutionStats stats = new ExecutionStats(testName);

        executeAndAverage(
                testName,
                numCases,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                null,
                Optional.of(3.75)
        );
        return stats;
    }
}
