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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.TestSystemProperties;
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
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

@Ignore
public class GetMultisigScriptHashPerformanceTestCase extends PrecompiledContractPerformanceTestCase {
    private CallTransaction.Function function;

    @Test
    public void getMultisigScriptHash() throws IOException {
        function = new GetMultisigScriptHash(null).getFunction();

        EnvironmentBuilder environmentBuilder = (int executionIndex, Transaction tx, int height) -> {
            BTOUtils contract = new BTOUtils(new TestSystemProperties(), PrecompiledContracts.BTOUTILS_ADDR);
            contract.init(tx, Helper.getMockBlock(1), null, null, null, null);

            return EnvironmentBuilder.Environment.withContract(contract);
        };

        // Get rid of outliers by executing some cases beforehand
        setQuietMode(true);
        System.out.print("Doing an initial pass... ");
        estimateGetMultisigScriptHash(100, 15, environmentBuilder);
        System.out.print("Done!\n");
        setQuietMode(false);

        CombinedExecutionStats stats = new CombinedExecutionStats(function.name);

        stats.add(estimateGetMultisigScriptHash(500, 2, environmentBuilder));
        stats.add(estimateGetMultisigScriptHash(2000, 8, environmentBuilder));
        stats.add(estimateGetMultisigScriptHash(1000, 15, environmentBuilder));

        BTOUtilsPerformanceTest.addStats(stats);
    }

    private ExecutionStats estimateGetMultisigScriptHash(int times, int numberOfKeys, EnvironmentBuilder environmentBuilder) {
        String name = String.format("%s-%d", function.name, numberOfKeys);
        ExecutionStats stats = new ExecutionStats(name);
        Random rnd = new Random();

        int minimumSignatures = rnd.nextInt(numberOfKeys) + 1;
        byte[][] publicKeys = new byte[numberOfKeys][];
        for (int i = 0; i < numberOfKeys; i++) {
            publicKeys[i] = new ECKey().getPubKey(true);
        }

        String expectedHashHex = Hex.toHexString(ScriptBuilder.createP2SHOutputScript(
                minimumSignatures,
                Arrays.stream(publicKeys).map(pk -> BtcECKey.fromPublicOnly(pk)).collect(Collectors.toList())
        ).getPubKeyHash());

        ABIEncoder abiEncoder = (int executionIndex) -> function.encode(new Object[]{
                minimumSignatures, publicKeys
        });

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
                    Assert.assertEquals(byte[].class, decodedResult[0].getClass());
                    String hexHash = Hex.toHexString((byte[]) decodedResult[0]);
                    Assert.assertEquals(expectedHashHex, hexHash);
                }
        );

        return stats;
    }
}
