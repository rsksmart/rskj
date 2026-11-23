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
import co.rsk.peg.pegout.HistoricalPegoutSelectionsTestNetConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import co.rsk.peg.whitelist.constants.WhitelistTestNetConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class BridgeTestNetConstants extends BridgeConstants {
    private static final BridgeTestNetConstants instance = new BridgeTestNetConstants();
    private final int preCardamomBtc2RskMinimumAcceptableConfirmations;
    private final int preCardamomRsk2BtcMinimumAcceptableConfirmations;

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;
        feePerKbConstants = FeePerKbTestNetConstants.getInstance();
        whitelistConstants = WhitelistTestNetConstants.getInstance();
        federationConstants = FederationTestNetConstants.getInstance();
        lockingCapConstants = LockingCapTestNetConstants.getInstance();
        historicalPegoutSelectionsConstants = HistoricalPegoutSelectionsTestNetConstants.getInstance();
        unionBridgeConstants = UnionBridgeTestNetConstants.getInstance();

        updateBridgeExecutionPeriod = THREE_MINUTES_IN_MILLIS;
        minSecondsBetweenCallsReceiveHeader = FIVE_MINUTES_IN_SECONDS;

        preCardamomBtc2RskMinimumAcceptableConfirmations = 10;
        btc2RskMinimumAcceptableConfirmations = 100;
        preCardamomRsk2BtcMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 100;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        legacyMaxInputsPerMigrationTransaction = 50;
        maxInputsPerMigrationTransaction = 150;
        maxOutputsPerMigrationTransaction = 50;
        migrationValueForMultipleOutputs = Coin.COIN.multiply(20);

        btcHeightWhenBlockIndexActivates = 2_039_594;
        maxDepthToSearchBlocksBelowIndexActivation = THIRTY_DAYS_IN_BTC_BLOCKS;

        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        numberOfBlocksBetweenPegouts = THREE_HOURS_IN_RSK_BLOCKS;

        btcHeightWhenPegoutTxIndexActivates = 2_589_553; // Estimated date Wed, 20 Mar 2024 15:00:00 GMT. 2,579,823 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 1_440; // 10 days in BTC blocks (considering 1 block every 10 minutes)

        blockWithTooMuchChainWorkHeight = Integer.MAX_VALUE;
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

    @Override
    public int getBtc2RskMinimumAcceptableConfirmations(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP643)) {
            return preCardamomBtc2RskMinimumAcceptableConfirmations;
        }

        return btc2RskMinimumAcceptableConfirmations;
    }

    @Override
    public int getRsk2BtcMinimumAcceptableConfirmations(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP643)) {
            return preCardamomRsk2BtcMinimumAcceptableConfirmations;
        }

        return rsk2BtcMinimumAcceptableConfirmations;
    }
}
