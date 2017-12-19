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
import org.ethereum.crypto.HashUtil;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BridgeRegTestConstants extends BridgeConstants {
    private static BridgeRegTestConstants instance = new BridgeRegTestConstants();

    protected List<BtcECKey> federatorPrivateKeys;

    BridgeRegTestConstants() {
        btcParamsString = NetworkParameters.ID_REGTEST;

        BtcECKey federator0PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator1".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator1PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator2".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator2PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator3".getBytes(StandardCharsets.UTF_8)));

        federatorPrivateKeys = Lists.newArrayList(federator0PrivateKey, federator1PrivateKey, federator2PrivateKey);
        List<BtcECKey> federatorPublicKeys = federatorPrivateKeys.stream().map(key -> BtcECKey.fromPublicOnly(key.getPubKey())).collect(Collectors.toList());

        Instant genesisFederationCreatedAt = ZonedDateTime.parse("2016-01-01T00:00:00Z").toInstant();

        genesisFederation = new Federation(
                federatorPublicKeys,
                genesisFederationCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 3;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 3;
        btcBroadcastingMinimumAcceptableBlocks = 3;

        updateBridgeExecutionPeriod = 1 * 15 * 1000; //15 seconds in millis

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.COIN;
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

        federationActivationAge = 10L;

        fundsMigrationAgeSinceActivationBegin = 15L;
        fundsMigrationAgeSinceActivationEnd = 150L;

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public List<BtcECKey> getFederatorPrivateKeys() {
        return federatorPrivateKeys;
    }

    public static BridgeRegTestConstants getInstance() {
        return instance;
    }
}
