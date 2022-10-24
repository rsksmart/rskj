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

package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.crypto.DeterministicKey;
import co.rsk.bitcoinj.crypto.HDKeyDerivation;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.performance.CombinedExecutionStats;
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

@Disabled
class DeriveExtendedPublicKeyPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    private static final int MAX_CHILD = (2 << 30) - 1;

    private CallTransaction.Function function;

    @Test
    void deriveExtendedPublicKey() throws VMException {
        function = new DeriveExtendedPublicKey(null, null).getFunction();

        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) -> {
            HDWalletUtils contract = new HDWalletUtils(new TestSystemProperties().getActivationConfig(), PrecompiledContracts.HD_WALLET_UTILS_ADDR);
            contract.init(txBuilder.build(executionIndex), Helper.getMockBlock(1), null, null, null, null);

            return EnvironmentBuilder.Environment.withContract(contract);
        };

        // Get rid of outliers by executing some cases beforehand
        setQuietMode(true);
        System.out.print("Doing an initial pass... ");
        estimateDeriveExtendedPublicKey(100, 1, environmentBuilder);
        System.out.print("Done!\n");
        setQuietMode(false);

        CombinedExecutionStats stats = new CombinedExecutionStats(function.name);

        stats.add(estimateDeriveExtendedPublicKey(500, 1, environmentBuilder));
        stats.add(estimateDeriveExtendedPublicKey(2000, 2, environmentBuilder));
        stats.add(estimateDeriveExtendedPublicKey(500, 4, environmentBuilder));
        stats.add(estimateDeriveExtendedPublicKey(500, 10, environmentBuilder));

        HDWalletUtilsPerformanceTest.addStats(stats);
    }

    private ExecutionStats estimateDeriveExtendedPublicKey(int times, int pathLength, EnvironmentBuilder environmentBuilder) throws VMException {
        String name = String.format("%s-%d", function.name, pathLength);
        ExecutionStats stats = new ExecutionStats(name);
        Random rnd = new Random();
        byte[] chainCode = new byte[32];
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ABIEncoder abiEncoder = (int executionIndex) -> {
            rnd.nextBytes(chainCode);
            DeterministicKey key = HDKeyDerivation.createMasterPubKeyFromBytes(
                    new ECKey().getPubKey(true),
                    chainCode
            );
            int[] pathParts = new int[pathLength];
            for (int i = 0; i < pathLength; i++) {
                pathParts[i] = rnd.nextInt(MAX_CHILD);
            }
            String path = String.join("/", Arrays.stream(pathParts)
                    .mapToObj(i -> String.format("%d", i)).collect(Collectors.toList()));

            return function.encode(new Object[] {
                    key.serializePubB58(networkParameters), path
            });
        };

        executeAndAverage(
                name,
                times,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    Object[] decodedResult = function.decodeResult(result);
                    Assertions.assertEquals(String.class, decodedResult[0].getClass());
                    String address = (String) decodedResult[0];
                    Assertions.assertTrue(address.startsWith("xpub"));
                }
        );

        return stats;
    }
}
