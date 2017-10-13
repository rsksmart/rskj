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
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;
import java.util.List;


public class BridgeRegTestConstants extends BridgeConstants {

    private static final Logger logger = LoggerFactory.getLogger("BridgeRegTestConstants");

    private static BridgeRegTestConstants instance = new BridgeRegTestConstants();
    public static BridgeRegTestConstants getInstance() {
        return instance;
    }

    protected List<BtcECKey> federatorPrivateKeys;

    BridgeRegTestConstants() {
        btcParamsString = NetworkParameters.ID_REGTEST;

        BtcECKey federator0PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator1".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator1PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator2".getBytes(StandardCharsets.UTF_8)));
        BtcECKey federator2PrivateKey = BtcECKey.fromPrivate(HashUtil.sha3("federator3".getBytes(StandardCharsets.UTF_8)));

        federatorPrivateKeys = Lists.newArrayList(federator0PrivateKey, federator1PrivateKey, federator2PrivateKey);

        long genesisFederationCreationTime = new GregorianCalendar(2016,0,1).getTimeInMillis();
        genesisFederation = Federation.fromPrivateKeys(
                2,
                federatorPrivateKeys,
                genesisFederationCreationTime,
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
    }

    public List<BtcECKey> getFederatorPrivateKeys() {
        return federatorPrivateKeys;
    }

}
