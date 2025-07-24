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
            "02339027256892db5d03bd3835fde93551941b3c5b9ad765b8e8d3451e3b7a2b3e",
            "033267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e",
            "031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-06-03T12:00:00.000Z").toInstant();

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "04339027256892db5d03bd3835fde93551941b3c5b9ad765b8e8d3451e3b7a2b3ed7c795665d7c20da2416f4be67e23b19a7654c29ce983acf5936c1705d105276",
            "043267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e75b8577faac2183782cb0d76820cf9f356831d216e99d886f8a6bc47fe696939",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
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
