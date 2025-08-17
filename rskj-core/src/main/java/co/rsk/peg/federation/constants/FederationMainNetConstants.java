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
            "026df77fe41e8ac503ba47cb3a27e12661c5ee9d7f9f185d11c5680c0923356c3e",
            "02b0db2c66fbad3a46f2b0a617660a66ad72f5391aec659dd4b4de5e45d642e404",
            "031c749a4e732bf871ec985496431b71d85c533690c12a4228143cc290c928078f"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
        genesisFederationCreationTime = ZonedDateTime.parse("2025-06-03T12:00:00.000Z").toInstant();

        // same as testnet
        List<ECKey> federationChangeAuthorizedKeys = Stream.of(
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        validationPeriodDurationInBlocks = 500L;

        federationActivationAgeLegacy = 18_500L;
        federationActivationAge = 600L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 150L;
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
