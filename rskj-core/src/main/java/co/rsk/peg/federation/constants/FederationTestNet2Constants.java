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
            "02337f4a3d81fb621ec72e0f62336e3fb9b181063ad86674ea7f80d3ae687acf73",
            "02f9c08784952982e5cc24624f34d5afd1584ed32851b0790f2bba965e7a131b0f",
            "030e6370c451967cef1ae35e3a04d2cdfc337d94cc15ce1afa16130ca677294a9b"

        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2026-07-20T00:00:00Z").toInstant();
        genesisFederationType = FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION;

        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
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
