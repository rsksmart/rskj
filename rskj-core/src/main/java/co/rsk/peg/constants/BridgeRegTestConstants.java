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
import co.rsk.peg.feeperkb.constants.FeePerKbRegTestConstants;
import co.rsk.peg.lockingcap.constants.LockingCapRegTestConstants;
import co.rsk.peg.whitelist.constants.WhitelistRegTestConstants;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;

public class BridgeRegTestConstants extends BridgeConstants {

    private static final List<BtcECKey> DEFAULT_FEDERATION_PUBLIC_KEYS = Stream.of(
        "0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124",
        "03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db",
        "02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1")
        .map(Hex::decode)
        .map(BtcECKey::fromPublicOnly)
        .toList();

    public BridgeRegTestConstants() {
        this(DEFAULT_FEDERATION_PUBLIC_KEYS);
    }

    public BridgeRegTestConstants(List<BtcECKey> federationPublicKeys) {
        btcParamsString = NetworkParameters.ID_REGTEST;
        feePerKbConstants = FeePerKbRegTestConstants.getInstance();
        whitelistConstants = WhitelistRegTestConstants.getInstance();
        federationConstants = FederationRegTestConstants.getInstance(federationPublicKeys);
        lockingCapConstants = LockingCapRegTestConstants.getInstance();

        btc2RskMinimumAcceptableConfirmations = 3;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 3;

        updateBridgeExecutionPeriod = 15_000; //15 seconds in millis

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.COIN;
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPeginTxValue = Coin.COIN.div(2);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds

        maxDepthBlockchainAccepted = 25;

        btcHeightWhenBlockIndexActivates = 10;
        maxDepthToSearchBlocksBelowIndexActivation = 5;

        minimumPegoutValuePercentageToReceiveAfterFee = 20;

        maxInputsPerPegoutTransaction = 10;

        numberOfBlocksBetweenPegouts = 50; // 25 Minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 250;
        pegoutTxIndexGracePeriodInBtcBlocks = 100;

        blockWithTooMuchChainWorkHeight = Integer.MAX_VALUE;
    }
}
