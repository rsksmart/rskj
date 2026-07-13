package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.FederationFormatVersion;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;

/**
 * Federation constants for the RSK testnet2 network (pegs to Bitcoin testnet4).
 *
 * NOTE: the keys below are LOCAL-DEV keys generated for a single-federator local network.
 * They are intentionally the only members of the genesis federation so a single powpeg-node
 * (loading the matching private keys from keyfiles) can operate the peg locally. These MUST be
 * replaced with the real, securely-generated genesis-federation keys before any shared/public
 * testnet2 launch.
 */
public class FederationTestNet2Constants extends FederationConstants {

    private static final FederationTestNet2Constants INSTANCE = new FederationTestNet2Constants();
    private final long preLovellActivationAge;

    private FederationTestNet2Constants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET4);

        // Single local federator (LOCAL-DEV key). Private key held by the powpeg-node keyfile.
        genesisFederationPublicKeys = Stream.of(
            "0308d9504568666445593ed6c1fbe82bb79996744ebcf599ef096397c8764d73be"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2024-05-03T00:00:00Z").toInstant();
        // powpeg reconstructs the active federation as a P2SH-P2WSH-ERP federation, so the genesis
        // federation must be created in the same format for the addresses to match.
        genesisFederationType = FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION;

        // Single local change-authorizer (LOCAL-DEV key); MAJORITY of 1 == 1.
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "040228ed699bda42fccc3df354791db8d554739e31d830d9a9d3a342ea186847b687b60104ae0c2595d2dac27cfe1d081ec91b90aef366e141c31324429a5cacc6"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 2000L;

        federationActivationAgeLegacy = 60L;
        preLovellActivationAge = 120L;
        federationActivationAge = 2400L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 900L;

        // Emergency / ERP keys (LOCAL-DEV keys).
        erpFedPubKeysList = Stream.of(
            "03e1fd3831cc16b05ccb23ad77bc53029682ded52dc0d9a77fa6e6f9d645bd0ee1",
            "036c740c55efd60cefa46361fb96c6e9e71318ce5b14a9bd7f232731a067f4ccb8",
            "03e19c2ff979a006632aabc42cc4b20b8775966a2366793044ce5a5165f64ece2e"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (1 block every 10 minutes)

        // No prior federation on a fresh network; placeholder retained for the migration API.
        oldFederationAddress = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";
    }

    public static FederationTestNet2Constants getInstance() {
        return INSTANCE;
    }

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
