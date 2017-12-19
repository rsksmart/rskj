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

package co.rsk.config;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.AddressBasedAuthorizer;
import co.rsk.peg.Federation;
import com.google.common.collect.Lists;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BridgeDevNetConstants extends BridgeConstants {
    private static BridgeDevNetConstants instance = new BridgeDevNetConstants();

    BridgeDevNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0234ab441aa5edb1c7341315e21408c3947cce345156c465b3336e8c6a5552f35f"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03301f6c4422aa96d85f52a93612a0c6eeea3d04cfa32f97a7a764c67e062e992a"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02d33a1f8f5cfa2f7be71b0002710f4c8f3ea44fef40056be7b89ed3ca0eb3431c"));

        List<BtcECKey> genesisFederationPublicKeys = Lists.newArrayList(
                federator0PublicKey, federator1PublicKey, federator2PublicKey
        );

        // To recreate the value use
        // genesisFederationAddressCreatedAt = Instant.ofEpochMilli(new GregorianCalendar(2009,0,1).getTimeInMillis());
        // Currently set to:
        // Monday, November 13, 2017 9:00:00 PM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1510617600l);

        // Expected federation address is:
        // 2NCEo1RdmGDj6MqiipD6DUSerSxKv79FNWX
        genesisFederation = new Federation(
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;
        btcBroadcastingMinimumAcceptableBlocks = 30;

        updateBridgeExecutionPeriod = 30000; // 30secs

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(500000);

        // Keys generated with GenNodeKey using generators 'auth-a' through 'auth-e'
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
                "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991",
                "04af886c67231476807e2a8eee9193878b9d94e30aa2ee469a9611d20e1e1c1b438e5044148f65e6e61bf03e9d72e597cb9cdea96d6fc044001b22099f9ec403e2",
                "045d4dedf9c69ab3ea139d0f0da0ad00160b7663d01ce7a6155cd44a3567d360112b0480ab6f31cac7345b5f64862205ea7ccf555fcf218f87fa0d801008fecb61",
                "04709f002ac4642b6a87ea0a9dc76eeaa93f71b3185985817ec1827eae34b46b5d869320efb5c5cbe2a5c13f96463fe0210710b53352a4314188daffe07bd54154",
                "04aff62315e9c18004392a5d9e39496ff5794b2d9f43ab4e8ade64740d7fdfe896969be859b43f26ef5aa4b5a0d11808277b4abfa1a07cc39f2839b89cc2bc6b4c"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
                federationChangeAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 10L;

        fundsMigrationAgeSinceActivationBegin = 15L;
        fundsMigrationAgeSinceActivationEnd = 100L;
    }

    public static BridgeDevNetConstants getInstance() {
        return instance;
    }
}
