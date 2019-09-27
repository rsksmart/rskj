/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;

/**
 * This tests take serialized Orchid tries coming from that version of the Repository and check if the state root is the
 * expected. These serializations where performed on top of the ORCHID-0.6.1 codebase replicating the same operations.
 * This is an example for generating {@link SERIALIZED_ORCHID_TRIESTORE_SIMPLE}:
 *
 * {@code
 *     public void test1Simple() {
 *         Random random = new Random(0);
 *         Repository repository = new RepositoryImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB()), true),
 *              new HashMapDB(), new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());
 *         Repository track = repository.startTracking();
 *
 *         int maxAccounts = 10;
 *         int maxStorageRows = 5;
 *         for (int i = 0; i < maxAccounts; i++) {
 *             RskAddress addr = new RskAddress(randomBytes(random, 20));;
 *             track.createAccount(addr);
 *             AccountState a = track.getAccountState(addr);
 *             a.setNonce(new BigInteger(4 * 8,random));
 *             a.addToBalance(new Coin(BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(random.nextInt(1000)))));
 *             track.updateAccountState(addr, a);
 *             if (i >= maxAccounts / 2) {
 *                 // half of them are contracts
 *                 for (int s = 0; s < maxStorageRows; s++) {
 *                     track.addStorageBytes(addr, new DataWord(randomBytes(random, 32)), randomBytes(random, random.nextInt(40) + 1));
 *                 }
 *                 track.saveCode(addr, randomCode(random, 60));
 *             }
 *         }
 *
 *         track.commit();
 *         repository.flush();
 *
 *         System.out.println(Hex.toHexString(repository.getRoot()));
 *     }
 * }
 */
