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

public class BridgeTestNetConstants extends BridgeConstants {
    private static BridgeTestNetConstants instance = new BridgeTestNetConstants();

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0206262e6bb2dceea515f77fae4928d87002a04b72f721034e1d4fbf3d84b16c72"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03481d38fd2113f289347b7fd47e428127de02a078a7e28089ebe0150b74d9dcf7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("029868937807b41dac42ff5a5b4a1d1711c4f3454f5826933465aa2614c5e90fdf"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c83e2dc1fbeaa54f0a8e8d482d46a32ef721322a4910a756fb07713f2dddbcb9"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02629b1e976a5ed02194c0680f4f7b30f8388b51e935796ccee35e5b0fad915c3a"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("023552f8144c944ffa220724cd4b5f455d75eaf59b17f73bdc1a7177a3e9bf7871"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("036d9d9bf6fa85bbb00a18b34e0d8baecf32c330c4b1920419c415e1005355f498"));
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03bb42b0d32e781b88319dbc3aadc43c7a032c1931b641f5ae8340b8891bfdedbd"));
        BtcECKey federator11PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03dece3c5f5b7df1ae3f4542c38dd25932e332d9e960c2c1f24712657626498705"));
        BtcECKey federator12PublicKey = BtcECKey.fromPublicOnly(Hex.decode("033965f98e9ec741fdd3281f5cf2a2a0ae89958f4bf4f6862ee73ac9bf2b49e0c7"));
        BtcECKey federator13PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0297d72f4c58b62495adbd49398b39d8fca69c6714ecaec49bd09e9dfcd9dc35cf"));
        BtcECKey federator14PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c5dc2281b1bf3a8db339dceb4867bbcca1a633f3a65d5f80f6e8aca35b9b191c"));

        List<BtcECKey> genesisFederationPublicKeys = Lists.newArrayList(
                federator0PublicKey, federator1PublicKey, federator2PublicKey,
                federator3PublicKey, federator4PublicKey, federator5PublicKey,
                federator6PublicKey, federator7PublicKey, federator8PublicKey,
                federator9PublicKey, federator10PublicKey, federator11PublicKey,
                federator12PublicKey, federator13PublicKey, federator14PublicKey
        );

        // Currently set to:
        // Sun Dec 03 00:00:00 ART 2017
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1512270000l);

        // Expected federation address is:
        // 2NCJZnqZHjvTNn9CUR8WyB3253cB7tPYKUq
        genesisFederation = new Federation(
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;
        btcBroadcastingMinimumAcceptableBlocks = 30;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(500000);

        // Keys generated with GenNodeKey using generators 'auth-a' through 'auth-e'
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
                federationChangeAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 60L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

}
