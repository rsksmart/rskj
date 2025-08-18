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
            "03d9d48cdc0fdf039d08371c64b1e86e1715e9898d4680595f1d4e3398dbdd9e9e",
            "0379d78dcae0be90715a088413c588da6a9381aae42e504f6e05c7b5204ed5bf3a",
            "035f29d6a4825b42f43de2e654531b7864059bfcfb33f5b6d868ba9b302cfb522b"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-08-15T12:00:00.000Z").toInstant();

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 600L;

        federationActivationAgeLegacy = 60L;
        preLovellActivationAge = 120L;
        federationActivationAge = 700L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 300L;
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
