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

        // Federation address: 2NDw8trifn5QYfc9eg4rTXiv5dADMitucpm
        // Generated with seed fed-01
        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02fe0881821ea20b7a7ae69429e05a6baeda2460778ab6b24b51fc92ff69656fa5"));
        // Generated with seed fed-02
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03394f1ecdc50498b8e5d4beba3f3612e97c74e56e5f90426df74b2fc074ecbde8"));
        // Generated with seed fed-03
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02a2acc68ec45a1cde42508b26dda8b67843c346fcc2b33866d806fb82c5642b2b"));

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
        btc2RskMinimumAcceptableConfirmationsOnRsk = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf( 1_000_000);
        minimumReleaseTxValue = Coin.valueOf(500_000);

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            // private key from seed fed-changer-01
            "04ed498022f9d618a96f272b1990a640d9f24fb97d2648f8716f9ee22dc008eba793f340602211b48a5393467f0b496bdee2bee0141e9b7150767664f7c7aec1fa",
            // private key from seed fed-changer-02
            "046f66639295ca8e4294c24d63e3fbc11247f6ba6a27b6b4de9a3492f414152d9b99717cacac94d3af114bfde4edbcce4ced20053c0937d94f5aeca349c7f7b211",
            // private key from seed fed-changer-03
            "04132685d71b0109fecef0160f1efcab0187eff916f4d472289741bff2666d0e1c80d2f8271e7f9ce9d9a7bf327084caf3fc5a1bb1db0e1bf90d77f60b79186b1a"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
                federationChangeAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Passphrases are kept private
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            // PK from seed whitelist-disabler-01
            "041a7f5c5e7cdf31d122ba94a305d6a5483cfe63e9400ab3567eca4f2ca1459fb6513a19614e24b59767cfaf138077a488ce096039d6ad9e8d4f77673bafe0d95d"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 60L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;

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
            // pk from seed locking-cap-changer-01
            "04701d1d27f8c2ae97912d96fb1f82f10c2395fd320e7a869049268c6b53d2060dfb2e22e3248955332d88cd2ae29a398f8f3858e48dd6d8ffbc37dfd6d1aa4934"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
                increaseLockingCapAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        lockingCapIncrementsMultiplier = 2;
        initialLockingCap = Coin.COIN.multiply(200); // 200 BTC

        btcHeightWhenBlockIndexActivates = 1_940_000; //TODO define this value when Iris activation height in RSK is determined
        maxDepthToSearchBlocksBelowIndexActivation = 1_000; //TODO define this value with Sergio
    }

    public static BridgeTestNetConstants getInstance() {
        return instance;
    }

}
