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

public class BridgeDevNetConstants extends BridgeConstants {
    // IMPORTANT: BTC, RSK and MST keys are the same.
    // Change upon implementation of the <INSERT FORK NAME HERE> fork.
    public static final List<BtcECKey> DEVNET_FEDERATION_PUBLIC_KEYS = Arrays.asList(
        BtcECKey.fromPublicOnly(
            Hex.decode("03d68975ab0f6ab782febc37aaa486ae19cc5e72c6900e34e21317285c88915ed6")
        ),
        BtcECKey.fromPublicOnly(
            Hex.decode("02914c05df0b11862ac6931c226ad40ebc4f5624ee6dca34278d3bbfa73b914cbd")
        ),
        BtcECKey.fromPublicOnly(
            Hex.decode("0309d9df35855aa45235a04e30d228889eb03e462874588e631359d5f9cdea6519")
        )
    );

    public BridgeDevNetConstants(List<BtcECKey> federationPublicKeys) {
        btcParamsString = NetworkParameters.ID_TESTNET;

        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(federationPublicKeys);

        // Currently set to:
        // Monday, November 13, 2017 9:00:00 PM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1510617600l);

        // Expected federation address is:
        // 2NCEo1RdmGDj6MqiipD6DUSerSxKv79FNWX
        genesisFederation = new Federation(
            federationMembers,
            genesisFederationAddressCreatedAt,
            1L,
            getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 1;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 30000; // 30secs

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValueInSatoshis = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValueInSatoshis = Coin.valueOf(500_000);
        minimumPeginTxValueInSatoshis = Coin.valueOf(500_000);
        minimumPegoutTxValueInSatoshis = Coin.valueOf(250_000);

        // Keys generated with GenNodeKey using generators 'auth-a' through 'auth-e'
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991",
            "04af886c67231476807e2a8eee9193878b9d94e30aa2ee469a9611d20e1e1c1b438e5044148f65e6e61bf03e9d72e597cb9cdea96d6fc044001b22099f9ec403e2",
            "045d4dedf9c69ab3ea139d0f0da0ad00160b7663d01ce7a6155cd44a3567d360112b0480ab6f31cac7345b5f64862205ea7ccf555fcf218f87fa0d801008fecb61",
            "04709f002ac4642b6a87ea0a9dc76eeaa93f71b3185985817ec1827eae34b46b5d869320efb5c5cbe2a5c13f96463fe0210710b53352a4314188daffe07bd54154",
//           "04aff62315e9c18004392a5d9e39496ff5794b2d9f43ab4e8ade64740d7fdfe896969be859b43f26ef5aa4b5a0d11808277b4abfa1a07cc39f2839b89cc2bc6b4c"
            "0447b4aba974c61c6c4045893267346730ec965b308e7ca04a899cf06a901face3106e1eef1bdad04928cd8263522eda4872d20d3fe1ef5e551785c4a482656a6e"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
            federationChangeAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
//           "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
            "0447b4aba974c61c6c4045893267346730ec965b308e7ca04a899cf06a901face3106e1eef1bdad04928cd8263522eda4872d20d3fe1ef5e551785c4a482656a6e"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAgeLegacy = 10L;
        federationActivationAge = 20L;

        fundsMigrationAgeSinceActivationBegin = 15L;
        fundsMigrationAgeSinceActivationEnd = 100L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 100L;

        // Key generated with GenNodeKey using generator 'auth-fee-per-kb'
        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "0430c7d0146029db553d60cf11e8d39df1c63979ee2e4cd1e4d4289a5d88cfcbf3a09b06b5cbc88b5bfeb4b87a94cefab81c8d44655e7e813fc3e18f51cfe7e8a0"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            feePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.MILLICOIN;

        maxFeePerKb = Coin.valueOf(5_000_000L);

        // Key generated with GenNodeKey using generator 'auth-increase_locking_cap'
        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
            increaseLockingCapAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialLockingCap = Coin.COIN.multiply(1_000L); // 1_000 BTC

        lockingCapIncrementsMultiplier = 2;

        btcHeightWhenBlockIndexActivates = 700_000; //TODO define this value when Iris activation height in RSK is determined
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        // Keys generated with GenNodeKey using generators 'erp-fed-01' through 'erp-fed-05'
        erpFedPubKeysList = Arrays.stream(new String[] {
            "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
            "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
            "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
            "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
            "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        // Multisig address created in bitcoind with the following private keys:
        // 47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83
        // 9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35
        // e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788
        oldFederationAddress = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds

        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 20;

        maxInputsPerPegoutTransaction = 10;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (considering 1 block every 30 seconds)
    }
}
