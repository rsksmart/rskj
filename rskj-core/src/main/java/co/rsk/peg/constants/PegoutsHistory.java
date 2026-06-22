/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg.constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ethereum.config.NetworkName;
import org.jspecify.annotations.Nullable;

import co.rsk.bitcoinj.core.Sha256Hash;

/**
 * Preserves historical pegouts before RSKIP559,
 * whose ordering is not aligned with the deterministic selection algorithm.
 */
public class PegoutsHistory {

    /**
     * Represent hardcoded reference to pegout
     */
    public static record Ref(Sha256Hash btcTxHash, long rskBlock) {

        public static Ref from(String hash, long rskBlock) {
            return new Ref(Sha256Hash.wrap(hash), rskBlock);
        }

        public static Ref[] listFrom(String hash, long rskBlock) {
            return new Ref[] { Ref.from(hash, rskBlock) };
        }

        @Override
        public String toString() {
            return String.format("%s@%d", btcTxHash, rskBlock);
        }
    }

    @Nullable
    public static Map<Long, Ref[]> getHardcodedPegouts(String networkName) {
        if (NetworkName.MAINNET.getName().equals(networkName)) {
            return MAINNET;
        } else if (NetworkName.TESTNET.getName().equals(networkName)) {
            return TESTNET;
        }
        return null;
    }

    private static final Map<Long, Ref[]> MAINNET;

    // MAINNET data collected up to the 8.8M height
    static {
        var m = new HashMap<Long, Ref[]>();
        // These are known diff between historical outputs and RSKIP559 stable output algo
        m.put(3345557L, Ref.listFrom("5965a75e7e56ed4a308cc1bf8d94415c03c6a56f7302ae488e1d3fa05cd70e61", 3341556L));
        m.put(3381087L, Ref.listFrom("8508d6a45b5a3e4ab8396c3f5ad895107e6a0ded9311729804b806661763779d", 3377083L));
        m.put(3381093L, Ref.listFrom("00a726e67845f3d16a263ffde47315457a3388b7b9ce10f73c05560a69d09a40", 3377083L));
        m.put(3441427L, Ref.listFrom("3aa787c1409f942991086de6c26fe7330ca2334e0261f1df57e1f1d15a5298d1", 3437424L));
        m.put(3441438L, Ref.listFrom("6ec8b3cbb583d396c33ee29488cc92ae4e358b84b7eec739c3a6029c349e295b", 3437428L));
        m.put(3655284L, Ref.listFrom("bff89f2d889c4f72c35dad13f2d0f9058ede1f61f57c7f0db0fdeca36a44410a", 3651282L));
        m.put(5002812L, Ref.listFrom("40921869eae466df43132a88faef2f71b5c481f52bd8925b3752e5a27713c5d7", 4998807L));
        m.put(7073815L, Ref.listFrom("d2ca62b50287a300122672a9b05e08422ec36e41d0424c2ba7612bf1ca96d607", 7069808L));
        // These are not required after RSKIP559 activation when old output algo will be dropped
        // because new sorting/selection algo producing same outputs on this heights
        m.put(3334327L, Ref.listFrom("49796a89abfd770308cf4f4a8c49e3f97ac2f0edb33bdba98434603c82135802", 3330316L));
        m.put(3340058L, Ref.listFrom("2c6f69d2f7746b01dba7c9cdf248973a239fcc78f7dd5971f99992e941a0715e", 3336051L));
        m.put(3344303L, Ref.listFrom("20806dadc7c9cbe777c2599983d38831461f7b701afb1ac93a3ec386c376edb9", 3340302L));
        m.put(3438812L, Ref.listFrom("5f1119f0d62babb36c513a41d4ccb4b0545a2a2dbd2b07ffd98d259e1c194e1f", 3434812L));
        m.put(4261189L, Ref.listFrom("3c17261a2361da8e82e705007ad1c5f35c4712f20d34522e4b588edbb7ab1380", 4257188L));
        m.put(4580303L, Ref.listFrom("56c0defa95f2039f26b091f03b1a049d3cba4b577ebe8fccf178f9da98da1910", 4576299L));
        m.put(4580424L, Ref.listFrom("58350f1b447372373a2a2f60b6a301fd708a9527294320f1171a6aedef197749", 4576407L));
        m.put(4675306L, Ref.listFrom("b8bdca02f08b4313b9e9051d5747d7472bae300ff2866480c73f37c3b4da10cb", 4671305L));
        m.put(4675313L, Ref.listFrom("99fd3ef49673538e60321a0dca5b5b3ee74b43e9788d98dfe7caed9f429b6d75", 4671312L));

        MAINNET = Collections.unmodifiableMap(m);
    }

    private static final Map<Long, Ref[]> TESTNET;

