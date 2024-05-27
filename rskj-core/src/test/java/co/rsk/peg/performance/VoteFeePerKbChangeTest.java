/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Disabled
class VoteFeePerKbChangeTest extends BridgePerformanceTestCase {

    @Test
    void voteFeePerKbChange() throws VMException {
        BridgeStorageProviderInitializer storageInitializer = Helper.buildNoopInitializer();

        AtomicReference<Long> newValue = new AtomicReference<>();
        ABIEncoder abiEncoder = (int executionIndex) -> {
            newValue.set(Helper.randomCoin(Coin.MILLICOIN, 1, 50).getValue());
            return Bridge.VOTE_FEE_PER_KB.encode(BigInteger.valueOf(newValue.get().longValue()));
        };

        TxBuilder txBuilder = (int executionIndex) -> {
            String generator = "auth-fee-per-kb";
            ECKey sender = ECKey.fromPrivate(HashUtil.keccak256(generator.getBytes(StandardCharsets.UTF_8)));

            return Helper.buildTx(sender);
        };

        ExecutionStats stats = new ExecutionStats("voteFeePerKbChange");
        executeAndAverage(
                "voteFeePerKbChange",
                1000,
                abiEncoder,
                storageInitializer,
                txBuilder,
                Helper.getRandomHeightProvider(10),
                stats,
                ((environment, callResult) -> {
                    Assertions.assertEquals(newValue.get().longValue(),((Bridge)environment.getContract()).getFeePerKb(null));
                }));

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    void voteFeePerKbChange_unauthorized() throws VMException {
        BridgeStorageProviderInitializer storageInitializer = Helper.buildNoopInitializer();

        Coin genesisFeePerKB = BridgeRegTestConstants.getInstance().getGenesisFeePerKb();
        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.VOTE_FEE_PER_KB.encode(BigInteger.valueOf(Helper.randomCoin(Coin.MILLICOIN, 1, 100).getValue()));

        TxBuilder txBuilder = (int executionIndex) -> {
            String generator = "unauthorized";
            ECKey sender = ECKey.fromPrivate(HashUtil.keccak256(generator.getBytes(StandardCharsets.UTF_8)));

            return Helper.buildTx(sender);
        };

        ExecutionStats stats = new ExecutionStats("voteFeePerKbChange_unauthorized");
        executeAndAverage(
                "voteFeePerKbChange_unauthorized",
                1000,
                abiEncoder,
                storageInitializer,
                txBuilder,
                Helper.getRandomHeightProvider(10),
                stats,
                ((environment, callResult) -> {
                    Assertions.assertEquals(genesisFeePerKB.getValue(),((Bridge)environment.getContract()).getFeePerKb(null));
                })
        );

        BridgePerformanceTest.addStats(stats);
    }
}
