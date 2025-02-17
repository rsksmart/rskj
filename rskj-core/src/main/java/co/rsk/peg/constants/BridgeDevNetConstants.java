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

package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;

public class BridgeDevNetConstants extends BridgeConstants {
    public BridgeDevNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        btc2RskMinimumAcceptableConfirmations = 1;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 30000; // 30secs

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        btcHeightWhenBlockIndexActivates = 700_000;
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes

        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 20;

        maxInputsPerPegoutTransaction = 10;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 1_000_000;
        pegoutTxIndexGracePeriodInBtcBlocks = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)
    }
}
