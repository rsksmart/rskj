package co.rsk.config;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.AddressBasedAuthorizer;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
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

        List<BtcECKey> genesisFederationPublicKeys = Lists.newArrayList(
                federator0PublicKey, federator1PublicKey, federator2PublicKey,
                federator3PublicKey, federator4PublicKey, federator5PublicKey,
                federator6PublicKey, federator7PublicKey, federator8PublicKey,
                federator9PublicKey, federator10PublicKey, federator11PublicKey,
                federator12PublicKey, federator13PublicKey, federator14PublicKey
        );

        // IMPORTANT: Both BTC and RSK keys are the same.
        // Change upon implementation of the <INSERT FORK NAME HERE> fork.
        List<FederationMember> federationMembers = genesisFederationPublicKeys.stream().map(pk -> new FederationMember(
                pk, ECKey.fromPublicOnly(pk.getPubKey())
        )).collect(Collectors.toList());

        // Currently set to:
        // Wednesday, January 3, 2018 12:00:00 AM GMT-03:00
        Instant genesisFederationAddressCreatedAt = Instant.ofEpochMilli(1514948400l);
        
        genesisFederation = new Federation(
                federationMembers,
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
