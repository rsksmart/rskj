package co.rsk.config;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.AddressBasedAuthorizer;
import co.rsk.peg.Federation;
import com.google.common.collect.Lists;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BridgeMainNetConstants extends BridgeConstants {
    private static BridgeMainNetConstants instance = new BridgeMainNetConstants();

    BridgeMainNetConstants() {
        btcParamsString = NetworkParameters.ID_MAINNET;

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2aafaaa2611606699ec4f82777a268b708dab346de4880cd223969f7bbe5422bf"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("047319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344f6f082925b6b823106d21b2aa0572701bf1cd007c41d0c36822aac4cf04412ca"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0455a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a7049b97cdffcdca0f5cd199f4f4ec3a35f7b40a35a16d79b65579b153e7fddf48f"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7e44c9bf49f31a5b5d9f09aa079e0dd86fb439f0e9f11ded3d305783a25eb4306"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0494c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adcb17171aa9ec8d8587098e0771f686ee61ac35279f9e5aadf9b06b738aa6d3720"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0472cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c65325dce1ca5a6ceabed7010c2880ca36a07be3be03e858549972812e12dbce9b"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7e44c9bf49f31a5b5d9f09aa079e0dd86fb439f0e9f11ded3d305783a25eb4306"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("044c532bcd5454f0eb8dd5f6caa9b0bffe91a5918cc45225e41d3d509de9ee0cc755033c6070103fc41a8551d8d765b7126e582cac099283fb177e410c24df25ea"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("041aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d261f17f8ec02af309b7b50c06e2baa05a57166266e038a0a7dce7b70386e8260a3"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0445ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeeae024d50312de76a7950f8c6268fbf454335cf252f961a67c47e67dc06fa590ba"));
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe85cff7d7a1f67fefc865bfa34927b4457ec4481fa93c297441fae8c1dd0440cee"));
        BtcECKey federator11PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04481f02b7140acbf3fcdd9f72cf9a7d9484d8125e6df7c9451cfa55ba3b0772657bc2471f34ed4f12c42ab1b1587849feb847576011b8f79ab223e2bd3bfebb04"));
        BtcECKey federator12PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04f909ae15558c70cc751aff9b1f495199c325b13a9e5b934fd6299cd30ec50be804870f0dad7b9d1e3ed6a852ce42a30c0afcf803a48443e5a096591016a3b663"));
        BtcECKey federator13PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04c6018fcbd3e89f3cf9c7f48b3232ea3638eb8bf217e59ee290f5f0cfb2fb9259fd2c5fd43652022645cbfa62bf24c759102dca0746b25d69fffed1b2162dbfd4"));
        BtcECKey federator14PublicKey = BtcECKey.fromPublicOnly(Hex.decode("04b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed605847b01ee22e2ee5014f547283a872d5ffb728802ec83e3761da32c4a01e705"));

        List<BtcECKey> genesisFederationPublicKeys = Lists.newArrayList(
                federator0PublicKey, federator1PublicKey, federator2PublicKey,
                federator3PublicKey, federator4PublicKey, federator5PublicKey,
                federator6PublicKey, federator7PublicKey, federator8PublicKey,
                federator9PublicKey, federator10PublicKey, federator11PublicKey,
                federator12PublicKey, federator13PublicKey, federator14PublicKey
        );

        // Currently set to:
        // Wednesday, January 3, 2018 12:00:00 AM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1514948400l);

        // Expected federation address is:
        // 34zsQPsDRD3mzDWXy3ekBj2sDXPGdeLcL6
        genesisFederation = new Federation(
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 100;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 1000;
        rsk2BtcMinimumAcceptableConfirmations = 4000;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        minimumLockTxValue = Coin.valueOf(1000000);
        minimumReleaseTxValue = Coin.valueOf(800000);

        // Keys generated with GenNodeKey using generators 'auth-a' through 'auth-e'
        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
                "04e593d4cde25137b13f19462bc4c02e97ba2ed5a3860813497abf9b4eeb9259e37e0384d12cfd2d9a7a0ba606b31ee34317a9d7f4a8591c6bcf5dfd5563248b2f",
                "045e7f2563e73d44d149c19cffca36e1898597dc759d76166b8104103c0d3f351a8a48e3a224544e9a649ad8ebcfdbd6c39744ddb85925f19c7e3fd48f07fc1c06",
                "0441945e4e272936106f6200b36162f3510e8083535c15e175ac82deaf828da955b85fd72b7534f2a34cedfb45fa63b728cc696a2bd3c5d39ec799ec2618e9aa9f"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
                federationChangeAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        // Key generated with GenNodeKey using generator 'auth-lock-whitelist'
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
                "041a2449e9d63409c5a8ea3a21c4109b1a6634ee88fd57176d45ea46a59713d5e0b688313cf252128a3e49a0b2effb4b413e5a2525a6fa5894d059f815c9d9efa6"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
                lockWhitelistAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAge = 18500L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 10585L;

        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
                "0448f51638348b034995b1fd934fe14c92afde783e69f120a46ee16eb6bdc2e4f6b5e37772094c68c0dea2b1be3d96ea9651a9eebda7304914c8047f4e3e251378",
                "0484c66f75548baf93e322574adac4e4579b6a53f8d11fab640e14c90118e6983ef24b0de349a3e88f72e81e771ae1c897cef446fd7f4da71778c532aee3b6c41b",
                "04bb6435dc1ea12da843ebe213893d136c1624acd681fff82551498ae00bf28e9323164b00daf925fa75177463b8254a2aae8a1713e4d851a84ea369c193e9ce51"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
                feePerKbAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.MILLICOIN.multiply(5);
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }

}
