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

import co.rsk.crypto.Keccak256;
import co.rsk.peg.performance.CombinedExecutionStats;
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Optional;


@Ignore
public class ECRecoverPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    @Test
    public void ECRecover() {
        CombinedExecutionStats stats = new CombinedExecutionStats("ECRecover");
        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) ->
                EnvironmentBuilder.Environment.withContract(new PrecompiledContracts.ECRecover());

        Keccak256 message = new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb");
        stats.add(doECRecover(environmentBuilder, 100, message, new ECKey().sign(message.getBytes())));

        message = new Keccak256("aabbccddeeff667788990011223344556677889900112233445566778899ffcc");
        stats.add(doECRecover(environmentBuilder, 100, message, new ECKey().sign(message.getBytes())));

        EthPrecompiledPerformanceTest.addStats(stats);
    }

    private void warmUp(EnvironmentBuilder environmentBuilder) {
        // Get rid of outliers by executing some cases beforehand
        setQuietMode(true);
        System.out.print("Doing an initial pass... ");
        Keccak256 message = new Keccak256("AA1122334455339988990011223344556677889900112233445566778899aabb");
        doECRecover(environmentBuilder, 100, message, new ECKey().sign(message.getBytes()));
        System.out.print("Done!\n");
        setQuietMode(false);
    }

    private ExecutionStats doECRecover(EnvironmentBuilder environmentBuilder, int numCases, Keccak256 message, ECKey.ECDSASignature signature)  {
        ABIEncoder abiEncoder = (int executionIndex) -> {
            byte[] result = new byte[128];
            System.arraycopy(message.getBytes(), 0, result, 0, 32);
            byte[] vBytes = new byte[32];
            vBytes[31] = signature.v;
            System.arraycopy(vBytes, 0, result, 32, 32);
            System.arraycopy(signature.r.toByteArray(), 0, result, 64, 32);
            System.arraycopy(signature.s.toByteArray(), 0, result, 96, 32);

            return result;
        };


        String testName = String.format("ECRecover %s", message.toHexString());
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
                Optional.of(0.0)
        );
        return stats;
    }
}
