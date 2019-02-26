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
import co.rsk.db.BenchmarkedRepository;
import co.rsk.peg.performance.CombinedExecutionStats;
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

@Ignore
public class DeriveExtendedPublicKeyPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    private static final int MAX_CHILD = (2 << 30) - 1;

    private CallTransaction.Function deriveExtendedPublicKeyFunction;

    @Test
    public void deriveExtendedPublicKey() throws IOException {
        deriveExtendedPublicKeyFunction = new DeriveExtendedPublicKey(null, null).getFunction();

        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder() {
            @Override
            public Environment initialize(int executionIndex, Transaction tx, int height) {
                BTOUtils contract = new BTOUtils(new TestSystemProperties(), PrecompiledContracts.BTOUTILS_ADDR);
                contract.init(tx, Helper.getMockBlock(1), null, null, null, null);

                return new Environment(
                        contract,
                        () -> new BenchmarkedRepository.Statistics()
                );
            }

            @Override
            public void teardown() {
            }
        };

        // Get rid of outliers by executing some cases beforehand
        setQuietMode(true);
        System.out.print("Doing an initial pass... ");
        estimateDeriveExtendedPublicKey(100, 1, environmentBuilder);
        System.out.print("Done!\n");
        setQuietMode(false);

        CombinedExecutionStats stats = new CombinedExecutionStats("deriveExtendedPublicKey");

        stats.add(estimateDeriveExtendedPublicKey(500, 1, environmentBuilder));
        stats.add(estimateDeriveExtendedPublicKey(2000, 2, environmentBuilder));
        stats.add(estimateDeriveExtendedPublicKey(500, 4, environmentBuilder));

        BTOUtilsPerformanceTest.addStats(stats);
    }

    private ExecutionStats estimateDeriveExtendedPublicKey(int times, int pathLength, EnvironmentBuilder environmentBuilder) {
        String name = String.format("deriveExtendedPublicKey-%d", pathLength);
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

            return deriveExtendedPublicKeyFunction.encode(new Object[] {
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
                (byte[] result) -> {
                    Object[] decodedResult = deriveExtendedPublicKeyFunction.decodeResult(result);
                    Assert.assertEquals(String.class, decodedResult[0].getClass());
                    String address = (String) decodedResult[0];
                    Assert.assertTrue(address.startsWith("xpub"));
                }
        );

        return stats;
    }
}
