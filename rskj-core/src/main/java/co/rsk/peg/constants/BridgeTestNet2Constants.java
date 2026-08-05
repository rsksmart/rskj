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
import co.rsk.peg.federation.constants.FederationTestNet2Constants;
import co.rsk.peg.feeperkb.constants.FeePerKbTestNetConstants;
import co.rsk.peg.lockingcap.constants.LockingCapTestNetConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import co.rsk.peg.whitelist.constants.WhitelistTestNetConstants;

/**
 * Bridge constants for the RSK testnet2 network, which pegs to Bitcoin testnet4.
 * Modeled on {@link BridgeTestNetConstants}; the key difference is that the Bitcoin network is
 * testnet4 ({@code NetworkParameters.ID_TESTNET4}) and the federation is {@link FederationTestNet2Constants}.
 * The BTC height-gated activations are set to 0 because testnet4 is a fresh, short chain.
 */
public class BridgeTestNet2Constants extends BridgeConstants {
    private static final BridgeTestNet2Constants instance = new BridgeTestNet2Constants();

    BridgeTestNet2Constants() {
        btcParamsString = NetworkParameters.ID_TESTNET4;
        feePerKbConstants = FeePerKbTestNetConstants.getInstance();
        whitelistConstants = WhitelistTestNetConstants.getInstance();
        federationConstants = FederationTestNet2Constants.getInstance();
        lockingCapConstants = LockingCapTestNetConstants.getInstance();
        unionBridgeConstants = UnionBridgeTestNetConstants.getInstance();

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (1 block every 30 seconds)

        blockWithTooMuchChainWorkHeight = Integer.MAX_VALUE;
    }

    public static BridgeTestNet2Constants getInstance() {
        return instance;
    }
}
