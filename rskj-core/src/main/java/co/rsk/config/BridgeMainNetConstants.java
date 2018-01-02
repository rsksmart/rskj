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

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0206262e6bb2dceea515f77fae4928d87002a04b72f721034e1d4fbf3d84b16c72"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03481d38fd2113f289347b7fd47e428127de02a078a7e28089ebe0150b74d9dcf7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("029868937807b41dac42ff5a5b4a1d1711c4f3454f5826933465aa2614c5e90fdf"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c83e2dc1fbeaa54f0a8e8d482d46a32ef721322a4910a756fb07713f2dddbcb9"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02629b1e976a5ed02194c0680f4f7b30f8388b51e935796ccee35e5b0fad915c3a"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("023552f8144c944ffa220724cd4b5f455d75eaf59b17f73bdc1a7177a3e9bf7871"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("036d9d9bf6fa85bbb00a18b34e0d8baecf32c330c4b1920419c415e1005355f498"));
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03bb42b0d32e781b88319dbc3aadc43c7a032c1931b641f5ae8340b8891bfdedbd"));
        BtcECKey federator11PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03dece3c5f5b7df1ae3f4542c38dd25932e332d9e960c2c1f24712657626498705"));
        BtcECKey federator12PublicKey = BtcECKey.fromPublicOnly(Hex.decode("033965f98e9ec741fdd3281f5cf2a2a0ae89958f4bf4f6862ee73ac9bf2b49e0c7"));
        BtcECKey federator13PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0297d72f4c58b62495adbd49398b39d8fca69c6714ecaec49bd09e9dfcd9dc35cf"));
        BtcECKey federator14PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c5dc2281b1bf3a8db339dceb4867bbcca1a633f3a65d5f80f6e8aca35b9b191c"));

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
        // 2NCJZnqZHjvTNn9CUR8WyB3253cB7tPYKUq
        genesisFederation = new Federation(
                genesisFederationPublicKeys,
                genesisFederationAddressCreatedAt,
                1L,
                getBtcParams()
        );

        btc2RskMinimumAcceptableConfirmations = 100;
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

        genesisFeePerKb = Coin.MILLICOIN;
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }

}
