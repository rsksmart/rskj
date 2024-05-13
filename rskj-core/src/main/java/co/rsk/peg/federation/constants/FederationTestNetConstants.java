package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FederationTestNetConstants extends FederationConstants {

    private static final FederationTestNetConstants instance = new FederationTestNetConstants();

    private FederationTestNetConstants() {
        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("034844a99cd7028aa319476674cc381df006628be71bc5593b8b5fdb32bb42ef85"));
        genesisFederationPublicKeys = Arrays.asList(federator0PublicKey, federator1PublicKey, federator2PublicKey, federator3PublicKey);
        genesisFederationCreationTime = ZonedDateTime.parse("1970-01-18T19:29:27.600Z").toInstant();

        // Passphrases are kept private
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        federationActivationAgeLegacy = 60L;
        federationActivationAge = 120L;

        fundsMigrationAgeSinceActivationBegin = 60L;
        fundsMigrationAgeSinceActivationEnd = 900L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 900L;

        erpFedPubKeysList = Arrays.stream(new String[] {
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        // Multisig address created in bitcoind with the following private keys:
        // 47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83
        // 9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35
        // e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788
        oldFederationAddress = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";
    }

    public static FederationTestNetConstants getInstance() { return instance; }
}
