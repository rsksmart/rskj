package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FederationMainNetConstants extends FederationConstants {

    private static final FederationMainNetConstants INSTANCE = new FederationMainNetConstants();

    private FederationMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        genesisFederationPublicKeys = Stream.of(
            "03d9d48cdc0fdf039d08371c64b1e86e1715e9898d4680595f1d4e3398dbdd9e9e",
            "0379d78dcae0be90715a088413c588da6a9381aae42e504f6e05c7b5204ed5bf3a",
            "035f29d6a4825b42f43de2e654531b7864059bfcfb33f5b6d868ba9b302cfb522b"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-08-15T12:00:00.000Z").toInstant();

        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 500L;

        federationActivationAgeLegacy = 18_500L;
        federationActivationAge = 600L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 200L;
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
