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
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;
import java.util.List;


public class BridgeRegTestConstants extends BridgeConstants {

    private static final Logger logger = LoggerFactory.getLogger("BridgeRegTestConstants");

    private static BridgeRegTestConstants instance = new BridgeRegTestConstants();
    protected List<BtcECKey> federatorPrivateKeys;

    public static BridgeRegTestConstants getInstance() {
        return instance;
    }

    BridgeRegTestConstants() {
        btcParamsString = NetworkParameters.ID_REGTEST;

        BtcECKey federator0PrivateKey = BtcECKey.fromPrivate(Hex.decode("d4b5aa0e4926aff7ba7fcb8b904328323243c5bc9af4c1781de8d4db4cc014a7"));
        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(federator0PrivateKey.getPubKey());

        federatorPrivateKeys = Lists.newArrayList(federator0PrivateKey);
        federatorPublicKeys = Lists.newArrayList(federator0PublicKey);

        federatorsRequiredToSign = 1;

        Script redeemScript = ScriptBuilder.createRedeemScript(federatorsRequiredToSign, federatorPublicKeys);
        federationPubScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//      To recalculate federationAddress
        federationAddress = Address.fromP2SHScript(getBtcParams(), federationPubScript);

        // To recreate the value use
        federationAddressCreationTime = new GregorianCalendar(2016,0,1).getTimeInMillis();

        btc2RskMinimumAcceptableConfirmations = 3;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 3;
        btcBroadcastingMinimumAcceptableBlocks = 3;

        updateBridgeExecutionPeriod = 1 * 15 * 1000; //15 seconds in millis

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.COIN;
        minimumReleaseTxValue = Coin.valueOf(500000);
    }

    public List<BtcECKey> getFederatorPrivateKeys() {
        return federatorPrivateKeys;
    }

}