public class TrieConverterTest {
    // This is only to easily identify codes while debugging the trie.
    private static byte[] CODE_PREFIX = new byte[]{-1, -1, -1, -1};
    // expected state root: 0xb5e35460d1ee9e2d7586f4c06c70d9a918cb0d7a1728cd4c37088ea5352a6b0a
    private static byte[] SERIALIZED_ORCHID_TRIESTORE_WITH_LEADING_ZEROES_STORAGE_KEYS = Hex.decode("0000b5e35460d" +
            "1ee9e2d7586f4c06c70d9a918cb0d7a1728cd4c37088ea5352a6b0a00000000001d000000208a47d977cab7876c6e127707fedee" +
            "f1be33d85d9b204e41673e36591a9be233c000000460203000000fb4b0ac9a996daf435c502bffa00cb111e7cd57348ee9c72b32" +
            "3148f37edcb64001fa3ac61f48fb03a8714e507fe8f69f763b964644e2bead8909186f044cb2bdd000000202adde0f4259adf28a" +
            "d6319a20c04208de869d01fc80a88fac327ef8dd6b396b400000053f851841feff205892fd03576f6b6880000a056e81f171bcc5" +
            "5a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bf" +
            "ad8045d85a47000000020fefc6be98c7e881fe7fbc53ab3487be8f8d5db61c6e7bbf504e774abcc6b76fe0000004602010003000" +
            "0c6ef4d7d4f9b2a02587940096b517905372dbd849f2b4c4c762c2a42546b8dd009048bc62f6b71f7e93dd39294f5f67f3acbf8f" +
            "e2717ef7e45be6090b984365f000000207090ff34d4bb49da92c0b2ac0f9b022522dae031b1d59479744f0e7c87dc75490000005" +
            "3f85184b9366083892a9bf0f397f1080000a0bdd87c65ef86cdaccf1b130b35268b2e088958e083e2e1de0b45178a1954ad0aa0e" +
            "eb2aa9b64736f671d5b10ddde0fd644a3cec57412599c9ec7ec963909ecbb420000002012698d33e7f409518688502846b324e16" +
            "ec93ef2633e793c6ab4b45418a090e4000000460203000000fd6e7c19f7ad6418eec4073127855c2deee2b467a5f2c3f3e0c6854" +
            "2bb693e24882adde0f4259adf28ad6319a20c04208de869d01fc80a88fac327ef8dd6b396b40000002009048bc62f6b71f7e93dd" +
            "39294f5f67f3acbf8fe2717ef7e45be6090b984365f000000460203000000fd315b0a1686123a38292de531875a23f01acadf52b" +
            "1f2e86a195d3641515d19889f51cd618705f61f5cd2c1f6b851839f16862681690afaecc7df01c4c716cf9c000000201fa3ac61f" +
            "48fb03a8714e507fe8f69f763b964644e2bead8909186f044cb2bdd00000053f851840102a0fd8914542ba12a337c0000a03d7d5" +
            "893a743944e58135370c2ed49d596956e8eee0c8dc2109fff44fa115706a04e51cc72e23a15a0d4b2f5396b83081e7835852d4c4" +
            "411be30ceb8759782e06900000020c3baa59c57520ee36d2dd671909962068e8f6c087e01ef53d102300f5162134a00000046020" +
            "10003000012027d66f440c5ffcdfa3150dfa3f2050572497ca8a1f516d89faa2b00e56535f2423ea188cc6d9c6de585eb86ac2a2" +
            "e82617d66d00c1b61bda5243f628c1d1f0000002024425f23244dc4d607ec38be2273b878749888f35d1f3d3cba945b6c1acb625" +
            "d000000460201000300007b72db60503f58eadfc5b4bd09aa684eaa3528040f79f74337bbc17c8663f2daa8275f6110d410de872" +
            "3389de2ee90bc9eacf29565ff6edf93d092136c7d67f00000002090f0aaabb52cf5345d6a248edb78970db4f8f4157334c969133" +
            "636473d962a6b00000053f85184f1d00c2d8907c0860e5a80dc0000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc" +
            "001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47000000020a8275f6110d41" +
            "0de8723389de2ee90bc9eacf29565ff6edf93d092136c7d67f0000000460203000000fe0a38f4f45467692febff06d6642424cb6" +
            "f6954b6c448674b0b4591c38ba0906090f0aaabb52cf5345d6a248edb78970db4f8f4157334c969133636473d962a6b00000020b" +
            "1a192d83c0c26dcb669c347da7796b10aa24dbca19d19ea114ee11814cad715000000460203000000fb9db2e6e4f07da4b81c3b7" +
            "e66b90b03666b6b84b23d7d4bcfd0536eccf402ad60417493da590e9b6284a94a43ca859e79771b107723c155c9576fb34162d21" +
            "e50000000205e6d2b1e84adb38f713961092d23ba8083860e6ca06a96c1f254919f8b958ee100000046020100030000b1a192d83" +
            "c0c26dcb669c347da7796b10aa24dbca19d19ea114ee11814cad7158a47d977cab7876c6e127707fedeef1be33d85d9b204e4167" +
            "3e36591a9be233c00000020b0585cb2fdf2163f61e3505d8ceece2dc152162113a368a535785097e1f8dcfb00000046020300000" +
            "0fc8b8697e636cbb9a1173aaf4506a05cff878220ad05d39fcfc9059a09d1a548e0437fb25566d01816b4a0416ddfc8509a4f9c3" +
            "30a3ac401a95af2c4cabf50d97300000020cacb5c364d3a0d4484b9f5799acd0280d284292b665855f4fa78d17630236dd000000" +
            "053f8518423c29b62890e33fafbdd50580000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a" +
            "0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000209f51cd618705f61f5cd2c1f6b851839" +
            "f16862681690afaecc7df01c4c716cf9c00000053f851840e264af6891cf2bd1abf2a980000a07f732326c16d3788eb97d1721c2" +
            "10d3bf84de7246a32172b1083a57d0b909a05a0787f05f10b18b39884102cf8e98d1ea7ba8be730d6e4ee5944c19df434dadc1b0" +
            "0000020662b1587771c16e780e7988169f42aa3d6121c06bb5966459068ace8a93f15d900000053f851843af01d4f891a9dfe6a9" +
            "20ccc0000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc" +
            "703c0e500b653ca82273b7bfad8045d85a47000000020551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7" +
            "953f400000053f85184e7f2bc408920a26da277a1280000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb" +
            "5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a4700000002012027d66f440c5ffcdfa3" +
            "150dfa3f2050572497ca8a1f516d89faa2b00e56535000000460203000000fc4776403afa21a78b4db6dbaba5340152e4c3eb973" +
            "f74a59a3f94ee6b3dab17f07090ff34d4bb49da92c0b2ac0f9b022522dae031b1d59479744f0e7c87dc754900000020c6ef4d7d4" +
            "f9b2a02587940096b517905372dbd849f2b4c4c762c2a42546b8dd0000000460201000300002e17fe2c7eab58807f3c34a2912bb" +
            "6302ecf1310678ba36fac0bdc2361ded4e6b0585cb2fdf2163f61e3505d8ceece2dc152162113a368a535785097e1f8dcfb00000" +
            "020437fb25566d01816b4a0416ddfc8509a4f9c330a3ac401a95af2c4cabf50d97300000052f85084277235fa88b469471f80140" +
            "000a07807814a944a40791dda112e3ef637a3c9fa86ed93660215ce88c704f592ec4ba0ad4c522fca549146c390f5ae49a5634a5" +
            "a1cb055d221b5c2bbe045bbe218c17f00000020f2423ea188cc6d9c6de585eb86ac2a2e82617d66d00c1b61bda5243f628c1d1f0" +
            "00000460203000000fc6286655e31404005b318f9a62c5a7d37f166cc3ea1e9ad3d078309f98056a2f0662b1587771c16e780e79" +
            "88169f42aa3d6121c06bb5966459068ace8a93f15d900000020ce7f418d697600040954c01b2862d2ce769e9dd6df5ba9b2e0cf2" +
            "d0661678cba00000046020100030000fefc6be98c7e881fe7fbc53ab3487be8f8d5db61c6e7bbf504e774abcc6b76feaef5b7585" +
            "489b486453032b913bd65c6d6d1bfd3c734c612ebe4e57a6ff046dd000000204df7fe53ef2166b4ba5f874aff489b40a57d73798" +
            "1804e13038f87b9d6715d14000000460203000000fc98c8a8cb46de7b1920ea33b91c9d7eb5bb0621cf5a9cc8443e9ed70f81ab7" +
            "e20551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7953f4000000202e17fe2c7eab58807f3c34a2912bb" +
            "6302ecf1310678ba36fac0bdc2361ded4e6000000460203000000fca1a35381df13d63d5e2665dfe4a9734759ea4718b6d0c8351" +
            "1da7b1547c8f680cacb5c364d3a0d4484b9f5799acd0280d284292b665855f4fa78d17630236dd000000020b5e35460d1ee9e2d7" +
            "586f4c06c70d9a918cb0d7a1728cd4c37088ea5352a6b0a00000046020100030000ce7f418d697600040954c01b2862d2ce769e9" +
            "dd6df5ba9b2e0cf2d0661678cba24425f23244dc4d607ec38be2273b878749888f35d1f3d3cba945b6c1acb625d0000002041749" +
            "3da590e9b6284a94a43ca859e79771b107723c155c9576fb34162d21e5000000053f85184fdf2e4058929cbc63f11222c0000a01" +
            "df9de51ea26b6a55ba77905255343eb980a5088a77cea2476748da73c3e046fa0fa62470743f1569e68d0d206d4ce58d4830542e" +
            "f10ae336ee01de58e34edc9eb000000207b72db60503f58eadfc5b4bd09aa684eaa3528040f79f74337bbc17c8663f2da0000004" +
            "7020100030001005e6d2b1e84adb38f713961092d23ba8083860e6ca06a96c1f254919f8b958ee14df7fe53ef2166b4ba5f874af" +
            "f489b40a57d737981804e13038f87b9d6715d1400000020aef5b7585489b486453032b913bd65c6d6d1bfd3c734c612ebe4e57a6" +
            "ff046dd00000046020100030000c3baa59c57520ee36d2dd671909962068e8f6c087e01ef53d102300f5162134a12698d33e7f40" +
            "9518688502846b324e16ec93ef2633e793c6ab4b45418a090e4");
    // expected state root 0x3513db97e8e9afff71727bce4b2ac01ff3b874e4814cbb31828829b730fd7eca
    private static byte[] SERIALIZED_ORCHID_TRIESTORE_SIMPLE = Hex.decode("00003513db97e8e9afff71727bce4b2ac01ff3b" +
            "874e4814cbb31828829b730fd7eca00000000001e00000020efca24d11fd5d95b6494378857d8e5856e69a78cb7bf7922c881fbb" +
            "85ded0f1b000000460203000000fdb14332af18a02002d98c7cd3162d3e9bf8b3661f50f4d69e83c184fcc02b5178662b1587771" +
            "c16e780e7988169f42aa3d6121c06bb5966459068ace8a93f15d900000020a5a9f4b9699784b58dde3e02788d1b6d58ecfc228bf" +
            "a79503663b7113004748e00000046020100030000efca24d11fd5d95b6494378857d8e5856e69a78cb7bf7922c881fbb85ded0f1" +
            "b3cbbb09e97eb85ba23ec1822eea6e057f24e8d4f6d698c4fa671df3b574f380b00000020cacb5c364d3a0d4484b9f5799acd028" +
            "0d284292b665855f4fa78d17630236dd000000053f8518423c29b62890e33fafbdd50580000a056e81f171bcc55a6ff8345e692c" +
            "0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a4700" +
            "0000020ea63ce51a897193cf2df6e91a9814fde7d812cc53a9b99dbfb8883099857b170000000460201000300006132777b0dcb4" +
            "885220f39408aa27c32693b518e691b38188b187a25d6bdd398abbe1f7f9a1e84fe21b8587100c3b74a36e24233855f7f0f17e00" +
            "ac1552bfd7e0000002056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421000000060201000000000" +
            "0000020ee9767b78a6ce5f0d753af61043266f3e5139910f0706da4d3210950b9f0695a00000053f851841e173a4c8908ac72304" +
            "89e800000a011eb4d4c3a3be276820b4732166e964512357fc6c95344744621f3dce2140a4aa0ce76085c8aafb41c0ef1a91e297" +
            "fa8f51dfd1738825a382077617aad9186897c000000204d98ab7395024fcee86793faec0fb1407b8a988774caee979e55594af24" +
            "8094700000046020100030000ca3f6737ecaa4957fc253a90b97a69f6cd85b35c3315b4d96150a22495604ee80faed22496bdf7a" +
            "e9aa65ac1a81fed84f23fa2d8f3313a725ad494df659b8fbb0000002056232e967ce61fd9429822dc5ead2e878362903ce20fd7c" +
            "1929abcd6ebd5170d00000046020100030000ea63ce51a897193cf2df6e91a9814fde7d812cc53a9b99dbfb8883099857b170a5a" +
            "9f4b9699784b58dde3e02788d1b6d58ecfc228bfa79503663b7113004748e00000020abbe1f7f9a1e84fe21b8587100c3b74a36e" +
            "24233855f7f0f17e00ac1552bfd7e000000460203000000fd75bcb031c9ac0e57cee2317aa7e86bd653931258f8439957c552dda" +
            "4297c33505c81c3a5275e6bf8ab5bada94c6e2debac620dbe95fd221fe08236b9549c31b2000000206132777b0dcb4885220f394" +
            "08aa27c32693b518e691b38188b187a25d6bdd398000000460203000000fd50d1a9c0ef89eb1eaf1332eff254b9a3acf5238c5b6" +
            "8641a88ed3d8aa3e47b40cacb5c364d3a0d4484b9f5799acd0280d284292b665855f4fa78d17630236dd0000000204df7fe53ef2" +
            "166b4ba5f874aff489b40a57d737981804e13038f87b9d6715d14000000460203000000fc98c8a8cb46de7b1920ea33b91c9d7eb" +
            "5bb0621cf5a9cc8443e9ed70f81ab7e20551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7953f40000002" +
            "09c6d8278b9f2ad00ad97366bed668c68113ef3eb08d27d30ce6d12489c75c5e4000000460203000000fbe855b478adef5ad6a85" +
            "f42b0c259f07361a5fa5e76d323141ff4b75ed85b98c0ee9767b78a6ce5f0d753af61043266f3e5139910f0706da4d3210950b9f" +
            "0695a0000002090185a8f6e377afc0e92713ddeb385ba25ed63ad13f21c6a9c44919d952e372c00000046020100030000d6330ec" +
            "57ade6c9f2d723985abc70790c752b5719e59ed7dc22d57c80f1f081aed8b70427b97a4b2aee2e98cc8f88d7d1b8cbc1ef4e2282" +
            "7585ccc6e7467b32600000020551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7953f400000053f85184e" +
            "7f2bc408920a26da277a1280000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d246018" +
            "6f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000200faed22496bdf7ae9aa65ac1a81fed84f23fa2d8f" +
            "3313a725ad494df659b8fbb000000460201000300002628411db3608a02b233dfc11c22c118b4331eeb040d6a119b899a08eacc8" +
            "8182bea7e6337e2cfc8b2a5fd0468ba7b66442526e9ab81ae459211c0b00e04cdba0000002090f0aaabb52cf5345d6a248edb789" +
            "70db4f8f4157334c969133636473d962a6b00000053f85184f1d00c2d8907c0860e5a80dc0000a056e81f171bcc55a6ff8345e69" +
            "2c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47" +
            "0000000203cbbb09e97eb85ba23ec1822eea6e057f24e8d4f6d698c4fa671df3b574f380b00000047020100030001009c6d8278b" +
            "9f2ad00ad97366bed668c68113ef3eb08d27d30ce6d12489c75c5e45a452145b2730e85950ee225954945f40b3350c999c7e4fbf" +
            "c2742eac5ab3cbc00000020d6330ec57ade6c9f2d723985abc70790c752b5719e59ed7dc22d57c80f1f081a00000046020300000" +
            "0fb1b767eb1e51fab7620a9c0db80be314e384ff1e62502a1d3ff6f00e1d4735b603747803acb776e564ed21b3df0cc9b7b9a020" +
            "081239cb5eaf9fbbc09fe158339000000202adde0f4259adf28ad6319a20c04208de869d01fc80a88fac327ef8dd6b396b400000" +
            "053f851841feff205892fd03576f6b6880000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a" +
            "0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47000000020afd73bf33a6607a7ee747603b26f016" +
            "bf4c1e7a0b3e500cc2ffc10a8974efb2300000053f851840102a0fd8914542ba12a337c0000a07a6bf06e8e84f90933b79bf84ff" +
            "45242eb04c9677f18cb0704836ad9467e0f96a02328404cb3d202860b72aac53ab103d38c365b60331824d80d164f6921e27a320" +
            "00000205a452145b2730e85950ee225954945f40b3350c999c7e4fbfc2742eac5ab3cbc000000460203000000fbb9f067deb5906" +
            "3bb101cc49e1570b7bb8ad19e97cb0fcf831a150aeda4f892202adde0f4259adf28ad6319a20c04208de869d01fc80a88fac327e" +
            "f8dd6b396b4000000205c81c3a5275e6bf8ab5bada94c6e2debac620dbe95fd221fe08236b9549c31b200000053f851847058017" +
            "18918650127cc3dc80000a0d575306f01546673a74fceec78818c646a408915ed93f0731aaa8fa3283de615a08be9da6943e5bf8" +
            "40b8ffaaa5f88203ca01a5c609c2f9c6e427258f9f274eefe000000202bea7e6337e2cfc8b2a5fd0468ba7b66442526e9ab81ae4" +
            "59211c0b00e04cdba000000460203000000fd6c47ec9caf640a114e6c7c7e0e98f83f05f3cd8d9497d8f7bea56ea64dd507587aa" +
            "9422af22e8c3d77b7aa096944a729a8532464631bdcb729021bdf4351937100000020ca3f6737ecaa4957fc253a90b97a69f6cd8" +
            "5b35c3315b4d96150a22495604ee8000000470201000300010090185a8f6e377afc0e92713ddeb385ba25ed63ad13f21c6a9c449" +
            "19d952e372c4df7fe53ef2166b4ba5f874aff489b40a57d737981804e13038f87b9d6715d1400000020ed8b70427b97a4b2aee2e" +
            "98cc8f88d7d1b8cbc1ef4e22827585ccc6e7467b326000000460203000000fb4b0ac9a996daf435c502bffa00cb111e7cd57348e" +
            "e9c72b323148f37edcb6400afd73bf33a6607a7ee747603b26f016bf4c1e7a0b3e500cc2ffc10a8974efb23000000207aa9422af" +
            "22e8c3d77b7aa096944a729a8532464631bdcb729021bdf4351937100000053f85184c47fa07489221920e76a48b40000a05aebe" +
            "7d8638c97d07675c9788d96105320f67b48d81ce34b43c31a8e0f60a2d9a0395139c01d04c4b144535c688055fa4baab703373af" +
            "103800733b60b3d857f61000000203513db97e8e9afff71727bce4b2ac01ff3b874e4814cbb31828829b730fd7eca00000046020" +
            "10003000056232e967ce61fd9429822dc5ead2e878362903ce20fd7c1929abcd6ebd5170d4d98ab7395024fcee86793faec0fb14" +
            "07b8a988774caee979e55594af248094700000020662b1587771c16e780e7988169f42aa3d6121c06bb5966459068ace8a93f15d" +
            "900000053f851843af01d4f891a9dfe6a920ccc0000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e36" +
            "3b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000203747803acb776e564ed21b3df" +
            "0cc9b7b9a020081239cb5eaf9fbbc09fe15833900000053f85184ca02da2a8912f939c99edab80000a0db06ef684dcdf23ad7c5d" +
            "1c1afdcf642d087e4c9643e52365589efe8d3bf0c62a0560ebca6bb0dc143e6ba187c62c764d01345f4008828066b71c0f10c49d" +
            "08a4a000000202628411db3608a02b233dfc11c22c118b4331eeb040d6a119b899a08eacc8818000000460203000000fd1471e9e" +
            "8a8ced25fd7fe0dacc8484996ded2a96d8890ce96168b2387174120c090f0aaabb52cf5345d6a248edb78970db4f8f4157334c96" +
            "9133636473d962a6b");

