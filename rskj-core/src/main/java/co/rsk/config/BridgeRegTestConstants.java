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

        BtcECKey federator0PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator1".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator1PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator2".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator2PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator3".getBytes(StandardCharsets.UTF_8)));

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1"));

        federatorPrivateKeys = Lists.newArrayList(federator0PrivateKey, federator1PrivateKey, federator2PrivateKey);
        federatorPublicKeys = Lists.newArrayList(federator0PublicKey, federator1PublicKey, federator2PublicKey);

        federatorsRequiredToSign = 2;

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
