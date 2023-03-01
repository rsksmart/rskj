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

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(
                Hex.decode("0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124")
        );
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(
                Hex.decode("03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db")
        );
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(
                Hex.decode("02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1")
        );

        List<BtcECKey> genesisFederationPublicKeys = Arrays.asList(federator0PublicKey, federator1PublicKey, federator2PublicKey);

        // IMPORTANT: BTC, RSK and MST keys are the same.
        // Change upon implementation of the <INSERT FORK NAME HERE> fork.
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);

        // Currently set to:
        // Currently set to: Monday, October 8, 2018 12:00:00 AM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1677700426096L);

        genesisFederation = new Federation(
            federationMembers,
            genesisFederationAddressCreatedAt,
            1L,
            getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 2;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 2;
        rsk2BtcMinimumAcceptableConfirmations = 6;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValueInSatoshis = Coin.valueOf(1_000_000);
        minimumPeginTxValueInSatoshis = Coin.valueOf(10_000);
        legacyMinimumPegoutTxValueInSatoshis = Coin.valueOf(500_000);
        minimumPegoutTxValueInSatoshis = Coin.valueOf(10_000);

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
                "045af2d6d0fdc81d5eeec2460f01b3dbc67f1998d25384e6be18e760046202508eb34c59de6fc7fb484f6df38a3cd7117789fea97710b465c2142e145a9edb31c0",
                "04d58edc18ea1bbddbaa0a44af4d8f20bcd2356ec3a85c1a8c54cc9719439cc69c639b366d12d2fd2de95c6fa909e0557982ee6382a770209d38c1e2806f7e6cc2",
                "0446b927069372ff2f3cafc48d9804b34675e4cf23240302468c6d92501ea5015b69c7fbaeb1a3ec299bf196b1b1ceef13c7f00f7fdb9196832ab974f4faa925dc"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
            federationChangeAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Passphrases are kept private
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
                "04765b6f50d100f54bffd5a4cf652b8396a31790601aab038799ca48171cd0f9ad0801bf119d320480bf94ea419a04c8b67890261e303d170a172aa6e69167868e"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 60L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 100L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 150L;

        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "04701d1d27f8c2ae97912d96fb1f82f10c2395fd320e7a869049268c6b53d2060dfb2e22e3248955332d88cd2ae29a398f8f3858e48dd6d8ffbc37dfd6d1aa4934",
            "045ef89e4a5645dc68895dbc33b4c966c3a0a52bb837ecdd2ba448604c4f47266456d1191420e1d32bbe8741f8315fde4d1440908d400e5998dbed6549d499559b",
            "0455db9b3867c14e84a6f58bd2165f13bfdba0703cb84ea85788373a6a109f3717e40483aa1f8ef947f435ccdf10e530dd8b3025aa2d4a7014f12180ee3a301d27"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            feePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.MILLICOIN;

        maxFeePerKb = Coin.valueOf(5_000_000L);

        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "04701d1d27f8c2ae97912d96fb1f82f10c2395fd320e7a869049268c6b53d2060dfb2e22e3248955332d88cd2ae29a398f8f3858e48dd6d8ffbc37dfd6d1aa4934",
            "045ef89e4a5645dc68895dbc33b4c966c3a0a52bb837ecdd2ba448604c4f47266456d1191420e1d32bbe8741f8315fde4d1440908d400e5998dbed6549d499559b",
            "0455db9b3867c14e84a6f58bd2165f13bfdba0703cb84ea85788373a6a109f3717e40483aa1f8ef947f435ccdf10e530dd8b3025aa2d4a7014f12180ee3a301d27"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
            increaseLockingCapAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        lockingCapIncrementsMultiplier = 2;
        initialLockingCap = Coin.COIN.multiply(200); // 200 BTC

        btcHeightWhenBlockIndexActivates = 2_039_594;
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        erpFedPubKeysList = Arrays.stream(new String[] {
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        // Multisig address created in bitcoind with the following private keys:
        // 47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83
        // 9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35
        // e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788
        oldFederationAddress = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (considering 1 block every 30 seconds)
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

}