    @Test
    public void getOrchidAccountTrieRootWithCompressedStorageKeys() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        TestUtils.getRandom().setSeed(0);
        int maxAccounts = 10;
        int maxStorageRows = 5;
        for (int i = 0; i < maxAccounts; i++) {
            // Create random accounts/contracts
            RskAddress addr = TestUtils.randomAddress();
            track.createAccount(addr);
            // Set some random balance
            AccountState a = track.getAccountState(addr);
            a.setNonce(TestUtils.randomBigInteger(4));
            // Balance between 1 and 100 SBTC
            a.addToBalance(TestUtils.randomCoin(18, 1000));
            track.updateAccountState(addr, a);
            if (i >= maxAccounts / 2) {
                // half of them are contracts
                track.setupContract(addr);
                for (int s = 0; s < maxStorageRows / 2; s++) {
                    track.addStorageBytes(addr, TestUtils.randomDataWord(), TestUtils.randomBytes(TestUtils.getRandom().nextInt(40) + 1));
                }
                for (int s = 0; s < maxStorageRows / 2; s++) {
                    track.addStorageBytes(addr, DataWord.valueOf(TestUtils.randomBytes(20)), TestUtils.randomBytes(TestUtils.getRandom().nextInt(40) + 1));
                }

                track.saveCode(addr, randomCode(60));
            }
        }

