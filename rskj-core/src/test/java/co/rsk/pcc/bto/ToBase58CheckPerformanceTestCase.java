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
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Random;

@Disabled
class ToBase58CheckPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    private static final int MIN_ADDRESS_LENGTH = 26;
    private static final int MAX_ADDRESS_LENGTH = 35;

    private CallTransaction.Function function;

    @Test
    void toBase58Check() throws VMException {
        function = new ToBase58Check(null).getFunction();

        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) -> {
            HDWalletUtils contract = new HDWalletUtils(new TestSystemProperties().getActivationConfig(), PrecompiledContracts.HD_WALLET_UTILS_ADDR);
            contract.init(txBuilder.build(executionIndex), Helper.getMockBlock(1), null, null, null, null);

            return EnvironmentBuilder.Environment.withContract(contract);
        };

        HDWalletUtilsPerformanceTest.addStats(estimateToBase58Check(2000, environmentBuilder));
    }

    private ExecutionStats estimateToBase58Check(int times, EnvironmentBuilder environmentBuilder) throws VMException {
        String name = function.name;
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

            return function.encode(new Object[] { hash, version });
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
                    Assertions.assertTrue(MIN_ADDRESS_LENGTH <= address.length());
                    Assertions.assertTrue(MAX_ADDRESS_LENGTH >= address.length());
                }
        );

        return stats;
    }
}
