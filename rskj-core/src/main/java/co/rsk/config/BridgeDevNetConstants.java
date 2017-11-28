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


public class BridgeDevNetConstants extends BridgeConstants {

    private static final Logger logger = LoggerFactory.getLogger("BridgeDevNetConstants");

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
                2,
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;
        btcBroadcastingMinimumAcceptableBlocks = 30;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // in millis

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(500000);
    }

    public static BridgeDevNetConstants getInstance() {
        return instance;
    }
}
