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
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;

@Ignore
public class BigIntegerModexpPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    @Test
    public void BigIntegerModexp() {
        CombinedExecutionStats stats = new CombinedExecutionStats("BigIntegerModexp");
        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) ->
                EnvironmentBuilder.Environment.withContract(new PrecompiledContracts.BigIntegerModexp());

        stats.add(doBigIntegerModexp(environmentBuilder, 100, 1,1,1));
        stats.add(doBigIntegerModexp(environmentBuilder, 100, 100, 8, 2));
        stats.add(doBigIntegerModexp(environmentBuilder, 100, 429, 37, 15));
        stats.add(doBigIntegerModexp(environmentBuilder, 100, 5439, 3547, 33));


        EthPrecompiledPerformanceTest.addStats(stats);
    }

    private ExecutionStats doBigIntegerModexp(EnvironmentBuilder environmentBuilder, int numCases, int baseLen, int expLen, int modLen) {
        ABIEncoder abiEncoder = (int executionIndex) -> {
            byte[] result = new byte[96];

            System.arraycopy(ByteUtil.bigIntegerToBytes(BigInteger.valueOf(baseLen),32), 0, result, 0, 32);
            System.arraycopy(ByteUtil.bigIntegerToBytes(BigInteger.valueOf(expLen),32), 0, result, 32, 32);
            System.arraycopy(ByteUtil.bigIntegerToBytes(BigInteger.valueOf(modLen),32), 0, result, 64, 32);
            return result;
        };

        String testName = String.format("BigIntegerModexp baseLen:%d expLen:%d modLen:%d", baseLen, expLen, modLen);
        ExecutionStats stats = new ExecutionStats(testName);

        executeAndAverage(
                testName,
                numCases,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                null
        );
        return stats;
    }
}
