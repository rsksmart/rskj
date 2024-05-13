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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.constants.FederationRegTestConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import co.rsk.peg.feeperkb.constants.FeePerKbRegTestConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class BridgeRegTestConstants extends BridgeConstants {

    private static final BridgeRegTestConstants instance = new BridgeRegTestConstants(FederationRegTestConstants.defaultGenesisFederationPublicKeys);

    public BridgeRegTestConstants(List<BtcECKey> federationPublicKeys) {
        btcParamsString = NetworkParameters.ID_REGTEST;
        feePerKbConstants = FeePerKbRegTestConstants.getInstance();
        federationConstants = new FederationRegTestConstants(federationPublicKeys);

        btc2RskMinimumAcceptableConfirmations = 3;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 3;

        updateBridgeExecutionPeriod = 15_000; //15 seconds in millis

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.COIN;
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPeginTxValue = Coin.COIN.div(2);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialLockingCap = Coin.COIN.multiply(1_000L); // 1_000 BTC

        lockingCapIncrementsMultiplier = 2;

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds

        maxDepthBlockchainAccepted = 25;

        // Key generated with GenNodeKey using generator 'auth-increase_locking_cap'
        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
            increaseLockingCapAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        btcHeightWhenBlockIndexActivates = 10;
        maxDepthToSearchBlocksBelowIndexActivation = 5;

        minimumPegoutValuePercentageToReceiveAfterFee = 20;

        maxInputsPerPegoutTransaction = 10;

        numberOfBlocksBetweenPegouts = 50; // 25 Minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 250;
        pegoutTxIndexGracePeriodInBtcBlocks = 100;
    }

    public static BridgeRegTestConstants getInstance() {
        return instance;
    }
}
