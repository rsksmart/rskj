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
            "026df77fe41e8ac503ba47cb3a27e12661c5ee9d7f9f185d11c5680c0923356c3e",
            "02b0db2c66fbad3a46f2b0a617660a66ad72f5391aec659dd4b4de5e45d642e404",
            "031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-06-03T12:00:00.000Z").toInstant();

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
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
