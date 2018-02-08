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
import co.rsk.peg.Bridge;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@Ignore
public class VoteFeePerKbChangeTest extends BridgePerformanceTestCase {

    @Test
    public void voteFeePerKbChange() throws IOException {
        BridgeStorageProviderInitializer storageInitializer = Helper.buildNoopInitializer();

        ABIEncoder abiEncoder = (int executionIndex) -> Bridge.VOTE_FEE_PER_KB.encode(BigInteger.valueOf(Helper.randomCoin(Coin.MILLICOIN, 1, 100).getValue()));

        TxBuilder txBuilder = (int executionIndex) -> {
            String generator = "auth-fee-per-kb";
            ECKey sender = ECKey.fromPrivate(HashUtil.keccak256(generator.getBytes(StandardCharsets.UTF_8)));

            return Helper.buildTx(sender);
        };

        ExecutionStats stats = new ExecutionStats("voteFeePerKbChange");
        executeAndAverage("voteFeePerKbChange", 1000, abiEncoder, storageInitializer, txBuilder, Helper.getRandomHeightProvider(10), stats);

        BridgePerformanceTest.addStats(stats);
    }
}
