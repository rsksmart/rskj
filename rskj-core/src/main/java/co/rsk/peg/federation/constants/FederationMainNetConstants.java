package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FederationMainNetConstants extends FederationConstants {

    private static final FederationMainNetConstants instance = new FederationMainNetConstants();

    private FederationMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"));
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8"));
        BtcECKey federator11PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02481f02b7140acbf3fcdd9f72cf9a7d9484d8125e6df7c9451cfa55ba3b077265"));
        BtcECKey federator12PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03f909ae15558c70cc751aff9b1f495199c325b13a9e5b934fd6299cd30ec50be8"));
        BtcECKey federator13PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02c6018fcbd3e89f3cf9c7f48b3232ea3638eb8bf217e59ee290f5f0cfb2fb9259"));
        BtcECKey federator14PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed6"));
        genesisFederationPublicKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey, federator7PublicKey, federator8PublicKey,
            federator9PublicKey, federator10PublicKey, federator11PublicKey,
            federator12PublicKey, federator13PublicKey, federator14PublicKey
        );
        genesisFederationCreationTime = ZonedDateTime.parse("1970-01-18T12:49:08.400Z").toInstant();

        List<ECKey> federationChangeAuthorizedKeys =  Arrays.stream(new String[]{
            "04e593d4cde25137b13f19462bc4c02e97ba2ed5a3860813497abf9b4eeb9259e37e0384d12cfd2d9a7a0ba606b31ee34317a9d7f4a8591c6bcf5dfd5563248b2f",
            "045e7f2563e73d44d149c19cffca36e1898597dc759d76166b8104103c0d3f351a8a48e3a224544e9a649ad8ebcfdbd6c39744ddb85925f19c7e3fd48f07fc1c06",
            "0441945e4e272936106f6200b36162f3510e8083535c15e175ac82deaf828da955b85fd72b7534f2a34cedfb45fa63b728cc696a2bd3c5d39ec799ec2618e9aa9f"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        federationChangeAuthorizer = new AddressBasedAuthorizer(federationChangeAuthorizedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        federationActivationAgeLegacy = 18500L;
        federationActivationAge = 40320L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 10585L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 172_800L; // 60 days, considering 1 block every 30 seconds

        String erpFederator0StringPubKey = "0257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d4";
        String erpFederator1StringPubKey = "03c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f9";
        String erpFederator2StringPubKey = "03cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b3";
        String erpFederator3StringPubKey = "02370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80";
        erpFedPubKeysList = Arrays.stream(new String[]
            {erpFederator0StringPubKey, erpFederator1StringPubKey, erpFederator2StringPubKey, erpFederator3StringPubKey})
            .map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        oldFederationAddress = "35JUi1FxabGdhygLhnNUEFG4AgvpNMgxK1";
    }

    public static FederationMainNetConstants getInstance() {
        return instance;
    }
}
