package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.FederationFormatVersion;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FederationTestNet2Constants extends FederationConstants {

    private static final FederationTestNet2Constants INSTANCE = new FederationTestNet2Constants();

    private FederationTestNet2Constants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET4);

        genesisFederationPublicKeys = Stream.of(
            "03d9d48cdc0fdf039d08371c64b1e86e1715e9898d4680595f1d4e3398dbdd9e9e",
            "0379d78dcae0be90715a088413c588da6a9381aae42e504f6e05c7b5204ed5bf3a",
            "035f29d6a4825b42f43de2e654531b7864059bfcfb33f5b6d868ba9b302cfb522b"

        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2026-07-20T00:00:00Z").toInstant();
        genesisFederationType = FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION;

        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 2000L;
        federationActivationAge = 2400L;
        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;

        erpFedPubKeysList = Stream.of(
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)
    }

    public static FederationTestNet2Constants getInstance() {
        return INSTANCE;
    }
}