        track.commit();
        TrieConverter tc = new TrieConverter();
        byte[] oldRoot = tc.getOrchidAccountTrieRoot(repository.getTrie());
        Trie atrie = deserialize(SERIALIZED_ORCHID_TRIESTORE_WITH_LEADING_ZEROES_STORAGE_KEYS);

        Assert.assertThat(Hex.toHexString(oldRoot), is(atrie.getHashOrchid(true).toHexString()));
    }

    @Test
    public void test1Simple() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));

        Repository track = repository.startTracking();

        TestUtils.getRandom().setSeed(0);
        int maxAccounts = 10;
        int maxStorageRows = 5;
        for (int i = 0; i < maxAccounts; i++) {
            // Create random accounts/contracts
            RskAddress addr = TestUtils.randomAddress();
            track.createAccount(addr);
            // Set some random balance
            AccountState a = track.getAccountState(addr);
            a.setNonce(TestUtils.randomBigInteger(4));
            // Balance between 1 and 100 SBTC
            a.addToBalance(TestUtils.randomCoin(18, 1000));
            track.updateAccountState(addr, a);
            if (i >= maxAccounts / 2) {
                // half of them are contracts
                track.setupContract(addr);
                for (int s = 0; s < maxStorageRows; s++) {
                    track.addStorageBytes(addr, TestUtils.randomDataWord(), TestUtils.randomBytes(TestUtils.getRandom().nextInt(40) + 1));
                }
                track.saveCode(addr, randomCode(60));
            }
        }

        track.commit();
        TrieConverter tc = new TrieConverter();
        byte[] oldRoot = tc.getOrchidAccountTrieRoot(repository.getTrie());
        Trie atrie = deserialize(SERIALIZED_ORCHID_TRIESTORE_SIMPLE);

        Assert.assertThat(Hex.toHexString(oldRoot), is(atrie.getHashOrchid(true).toHexString()));
    }

    private byte[] randomCode(int maxSize) {
        int length = TestUtils.getRandom().nextInt(maxSize - CODE_PREFIX.length - 1) + 1;
        return TestUtils.concat(CODE_PREFIX, TestUtils.randomBytes(length));
    }


    private static Trie deserialize(byte[] bytes) {
        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        int expectedSize = Short.BYTES + keccakSize;
        if (expectedSize > bytes.length) {
            throw new IllegalArgumentException(
                    String.format("Expected size is: %d actual size is %d", expectedSize, bytes.length));
        }

        byte[] root = Arrays.copyOfRange(bytes, Short.BYTES, expectedSize);
        TrieStore store = trieStoreDeserialization(bytes, expectedSize, new HashMapDB());

        return store.retrieve(root).get();
    }

    private static TrieStore trieStoreDeserialization(byte[] bytes, int offset, KeyValueDataSource ds) {
        int current = offset;
        current += Short.BYTES; // version

        int nkeys = readInt(bytes, current);
        current += Integer.BYTES;

        for (int k = 0; k < nkeys; k++) {
            int lkey = readInt(bytes, current);
            current += Integer.BYTES;
            if (lkey > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for key expected:%d actual:%d total:%d",
                        lkey, bytes.length - current, bytes.length));
            }
            byte[] key = Arrays.copyOfRange(bytes, current, current + lkey);
            current += lkey;

            int lvalue = readInt(bytes, current);
            current += Integer.BYTES;
            if (lvalue > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for value expected:%d actual:%d total:%d",
                        lvalue, bytes.length - current, bytes.length));
            }
            byte[] value = Arrays.copyOfRange(bytes, current, current + lvalue);
            current += lvalue;
            ds.put(key, value);
        }

        return new TrieStoreImpl(ds);
    }

    // this methods reads a int as dataInputStream + byteArrayInputStream
    private static int readInt(byte[] bytes, int position) {
        int ch1 = Byte.toUnsignedInt(bytes[position]);
        int ch2 = Byte.toUnsignedInt(bytes[position + 1]);
        int ch3 = Byte.toUnsignedInt(bytes[position + 2]);
        int ch4 = Byte.toUnsignedInt(bytes[position + 3]);
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new IllegalArgumentException(
                    String.format("On position %d there are invalid bytes for a short value %s %s %s %s",
                            position, ch1, ch2, ch3, ch4));
        } else {
            return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4);
        }
    }

}