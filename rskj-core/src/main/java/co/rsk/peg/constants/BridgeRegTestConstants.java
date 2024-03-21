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

package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import co.rsk.peg.feeperkb.constants.FeePerKbRegTestConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

public class BridgeRegTestConstants extends BridgeConstants {
    // IMPORTANT: BTC, RSK and MST keys are the same.
    // Change upon implementation of the <INSERT FORK NAME HERE> fork.
    public static final List<BtcECKey> REGTEST_FEDERATION_PRIVATE_KEYS = Collections.unmodifiableList(Arrays.asList(
        BtcECKey.fromPrivate(HashUtil.keccak256("federator1".getBytes(StandardCharsets.UTF_8))),
        BtcECKey.fromPrivate(HashUtil.keccak256("federator2".getBytes(StandardCharsets.UTF_8))),
        BtcECKey.fromPrivate(HashUtil.keccak256("federator3".getBytes(StandardCharsets.UTF_8)))
    ));
    public static final List<BtcECKey> REGTEST_FEDERATION_PUBLIC_KEYS = Collections.unmodifiableList(REGTEST_FEDERATION_PRIVATE_KEYS.stream()
        .map(key -> BtcECKey.fromPublicOnly(key.getPubKey()))
        .collect(Collectors.toList()));

    private static final BridgeRegTestConstants instance = new BridgeRegTestConstants(REGTEST_FEDERATION_PUBLIC_KEYS);

    public BridgeRegTestConstants(List<BtcECKey> federationPublicKeys) {
        btcParamsString = NetworkParameters.ID_REGTEST;
        feePerKbConstants = new FeePerKbRegTestConstants();

        genesisFederationPublicKeys = federationPublicKeys;
        genesisFederationCreationTime = ZonedDateTime.parse("2016-01-01T00:00:00Z").toInstant();

        btc2RskMinimumAcceptableConfirmations = 3;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 5;
        rsk2BtcMinimumAcceptableConfirmations = 3;

        updateBridgeExecutionPeriod = 15_000; //15 seconds in millis

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.COIN;
        legacyMinimumPegoutTxValue = Coin.valueOf(500_000);
        minimumPeginTxValue = Coin.COIN.div(2);
        minimumPegoutTxValue = Coin.valueOf(250_000);

        // Keys generated with GenNodeKey using generators 'auth-a' through 'auth-e'
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991",
            "04af886c67231476807e2a8eee9193878b9d94e30aa2ee469a9611d20e1e1c1b438e5044148f65e6e61bf03e9d72e597cb9cdea96d6fc044001b22099f9ec403e2",
            "045d4dedf9c69ab3ea139d0f0da0ad00160b7663d01ce7a6155cd44a3567d360112b0480ab6f31cac7345b5f64862205ea7ccf555fcf218f87fa0d801008fecb61",
            "04709f002ac4642b6a87ea0a9dc76eeaa93f71b3185985817ec1827eae34b46b5d869320efb5c5cbe2a5c13f96463fe0210710b53352a4314188daffe07bd54154",
            "04aff62315e9c18004392a5d9e39496ff5794b2d9f43ab4e8ade64740d7fdfe896969be859b43f26ef5aa4b5a0d11808277b4abfa1a07cc39f2839b89cc2bc6b4c"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
            federationChangeAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        federationActivationAgeLegacy = 10L;
        federationActivationAge = 20L;

        fundsMigrationAgeSinceActivationBegin = 15L;
        fundsMigrationAgeSinceActivationEnd = 150L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 150L;

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialLockingCap = Coin.COIN.multiply(1_000L); // 1_000 BTC

        lockingCapIncrementsMultiplier = 2;

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds

        maxDepthBlockchainAccepted = 25;

        // Key generated with GenNodeKey using generator 'auth-increase_locking_cap'
        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
            increaseLockingCapAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        btcHeightWhenBlockIndexActivates = 10;
        maxDepthToSearchBlocksBelowIndexActivation = 5;

        erpFedActivationDelay = 500;

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

        minimumPegoutValuePercentageToReceiveAfterFee = 20;

        maxInputsPerPegoutTransaction = 10;

        numberOfBlocksBetweenPegouts = 50; // 25 Minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 250;
        pegoutTxIndexGracePeriodInBtcBlocks = 100;
    }

    public static BridgeRegTestConstants getInstance() {
        return instance;
    }
}
