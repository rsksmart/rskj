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
import co.rsk.peg.FederationMember;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BridgeTestNetConstants extends BridgeConstants {
    private static BridgeTestNetConstants instance = new BridgeTestNetConstants();

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;

        // These are temporary keys to be used in tests only. generated through gennodekeyid using fed-alpha-01/2/3
        BtcECKey federator0PublicKey = BtcECKey.fromPrivate(Hex.decode("d67e190199a59488864cb424b72f39ee6d4b6d64d7fee66d0e902dc34850d67c"));
        BtcECKey federator1PublicKey = BtcECKey.fromPrivate(Hex.decode("7f98bd4eec1da0f44f04a20899eb57cd775526f0a2930ae9bdc1ad145533dc49"));
        BtcECKey federator2PublicKey = BtcECKey.fromPrivate(Hex.decode("75f6b9b0e5605ee00c6b1ff240ef87288a92be1c17ce005ed9c924838b01d185"));

        List<BtcECKey> genesisFederationPublicKeys = Arrays.asList(federator0PublicKey, federator1PublicKey, federator2PublicKey);

        // IMPORTANT: BTC, RSK and MST keys are the same.
        // Change upon implementation of the <INSERT FORK NAME HERE> fork.
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);

        // Currently set to:
        // Currently set to: Monday, October 8, 2018 12:00:00 AM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1538967600l);

        genesisFederation = new Federation(
                federationMembers,
                genesisFederationAddressCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 5;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 0;
        rsk2BtcMinimumAcceptableConfirmations = 5;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(500000);

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "e77effb6858f373c5e9a2b7eb68b5d9e0ae2f28a430142452a197f877daf15ac",
            "233360738d2227fe43cb1fac655fc228d246aeabdf06295dc34bac01f730baeb",
            "1f486630a370ced74e77e5c3be5b486f70b138750fdd4384c4f6c2f812b8679d"
        }).map(hex -> ECKey.fromPrivate(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
                federationChangeAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Passphrases are kept private
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "e77effb6858f373c5e9a2b7eb68b5d9e0ae2f28a430142452a197f877daf15ac"
        }).map(hex -> ECKey.fromPrivate(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 60L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;

        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "e77effb6858f373c5e9a2b7eb68b5d9e0ae2f28a430142452a197f877daf15ac",
            "233360738d2227fe43cb1fac655fc228d246aeabdf06295dc34bac01f730baeb",
            "1f486630a370ced74e77e5c3be5b486f70b138750fdd4384c4f6c2f812b8679d"
        }).map(hex -> ECKey.fromPrivate(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
                feePerKbAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.MILLICOIN;

        maxFeePerKb = Coin.valueOf(5_000_000L);

        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "e77effb6858f373c5e9a2b7eb68b5d9e0ae2f28a430142452a197f877daf15ac",
            "233360738d2227fe43cb1fac655fc228d246aeabdf06295dc34bac01f730baeb",
            "1f486630a370ced74e77e5c3be5b486f70b138750fdd4384c4f6c2f812b8679d"
        }).map(hex -> ECKey.fromPrivate(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
                increaseLockingCapAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        lockingCapIncrementsMultiplier = 2;
        initialLockingCap = Coin.COIN.multiply(200); // 200 BTC
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

}
