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

import co.rsk.bitcoinj.core.*;
import com.google.common.collect.Lists;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;


public class BridgeDevNetConstants extends BridgeConstants {

    private static final Logger logger = LoggerFactory.getLogger("BridgeDevNetConstants");

    private static BridgeDevNetConstants instance = new BridgeDevNetConstants();

    BridgeDevNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0234ab441aa5edb1c7341315e21408c3947cce345156c465b3336e8c6a5552f35f"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03301f6c4422aa96d85f52a93612a0c6eeea3d04cfa32f97a7a764c67e062e992a"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02d33a1f8f5cfa2f7be71b0002710f4c8f3ea44fef40056be7b89ed3ca0eb3431c"));

        federatorPublicKeys = Lists.newArrayList(federator0PublicKey, federator1PublicKey, federator2PublicKey);

        federatorsRequiredToSign = 2;

        Script redeemScript = ScriptBuilder.createRedeemScript(federatorsRequiredToSign, federatorPublicKeys);
        federationPubScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//      To recalculate federationAddress
//      federationAddress = Address.fromP2SHScript(btcParams, federationPubScript);
        try {
            federationAddress = Address.fromBase58(getBtcParams(), "2NCEo1RdmGDj6MqiipD6DUSerSxKv79FNWX");
        } catch (AddressFormatException e) {
            logger.error("Federation address format is invalid");
            throw new RskConfigurationException(e.getMessage(), e);
        }

        // To recreate the value use
        // federationAddressCreationTime = new GregorianCalendar(2009,0,1).getTimeInMillis() / 1000;
        // Currently set to:
        // Tue Aug 23 21:53:20 ART 2016
        federationAddressCreationTime = 1472000000l;

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
