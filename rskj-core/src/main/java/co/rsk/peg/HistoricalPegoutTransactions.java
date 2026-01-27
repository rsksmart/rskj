package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a lookup table for historical pegout transactions.
 *
 * <p>This class maintains immutable mappings between RSK transaction hashes
 * and their corresponding Bitcoin transaction hashes for both Testnet
 * and Mainnet.</p>
 *
 * <p>This is a utility class and is not meant to be instantiated.</p>
 */
public class HistoricalPegoutTransactions {

    private static final Map<String, String> TESTNET_HISTORICAL_PEGOUT_TRANSACTIONS;
    private static final Map<String, String> MAINNET_HISTORICAL_PEGOUT_TRANSACTIONS;

    static {
        Map<String, String> testnet = new HashMap<>();
        testnet.put("2d1b35c663d6c0c02380aba68e656cdc61cb7d412c31b19d330b637b4957a64c","a9bdc4e4a48a3e3754b2722b3e61eeca9ae4009379a62a8313acf485c79171c1");
        testnet.put("0be2c6ef85204b2735f18f352ab54863c44de092461e7b6490d58e0f920ea158","648858fe92cd7c9078b9870852d8abceecd472a8d1102fd767ec658fe414af9c");
        testnet.put("9b0135443f1a90464fa8224dbf70a9f840b583fea1a818ddf9d486eb783307fb","bb52a27915b91e628066d51713f1f39ac3f0770919e84a4fd5285841496d77ca");
        testnet.put("aba36f2d4d34fa29ff7f2edb5d782127606c9aabe0a1d0e8d784c6bd42f3ec06","7f199079f5c55b5ea4d4bf150c105bbac1af068f96e2897e88bb798289578c75");
        testnet.put("b2976235fd7dbdc23450dd9f1ee8e5becabc7977f7bbd17c3d0e3b0d8f754414","fc48fd9099b0ed41511e5d6da7a536880ef7dd1deb1ed1289e3c651e934aaa49");
        testnet.put("e95e556415ed6f3663b4eb8c8c4b6d23a7357e0f37f311c3630c96056cac0d3d","3d4f1c085e42b273ddd327fc30a6633b1277d952d3b5e76156acbdeffdaf7809");
        testnet.put("376ef807409d3a7d6dc878d8aa3be4c07431830a122b80b14044a8f659a37e6a","816a0708cfe301b1076d795d74b36955f4ef1fc477487772d06121a5dedfeca0");
        testnet.put("fc2ebf22d37544f66e7785ba77aa289967a55e9c2c9410bfd6a3328dc2967563","fa2350f918db4a59b6f4a74b7f10420dc309226d87c8c0b0ce56d71fd94abad8");
        testnet.put("e6786f5a39d2b6ca53df1125fc4bf39f3d95387e8f2d9e7fa4f43f814fbfe298","2924e68abd7371f9e34cb817213528478cc8ec22f48389f756623d5a4c35e0a4");
        testnet.put("bfd2a278af72e3ffe48c31d7afd83fde9b71ac86c69b006123302829497b72f7","c6f1fe4aba2e98cc9e190ae7aa6664901d417172847232a03a8112a3342ef53e");
        testnet.put("31909e372ee0bfb30c5d9118d5b3ebe86d9c769d98afaad424bdd2458cdca14b","4dbe93aaaab473d53039e88ab6f4b81704c3c9ae34a60fa94e32fc350763a9d3");
        testnet.put("277e1130908e883ee2c6498785fd142eea9745ef9794423da4472fbf75f37851","a476e91aeca06b6c52d276fb6734c2a4849dfe9f9feca2a055ad5f3add2ec328");
        testnet.put("25eb1af4cd91e5cb68cab69da88bc3c8d656543e07034e8f0fc636211eb5b457","0359d4b1621b4faa203f94394dd0c9f5094fcf6cad4acf6af604da9ce1ec3217");
        testnet.put("348fa88caed35640032036e55a0aa7231d13ca1ef993d3e076cc5a6b65ae3c9d","6ceb7c9be0f828f7d6e6d847671aba87760b72ae4e63a14afb1e11170d306d4f");
        testnet.put("eb798f9b863cde6b8d4c68cb6eb341ff94cd5258470698c8044af5b48f4f74d2","9d5b2dc437edc59f216257e202e9ab1bc6d10a791624cb1e976879049a74178d");
        testnet.put("9caf9320b1d34f4737eb4bf3b2c4b0752733379c591f7399afa6ee365df80bb7","0c5b2a437187481623e755ec0f4c7095fcdf66e6eff5b68852b0c8ad9f1063da");
        testnet.put("f327a6426ab06b93bfa015964f9ca2638f93ce588879e85182c02b7dd4c218bf","30f776e7e2842db08ffe1424cba3caf8857654fa2269c0cfd22a9bae177ee39c");
        testnet.put("6a9aa9d35df2ca26f09be7ad2a50db4a8394b49d9b8b9f8039860dcb619cbe54","caf72f6d65f287981afdba49d617a1bad8490e2b0a445629e797a7864d95b15f");
        testnet.put("5153d8121967affd37ec7302ecf5c9d9ca204812bcc674686c69445d77572749","c6184c576891dea44f2aa6269315a84c946486d3aa3845e8e29c65b63da778f7");
        testnet.put("4009afe3bf9a4690671f9b3b0fca13d2bef49a79a38fc97eb0b1c093d60a2601","0cbb38f1abc521691983be493e85b7115f317910e22ebeb55765c63615c07524");
        testnet.put("70d57ea83b7a8f3d4a256d06440b1aa870e3a501c37084b1a48f55f48b4afbeb","259bbdd9eb7c0deba308d084365cdb6e02a34f7827b2ee0ca7e341dfa90daa8e");
        testnet.put("165266d8527de457aacc5a4ddffc21b073e1c93d5c8e956139cfbcbf8df0d02a","0762be9af2d43df6b5bd6ef2ead5876137235ee7efeed0f17f1401bef5f18ef8");
        testnet.put("b669c8f1415b5742f4ef76a46df64704d4e28e4112890541dd0d8da8510a2af0","6dd692b7c2aa1b8a5e14b2f23be590a5b0f45a74c5a7933f824abf9d40cc139f");
        testnet.put("e2a53c7b68fcea5778bcf375b76799e5e76af02a7dfa5c3392f2b7627d74d5ef","b0b0caa4338dca07086da1ef503aaa5c81250e3b7390cff304d6bc83169d8bc7");
        testnet.put("bd9e3f7a80d124c5f5fde9cb84049cbd049a9341d2d88c0f43de2c88811c2a73","19a8eac8d1735bd80cdd087b132b1c7b73ebf5c0fe1691892cad1a7dad3e5086");
        testnet.put("549d43c466892fe1fec5b3fb393c4383a96570ec478d4043771a3e4f5b778ad9","6555870f4ab9eb25a2a3eaec6aa2d9c0e347fbaf45eba1737e77a234945214a7");
        testnet.put("8a6b7c670404df25f1bc3b8424a709883dc8a51bb11dc4ee40b170e3587cd63c","9a275de558b3de838eaf9674dfe7889da9283cac7ac661dc77db07337f3eaa8f");
        testnet.put("3977b41a15a0be0a044561763646ed7a557d188c549d76b13663a72a520d3acf","9dd5ad7357ee9d9a2c28b13b6c44aa69838a84f4b274a5e34c629300d8267702");
        testnet.put("8155900c5ba21da17aee0a7ad95475b8152c4511bd79049d99ae8480538b22c2","2dbbc05c756d339755f8e900c78163749592ce952f91f3c6d424053282749efa");
        testnet.put("0f34f67cb0497c45cb4f9d336810a2475fa0f1a6a7b2eb907e7c05efbedb237b","95e665b9dd56c5e33f22c209ac57ef46903bd1b7a3a3a04a62150a2277d75a69");
        testnet.put("d1a4c3386bd7b487eae84d22caf0286ac142511dbfe0e1dbd798f2c262e6828f","c074389cf3f0979272292cde8988b9ef73b2fb176516a9042f8935707e8a5c8c");
        testnet.put("c2571da571bf6dc72c3bc0d34c87728a1655685b5aad518e840376e92af373ad","8262e3e1ff484a30eb2833c7a504002f3de07aa3fcd5a76c4243b7027e728bee");
        testnet.put("dc670d1d420702da00c9087646f7e05ddbcaba761156a6babd41ea520c550842","3fc1d66b07e38ae41b534040e55f9159985cd5cc9f0eab8497c5d53d3b8721b8");
        testnet.put("51fff84dcba996f079049a4bd3a3c5f773b5069fdad7a2b30c81c30a5ccb2942","300fdbc7c63a9b6e5738d25788e5b1299c2dfc7eb3331b11cd22766696674591");
        testnet.put("19725fc6b52dc7f3a901a33e48ef09139cdaec20d6514e6210ffd730409a2bb7","420c5bdc5ae84a49417c120b978231a50b1a67142179fda46fa6635f44f9dfaf");
        testnet.put("e1ec211cab52c238a11d9af7366dd8ee3c8f9b974bddb636bf667a3e2e6ec111","9c0016a1abecd5c7d62ce5db5f80655f9e69d285ff3ebc0db00e7fb860549c4f");
        testnet.put("5cbeec1b643bc3ec31437c1f224f0674e84a4d24edf985f8c0307f6f4c32e7de","7c49a9651afe24236a56afe3b6ad3d1f822ea956cf1927be5d0ee183a152b39e");
        testnet.put("ef910844c0fb840ef5f89cb0210f89239de0dd9e6df5f42c2f737e8f98369e25","a342a0668fc637e28a610a2d36b235af899b7ec1b24e939d8a37e076fe57bc8c");
        testnet.put("10b572a23ba0cf9d9f7c6015fd067faa293be63fb5dfce153a3ba927b235b120","181a0390efa4866580e586159201756b8dc1089661d5a14a3b1f8aa4a4fafb41");
        testnet.put("77729445e640dbf0ce5286d76ec171bdf03e3e1663f7fc7d5fa699e9cd22f974","c00f8917a3213500388121060821e371b973f27504947018905abd04bb07a7f1");
        testnet.put("14c19f0ff2c8935db2ee8dc73133e43b3b5f72e37a5276ad1f98a641cf89f1a4","0a582d15ccc473e2a6383828bea3825ba5fa2d4124cf66cec66ce7a0535f8bfd");
        testnet.put("7552b20d2c6bc1120d48aff229774f3056448da49617e8e03e30fe0bbcb6a5cb","b66e4df75960f16f67c546bc3b44b1ce1f4fa55c15637fb0c662c036dd4d41e8");
        testnet.put("3816aa373a6938c5f13fcbd186f689ea40d7da7e0f7dfe61ffadc7f84249332b","302c8ba636331d92b0576599aa3fb06459040e1071cfa7975c132b50cfba1c40");
        testnet.put("652659074f8775c1ab7b16acd1d93c45478a001359f2d543955019c5e221eada","d52f8fe56df1254fa01efb4653f4b4d6d088f50255c83b50bd3f66c33455c8ad");
        testnet.put("0224eaa76ac7d078c074333f5dd19245ce9ded173536a9325fc5b55351a92570","496a1d5334751c0cca06ca40becc276672c37845deca8ae613250250f8f75bf5");
        testnet.put("4c97bbe80f7d4cf7e54b55d94599abd2087128fee97dd29f85aa29483d7d80fa","a4a3f843e789b1be9a2aa5155d2ae7cb54709f02c78115c8392ad4efbbcffce1");
        testnet.put("11dc63f2e638a0a49ccece61cf537875f6559c24b8732b60fffd67c380442620","1a7f93e39d3e7a87f26d5cfd8047d909945250a2c87c80f710085d4d97e940fc");
        testnet.put("bed1a456747681da552cc278391d277cf9863c8560effaaf2fe4fe055762fb6a","5369d7bc18f8b1ec6cdaaff6c984a554991eb5ae2d177d916b7759011f468940");
        testnet.put("d1fc670b4b0caca350e634faee1cc4b790a1fa122c78e25326180986100e8013","75db78f6c0ba87bad1d7bd41a1b4bc412a4ae069916e6a47eb713afa92f3267f");
        testnet.put("7c0b61d8ed9e5aa990331a636c92a5074456acdd5b82e556ff3341f6d78e4049","342b34571db7d9bc2783ec490b6a2a6abddf34c0ea0cf27e14b65e238f4eda34");
        testnet.put("8a9eb30b95b0680f0e2a686d536782af59b34c7c02bfef995d83d68b447494e7","35e2108cfbb009bac4c2b52efc31ff92cd36c8c803cee459afccfc9d3a81a2cf");
        testnet.put("9d69023cf4b12a790bb9cb24767061937e1f7b07d4b8370511979fd8eee6f4f0","9a163ae0df24833af87992fa0e3a09e4c0bed27a660e6fd94814b0da48c629e6");
        testnet.put("d3e94aac06e45556359d3d42a7d8eeca3e6fa89972bc921245f9d1892b1da9eb","5bd422c96cabc0c4adecc4d7a2a23dd7c18ace92fb86348910e596449570a45f");
        TESTNET_HISTORICAL_PEGOUT_TRANSACTIONS = Map.copyOf(testnet); // GENESIS-to-7.242.364

        Map<String, String> mainnet = new HashMap<>();
        mainnet.put("8472c6d227fe867f04859ad819a0585b7c2dc953896c37d9fd04d4019941ac18","49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802");
        mainnet.put("7e25dedc1a2760d1fa94786d3cd61c3419c37eb795351bdac006232e6c26903c","2c6f69d2f7746b01dba7c9cdf248973a239fcc78f7dd5971f99992e941a0715e");
        mainnet.put("a44ebfa65662ed2d2d480a1535be81f77c75aaa91119292dc1217d30625bb1d3","20806dadc7c9cbe777c2599983d38831461f7b701afb1ac93a3ec386c376edb9");
        mainnet.put("504dfc8314c67b81554e15cfebf093ba2b0ce443d5c66e0b3e25061fd61f91b8","5965a75e7e56ed4a308cc1bf8d94415c03c6a56f7302ae488e1d3fa05cd70e61");
        mainnet.put("d5292674f3a5c2d12271ce0a64eb1d82d0e18d022bae0fe771146597634e713a","8508d6a45b5a3e4ab8396c3f5ad895107e6a0ded9311729804b806661763779d");
        mainnet.put("f921e810974397e37dbc53199b28feb2e8fe2d35917595f7435f73660a33d059","00a726e67845f3d16a263ffde47315457a3388b7b9ce10f73c05560a69d09a40");
        mainnet.put("c3a8d93edac614f0a65243e300c75752f67835d4a5d9d08fd43cfe2c80d5aef9","5f1119f0d62babb36c513a41d4ccb4b0545a2a2dbd2b07ffd98d259e1c194e1f");
        mainnet.put("8f9a0fd8b3f2478d16e62d7b95ba61bfc8e6089a58318a62aae4efc20b8b7a3a","3aa787c1409f942991086de6c26fe7330ca2334e0261f1df57e1f1d15a5298d1");
        mainnet.put("91bdfd83c09b38b2f8cc809195d816fe14c3d11ea0fad18deb8a293fa04d3fcf","6ec8b3cbb583d396c33ee29488cc92ae4e358b84b7eec739c3a6029c349e295b");
        mainnet.put("b0e4fd1f5247607b3bc45bbb4f9e11cdd7bfaa503c573f5f46bd9b78c20f445d","bff89f2d889c4f72c35dad13f2d0f9058ede1f61f57c7f0db0fdeca36a44410a");
        mainnet.put("06f1cc6d737a73a30adf23882916a21d0f9723eac209e430840f64d9dde23397","3c17261a2361da8e82e705007ad1c5f35c4712f20d34522e4b588edbb7ab1380");
        mainnet.put("542b7b90466a322d4d3d5dfca4b77665655909c69bf93e48366b8f7147d960a2","56c0defa95f2039f26b091f03b1a049d3cba4b577ebe8fccf178f9da98da1910");
        mainnet.put("62213988a1a5da72e969acfae23d006f0578735997df5f009ae2f0e53d663075","58350f1b447372373a2a2f60b6a301fd708a9527294320f1171a6aedef197749");
        mainnet.put("e6b72ec3e27d1e6a08a64d73db972140b9ff149bdd9fc2c8c78a9cd869cc31b1","b8bdca02f08b4313b9e9051d5747d7472bae300ff2866480c73f37c3b4da10cb");
        mainnet.put("5732993d1305b266116d8ebf84974f8f3900b24510eb9adf9d8430fae7f67b90","99fd3ef49673538e60321a0dca5b5b3ee74b43e9788d98dfe7caed9f429b6d75");
        mainnet.put("a3c5b4b7a55662e04e405131bccaa007d520efe22b8527d4461238e4982be63c","40921869eae466df43132a88faef2f71b5c481f52bd8925b3752e5a27713c5d7");
        mainnet.put("67ccabfd373e9d92b8bc9e86bb0f38856d3de2ed62491ab6d1a4ce6bf6c3a4b5","d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607");
        MAINNET_HISTORICAL_PEGOUT_TRANSACTIONS = Map.copyOf(mainnet);        //GENESIS-to-8.425.850
    }

