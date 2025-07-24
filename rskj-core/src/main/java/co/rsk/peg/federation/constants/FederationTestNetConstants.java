package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;

public class FederationTestNetConstants extends FederationConstants {

    private static final FederationTestNetConstants INSTANCE = new FederationTestNetConstants();
    private final long preLovellActivationAge;

    private FederationTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        genesisFederationPublicKeys = Stream.of(
            "03f7f755a259ce31f44a44f32fad9b744a7d529243a78a3f832f80c95a3f6813c2",
            "03930cc5564c1a1382cc3cbb6e39db6d65d41e0948d9f7fa77125e09d03be431a4",
            "0202a813e06617ed8f71aacd49f4e320c4113e07608cc20c02cdd78f9f3955e124"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-06-03T12:00:00.000Z").toInstant();

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "04f7f755a259ce31f44a44f32fad9b744a7d529243a78a3f832f80c95a3f6813c27daf400b879ecf7a147324d96be401dc9d179ef49c39f62488cca3bd2290f113",
            "04930cc5564c1a1382cc3cbb6e39db6d65d41e0948d9f7fa77125e09d03be431a48aa8260e693b8c188445bfea0fe2bb6e703b65bb6a4f3524c0050817bf2979bf",
            "0402a813e06617ed8f71aacd49f4e320c4113e07608cc20c02cdd78f9f3955e1244f90624d0be1f4ebab0598ec9bcab8f68ec20e29822b20001407e3411f1215a6"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 300L;

        federationActivationAgeLegacy = 60L;
        preLovellActivationAge = 120L;
        federationActivationAge = 360L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 150L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 900L;

        erpFedPubKeysList = Stream.of(
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        // Multisig address created in bitcoind with the following private keys:
        // 47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83
        // 9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35
        // e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788
        oldFederationAddress = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";
    }

    public static FederationTestNetConstants getInstance() {
        return INSTANCE;
    }

    // After lovell, we have to consider the activation age
    // to be more blocks than the validation period duration
    @Override
    public long getFederationActivationAge(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP383)) {
            return federationActivationAgeLegacy;
        }

        if (!activations.isActive(ConsensusRule.RSKIP419)) {
            return preLovellActivationAge;
        }

        return federationActivationAge;
    }
}
