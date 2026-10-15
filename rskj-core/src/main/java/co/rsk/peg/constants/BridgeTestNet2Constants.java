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
 * The other BTC height-gated activations are left at 0 because testnet4 is a fresh, short chain.
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
        historicalPegoutSelectionsConstants = new co.rsk.peg.pegout.HistoricalPegoutSelectionsConstants();

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = THREE_MINUTES_IN_MILLIS;
        minSecondsBetweenCallsReceiveHeader = FIVE_MINUTES_IN_SECONDS;

        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        legacyMaxInputsPerMigrationTransaction = 50;
        maxInputsPerMigrationTransaction = 150;
        maxOutputsPerMigrationTransaction = 50;
        migrationValueForMultipleOutputs = Coin.COIN.multiply(20);

        // The Bridge BTC block store starts at the last block in org.bitcoin.testnet4.checkpoints,
        // and the block index covers the store from that block on.
        // So no height below it is in the store, and there are no unindexed blocks to search for.
        btcHeightWhenBlockIndexActivates = 143_136;
        maxDepthToSearchBlocksBelowIndexActivation = 0;

        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        numberOfBlocksBetweenPegouts = THREE_HOURS_IN_RSK_BLOCKS;

        // Pegout tx index active since genesis
        btcHeightWhenPegoutTxIndexActivates = 0;
        pegoutTxIndexGracePeriodInBtcBlocks = 0;

        blockWithTooMuchChainWorkHeight = Integer.MAX_VALUE;
    }

    public static BridgeTestNet2Constants getInstance() {
        return instance;
    }
}
