package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.federation.FederationFormatVersion;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FederationMainNetConstants extends FederationConstants {

    private static final FederationMainNetConstants INSTANCE = new FederationMainNetConstants();

    private FederationMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        genesisFederationPublicKeys = Stream.of(
            "021ee3b08f7a27fba3d7dfbff33859d48ce81d5a27b6ca0089ee466419ad18076f",
            "03866a68b2abe4b9cf7a703db53373c24c5f389b03b32aad65e2f0a99ee3fc7e8c",
            "03cb7b88e79c497f5e28244efbc01afa5e9b094d0fb6799ef79106b8269bca43fd"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2026-06-01T12:00:00.000Z").toInstant();
        genesisFederationType = FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION;

        Set<RskAddress> federationChangeAuthorizedAddresses = Stream.of(
            "02f94dade3b1fbb7d066e6280852f33d29917a8edc4306b4a5d77c0eeb1566afa2",
            "02c6732476e084a1d8788c2aa5f3aeb7966ce295d70e751efdab5a17a6b4d597d5",
            "02c10071384c7766f6db4a71cb532e16177629401c27ae725c4fc7105a94a7907b"
        ).map(hex -> new RskAddress(ECKey.fromPublicOnly(Hex.decode(hex)).getAddress())).collect(Collectors.toSet());
        federationChangeAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(federationChangeAuthorizedAddresses);

        validationPeriodDurationInBlocks = 600L;

        federationActivationAgeLegacy = 18_500L;
        federationActivationAge = 700L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 300L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 172_800L; // 60 days, considering 1 block every 30 seconds

        erpFedPubKeysList = Stream.of(
            "0257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d4",
            "03c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f9",
            "03cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b3",
            "02370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        oldFederationAddress = "35JUi1FxabGdhygLhnNUEFG4AgvpNMgxK1";
    }

    public static FederationMainNetConstants getInstance() {
        return INSTANCE;
    }
}