    /**
     * Returns the Bitcoin transaction hash associated with a given RSK
     * transaction hash and network.
     *
     * @param rskTransactionHash the RSK transaction hash used as key.
     * @param networkId the network identifier (e.g. {@link NetworkParameters#ID_TESTNET} or {@link NetworkParameters#ID_MAINNET})
     * @return an {@link Optional} containing the corresponding Bitcoin transaction hash if present, or {@link Optional#empty()} if not found
     * @throws IllegalStateException if the provided network is not supported.
     */
    public static Optional<String> get(String rskTransactionHash, String networkId) {
        return Optional.ofNullable(selectMap(networkId).get(rskTransactionHash));
    }

    private HistoricalPegoutTransactions() {}

    /**
     * Selects the appropriate historical pegout transaction map based on
     * the given network identifier.
     *
     * @param networkId the network identifier
     * @return the corresponding historical pegout transaction map
     * @throws IllegalStateException if the network is not supported
     */
    private static Map<String, String> selectMap(String networkId) {
        return switch (networkId) {
            case NetworkParameters.ID_TESTNET -> TESTNET_HISTORICAL_PEGOUT_TRANSACTIONS;
            case NetworkParameters.ID_MAINNET -> MAINNET_HISTORICAL_PEGOUT_TRANSACTIONS;
            default -> throw historicalPegoutTransactionNotFoundForNetwork(networkId);
        };
    }

    private static RuntimeException historicalPegoutTransactionNotFoundForNetwork(String networkId) {
        return new IllegalStateException("Historical pegout transactions are not defined for Network: " + networkId);
    }
}
