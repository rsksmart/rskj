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
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbTestNetConstants;
import co.rsk.peg.lockingcap.constants.LockingCapTestNetConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import co.rsk.peg.whitelist.constants.WhitelistTestNetConstants;

public class BridgeTestNetConstants extends BridgeConstants {
    private static final BridgeTestNetConstants instance = new BridgeTestNetConstants();

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;
        feePerKbConstants = FeePerKbTestNetConstants.getInstance();
        whitelistConstants = WhitelistTestNetConstants.getInstance();
        federationConstants = FederationTestNetConstants.getInstance();
        lockingCapConstants = LockingCapTestNetConstants.getInstance();
        unionBridgeConstants = UnionBridgeTestNetConstants.getInstance();

        btc2RskMinimumAcceptableConfirmations = 5;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        btcHeightWhenBlockIndexActivates = 2_039_594;
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 60; // 30 minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 2_589_553; // Estimated date Wed, 20 Mar 2024 15:00:00 GMT. 2,579,823 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 1_440; // 10 days in BTC blocks (considering 1 block every 10 minutes)

        blockWithTooMuchChainWorkHeight = Integer.MAX_VALUE;
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }
}
