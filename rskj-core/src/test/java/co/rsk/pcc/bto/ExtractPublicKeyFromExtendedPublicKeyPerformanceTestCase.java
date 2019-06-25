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
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import java.util.Random;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

@Ignore
public class ExtractPublicKeyFromExtendedPublicKeyPerformanceTestCase
        extends PrecompiledContractPerformanceTestCase {
    private CallTransaction.Function function;

    @Test
    public void extractPublicKeyFromExtendedPublicKey() {
        function = new ExtractPublicKeyFromExtendedPublicKey(null, null).getFunction();

        EnvironmentBuilder environmentBuilder =
                (int executionIndex, TxBuilder txBuilder, int height) -> {
                    HDWalletUtils contract =
                            new HDWalletUtils(
                                    new TestSystemProperties().getActivationConfig(),
                                    PrecompiledContracts.HD_WALLET_UTILS_ADDR);
                    contract.init(
                            txBuilder.build(executionIndex),
                            Helper.getMockBlock(1),
                            null,
                            null,
                            null,
                            null);

                    return EnvironmentBuilder.Environment.withContract(contract);
                };

        HDWalletUtilsPerformanceTest.addStats(
                estimateExtractPublicKeyFromExtendedPublicKey(500, environmentBuilder));
    }

    private ExecutionStats estimateExtractPublicKeyFromExtendedPublicKey(
            int times, EnvironmentBuilder environmentBuilder) {
        ExecutionStats stats = new ExecutionStats(function.name);
        Random rnd = new Random();
        byte[] chainCode = new byte[32];
        NetworkParameters networkParameters =
                NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        byte[] publicKey = new ECKey().getPubKey(true);
        String expectedHexPublicKey = Hex.toHexString(publicKey);

        ABIEncoder abiEncoder =
                (int executionIndex) -> {
                    rnd.nextBytes(chainCode);
                    DeterministicKey key =
                            HDKeyDerivation.createMasterPubKeyFromBytes(publicKey, chainCode);

                    return function.encode(new Object[] {key.serializePubB58(networkParameters)});
                };

        executeAndAverage(
                function.name,
                times,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    Object[] decodedResult = function.decodeResult(result);
                    Assert.assertEquals(byte[].class, decodedResult[0].getClass());
                    String hexPublicKey = Hex.toHexString((byte[]) decodedResult[0]);
                    Assert.assertEquals(expectedHexPublicKey, hexPublicKey);
                });

        return stats;
    }
}
