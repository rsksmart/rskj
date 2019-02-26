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

import co.rsk.config.TestSystemProperties;
import co.rsk.db.BenchmarkedRepository;
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
import java.util.Random;

@Ignore
public class ToBase58CheckPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    private static final int MIN_ADDRESS_LENGTH = 26;
    private static final int MAX_ADDRESS_LENGTH = 35;

    private CallTransaction.Function toBase58CheckFunction;

    @Test
    public void toBase58Check() throws IOException {
        toBase58CheckFunction = new ToBase58Check(null).getFunction();

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

        BTOUtilsPerformanceTest.addStats(estimateToBase58Check(2000, environmentBuilder));
    }

    private ExecutionStats estimateToBase58Check(int times, EnvironmentBuilder environmentBuilder) {
        String name = "toBase58Check";
        ExecutionStats stats = new ExecutionStats(name);
        Random rnd = new Random();
        int[] versions = new int[] {
                // Testnet and mainnet pubkey hash and script hash only
                // See https://en.bitcoin.it/wiki/Base58Check_encoding for details
                0, 5, 111, 196
        };
        byte[] hash = new byte[20];

        ABIEncoder abiEncoder = (int executionIndex) -> {
            rnd.nextBytes(hash);
            int version = versions[rnd.nextInt(versions.length)];

            return toBase58CheckFunction.encode(new Object[] { hash, version });
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
                    Object[] decodedResult = toBase58CheckFunction.decodeResult(result);
                    Assert.assertEquals(String.class, decodedResult[0].getClass());
                    String address = (String) decodedResult[0];
                    Assert.assertTrue(MIN_ADDRESS_LENGTH <= address.length());
                    Assert.assertTrue(MAX_ADDRESS_LENGTH >= address.length());
                }
        );

        return stats;
    }
}
