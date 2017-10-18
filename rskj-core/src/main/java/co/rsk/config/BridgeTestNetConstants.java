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
import co.rsk.peg.Federation;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.time.Instant;
import java.util.List;

public class BridgeTestNetConstants extends BridgeConstants {

    private static final Logger logger = LoggerFactory.getLogger("BridgeTestNetConstants");

    private static BridgeTestNetConstants instance = new BridgeTestNetConstants();

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0206262e6bb2dceea515f77fae4928d87002a04b72f721034e1d4fbf3d84b16c72"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03481d38fd2113f289347b7fd47e428127de02a078a7e28089ebe0150b74d9dcf7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("029868937807b41dac42ff5a5b4a1d1711c4f3454f5826933465aa2614c5e90fdf"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c83e2dc1fbeaa54f0a8e8d482d46a32ef721322a4910a756fb07713f2dddbcb9"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02629b1e976a5ed02194c0680f4f7b30f8388b51e935796ccee35e5b0fad915c3a"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("032ed58056d205829824c3693cc2f9285565b068a29661c37bc90f431b147f8e55"));
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

        // To recreate the value use
        // genesisFederationAddressCreatedAt = new GregorianCalendar(2017,4,10).getTimeInMillis() / 1000;
        // Currently set to:
        // Wed May 10 00:00:00 ART 2017
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1494385200l);

        // Expected federation address is:
        // 2NBPystfboREksK6hMCZesfH444zB3BkUUm
        genesisFederation = new Federation(
                7,
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;
        btcBroadcastingMinimumAcceptableBlocks = 30;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(500000);
    }

}
