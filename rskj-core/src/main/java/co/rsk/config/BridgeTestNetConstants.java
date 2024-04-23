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
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import co.rsk.peg.feeperkb.constants.FeePerKbTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class BridgeTestNetConstants extends BridgeConstants {
    private static final BridgeTestNetConstants instance = new BridgeTestNetConstants();

    BridgeTestNetConstants() {
        btcParamsString = NetworkParameters.ID_TESTNET;
        feePerKbConstants = FeePerKbTestNetConstants.getInstance();

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9")
        );
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")
        );
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09")
        );
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("034844a99cd7028aa319476674cc381df006628be71bc5593b8b5fdb32bb42ef85")
        );

        List<BtcECKey> genesisFederationPublicKeys = Arrays.asList(
            federator0PublicKey,
            federator1PublicKey,
            federator2PublicKey,
            federator3PublicKey
        );

        // IMPORTANT: BTC, RSK and MST keys are the same.
        // Change upon implementation of the <INSERT FORK NAME HERE> fork.
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);

        // Currently set to:
        // Currently set to: Monday, October 8, 2018 12:00:00 AM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1538967600l);

        FederationArgs federationArgs = new FederationArgs(federationMembers, genesisFederationAddressCreatedAt, 1L, getBtcParams());
        genesisFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        btc2RskMinimumAcceptableConfirmations = 10;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        legacyMinimumPegoutTxValueInSatoshis = Coin.valueOf(500_000);
        minimumPegoutTxValueInSatoshis = Coin.valueOf(250_000);

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
            federationChangeAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Passphrases are kept private
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAgeLegacy = 60L;
        federationActivationAge = 120L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 900L;

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

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 2_589_553; // Estimated date Wed, 20 Mar 2024 15:00:00 GMT. 2,579,823 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 1_440; // 10 days in BTC blocks (considering 1 block every 10 minutes)
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

}