    // TESTNET data collected up to the 7.6M height
    static {
        var t = new HashMap<Long, Ref[]>();
        // These are known diff between historical outputs and RSKIP559 stable output algo
        t.put(1120318L, Ref.listFrom("fc48fd9099b0ed41511e5d6da7a536880ef7dd1deb1ed1289e3c651e934aaa49", 1120291L));
        t.put(1865157L, Ref.listFrom("816a0708cfe301b1076d795d74b36955f4ef1fc477487772d06121a5dedfeca0", 1865146L));
        t.put(2589068L, new Ref[] {
             Ref.from("2924e68abd7371f9e34cb817213528478cc8ec22f48389f756623d5a4c35e0a4", 2589056L),
             Ref.from("c6f1fe4aba2e98cc9e190ae7aa6664901d417172847232a03a8112a3342ef53e", 2589056L),
             Ref.from("4dbe93aaaab473d53039e88ab6f4b81704c3c9ae34a60fa94e32fc350763a9d3", 2589056L)
        });
        t.put(2589071L, Ref.listFrom("a476e91aeca06b6c52d276fb6734c2a4849dfe9f9feca2a055ad5f3add2ec328", 2589056L));
        t.put(2589077L, Ref.listFrom("0359d4b1621b4faa203f94394dd0c9f5094fcf6cad4acf6af604da9ce1ec3217", 2589056L));
        t.put(2589086L, Ref.listFrom("6ceb7c9be0f828f7d6e6d847671aba87760b72ae4e63a14afb1e11170d306d4f", 2589068L));
        t.put(2589112L, new Ref[] {
            Ref.from("9d5b2dc437edc59f216257e202e9ab1bc6d10a791624cb1e976879049a74178d", 2589086L),
            Ref.from("0c5b2a437187481623e755ec0f4c7095fcdf66e6eff5b68852b0c8ad9f1063da", 2589086L),
            Ref.from("30f776e7e2842db08ffe1424cba3caf8857654fa2269c0cfd22a9bae177ee39c", 2589086L)
        });
        t.put(2589115L, Ref.listFrom("caf72f6d65f287981afdba49d617a1bad8490e2b0a445629e797a7864d95b15f", 2589086L));
        t.put(2589121L, Ref.listFrom("c6184c576891dea44f2aa6269315a84c946486d3aa3845e8e29c65b63da778f7", 2589086L));
        t.put(2589135L, Ref.listFrom("0cbb38f1abc521691983be493e85b7115f317910e22ebeb55765c63615c07524", 2589112L));
        t.put(2589142L, Ref.listFrom("259bbdd9eb7c0deba308d084365cdb6e02a34f7827b2ee0ca7e341dfa90daa8e", 2589112L));
        t.put(2589148L, Ref.listFrom("0762be9af2d43df6b5bd6ef2ead5876137235ee7efeed0f17f1401bef5f18ef8", 2589112L));
        t.put(2589153L, Ref.listFrom("6dd692b7c2aa1b8a5e14b2f23be590a5b0f45a74c5a7933f824abf9d40cc139f", 2589112L));
        t.put(2589163L, Ref.listFrom("b0b0caa4338dca07086da1ef503aaa5c81250e3b7390cff304d6bc83169d8bc7", 2589112L));
        t.put(2589169L, Ref.listFrom("19a8eac8d1735bd80cdd087b132b1c7b73ebf5c0fe1691892cad1a7dad3e5086", 2589086L));
        t.put(2589174L, Ref.listFrom("6555870f4ab9eb25a2a3eaec6aa2d9c0e347fbaf45eba1737e77a234945214a7", 2589112L));
        t.put(2589179L, Ref.listFrom("9a275de558b3de838eaf9674dfe7889da9283cac7ac661dc77db07337f3eaa8f", 2589112L));
        t.put(2589190L, Ref.listFrom("9dd5ad7357ee9d9a2c28b13b6c44aa69838a84f4b274a5e34c629300d8267702", 2589112L));
        t.put(2589196L, Ref.listFrom("2dbbc05c756d339755f8e900c78163749592ce952f91f3c6d424053282749efa", 2589112L));
        t.put(2589202L, Ref.listFrom("95e665b9dd56c5e33f22c209ac57ef46903bd1b7a3a3a04a62150a2277d75a69", 2589112L));
        t.put(2589209L, Ref.listFrom("c074389cf3f0979272292cde8988b9ef73b2fb176516a9042f8935707e8a5c8c", 2589086L));
        t.put(2589308L, Ref.listFrom("8262e3e1ff484a30eb2833c7a504002f3de07aa3fcd5a76c4243b7027e728bee", 2589112L));
        t.put(2589325L, Ref.listFrom("3fc1d66b07e38ae41b534040e55f9159985cd5cc9f0eab8497c5d53d3b8721b8", 2589308L));
        t.put(2589332L, Ref.listFrom("300fdbc7c63a9b6e5738d25788e5b1299c2dfc7eb3331b11cd22766696674591", 2589308L));
        t.put(2589338L, Ref.listFrom("420c5bdc5ae84a49417c120b978231a50b1a67142179fda46fa6635f44f9dfaf", 2589308L));
        t.put(2589375L, Ref.listFrom("9c0016a1abecd5c7d62ce5db5f80655f9e69d285ff3ebc0db00e7fb860549c4f", 2589308L));
        t.put(2589380L, Ref.listFrom("7c49a9651afe24236a56afe3b6ad3d1f822ea956cf1927be5d0ee183a152b39e", 2589308L));
        t.put(2589390L, Ref.listFrom("a342a0668fc637e28a610a2d36b235af899b7ec1b24e939d8a37e076fe57bc8c", 2589308L));
        t.put(2589501L, Ref.listFrom("c00f8917a3213500388121060821e371b973f27504947018905abd04bb07a7f1", 2589308L));
        t.put(2589515L, Ref.listFrom("0a582d15ccc473e2a6383828bea3825ba5fa2d4124cf66cec66ce7a0535f8bfd", 2589308L));
        t.put(2589521L, Ref.listFrom("b66e4df75960f16f67c546bc3b44b1ce1f4fa55c15637fb0c662c036dd4d41e8", 2589056L));
        t.put(2589527L, Ref.listFrom("302c8ba636331d92b0576599aa3fb06459040e1071cfa7975c132b50cfba1c40", 2589308L));
        t.put(2589645L, Ref.listFrom("d52f8fe56df1254fa01efb4653f4b4d6d088f50255c83b50bd3f66c33455c8ad", 2589056L));
        t.put(3106055L, Ref.listFrom("5369d7bc18f8b1ec6cdaaff6c984a554991eb5ae2d177d916b7759011f468940", 3106043L));
        t.put(5788165L, Ref.listFrom("35e2108cfbb009bac4c2b52efc31ff92cd36c8c803cee459afccfc9d3a81a2cf", 5788151L));
        t.put(6858657L, Ref.listFrom("9a163ae0df24833af87992fa0e3a09e4c0bed27a660e6fd94814b0da48c629e6", 6858644L));

        // These are not required after RSKIP559 activation when old output algo will be dropped
        // because new sorting/selection algo producing same outputs on this heights
        t.put(695811L, Ref.listFrom("a9bdc4e4a48a3e3754b2722b3e61eeca9ae4009379a62a8313acf485c79171c1", 695800L));
        t.put(739307L, Ref.listFrom("648858fe92cd7c9078b9870852d8abceecd472a8d1102fd767ec658fe414af9c", 739296L));
        t.put(752839L, Ref.listFrom("bb52a27915b91e628066d51713f1f39ac3f0770919e84a4fd5285841496d77ca", 752823L));
        t.put(1112233L, Ref.listFrom("7f199079f5c55b5ea4d4bf150c105bbac1af068f96e2897e88bb798289578c75", 1112212L));
        t.put(1638761L, Ref.listFrom("3d4f1c085e42b273ddd327fc30a6633b1277d952d3b5e76156acbdeffdaf7809", 1638742L));
        t.put(1865172L, Ref.listFrom("fa2350f918db4a59b6f4a74b7f10420dc309226d87c8c0b0ce56d71fd94abad8", 1865146L));
        t.put(2589423L, Ref.listFrom("181a0390efa4866580e586159201756b8dc1089661d5a14a3b1f8aa4a4fafb41", 2589308L));
        t.put(2589656L, Ref.listFrom("496a1d5334751c0cca06ca40becc276672c37845deca8ae613250250f8f75bf5", 2589112L));
        t.put(2589662L, Ref.listFrom("a4a3f843e789b1be9a2aa5155d2ae7cb54709f02c78115c8392ad4efbbcffce1", 2589112L));
        t.put(2863896L, Ref.listFrom("1a7f93e39d3e7a87f26d5cfd8047d909945250a2c87c80f710085d4d97e940fc", 2863880L));
        t.put(3364542L, Ref.listFrom("75db78f6c0ba87bad1d7bd41a1b4bc412a4ae069916e6a47eb713afa92f3267f", 3364531L));
        t.put(5577212L, Ref.listFrom("342b34571db7d9bc2783ec490b6a2a6abddf34c0ea0cf27e14b65e238f4eda34", 5577158L));
        t.put(6858665L, Ref.listFrom("5bd422c96cabc0c4adecc4d7a2a23dd7c18ace92fb86348910e596449570a45f", 6858649L));
        t.put(7440352L, Ref.listFrom("7cbe57e4b3c79a4187e21c1185a91212aa832a7dc9ff988f6cefb2be06b1fb86", 7440339L));

        TESTNET = Collections.unmodifiableMap(t);
    }
}
