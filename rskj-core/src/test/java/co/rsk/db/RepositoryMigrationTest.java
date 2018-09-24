package co.rsk.db;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.trie.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

import static org.hamcrest.Matchers.is;

/**
 * Created by SerAdmin on 10/24/2018.
 */
public class RepositoryMigrationTest {
    public static final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
    public static final RskAddress HORSE = new RskAddress("13978AEE95F38490E9769C39B2773ED763D9CD5F");
    private final TestSystemProperties config = new TestSystemProperties();


    // This is only to easily identify codes while debugging the trie.
    static byte[] codePrefix = new byte[]{-1,-1,-1,-1};

    public byte[] randomCode(int maxSize) {
        int length = TestUtils.getRandom().nextInt(maxSize-codePrefix.length-1)+1;
        return TestUtils.concat(codePrefix,TestUtils.randomBytes(length));
    }

    byte[] oldTrie10_5=Hex.decode("00003513db97e8e9afff71727bce4b2ac01ff3b874e4814cbb31828829b730fd7eca00000000001e00000020efca24d11fd5d95b6494378857d8e5856e69a78cb7bf7922c881fbb85ded0f1b000000460203000000fdb14332af18a02002d98c7cd3162d3e9bf8b3661f50f4d69e83c184fcc02b5178662b1587771c16e780e7988169f42aa3d6121c06bb5966459068ace8a93f15d900000020a5a9f4b9699784b58dde3e02788d1b6d58ecfc228bfa79503663b7113004748e00000046020100030000efca24d11fd5d95b6494378857d8e5856e69a78cb7bf7922c881fbb85ded0f1b3cbbb09e97eb85ba23ec1822eea6e057f24e8d4f6d698c4fa671df3b574f380b00000020cacb5c364d3a0d4484b9f5799acd0280d284292b665855f4fa78d17630236dd000000053f8518423c29b62890e33fafbdd50580000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47000000020ea63ce51a897193cf2df6e91a9814fde7d812cc53a9b99dbfb8883099857b170000000460201000300006132777b0dcb4885220f39408aa27c32693b518e691b38188b187a25d6bdd398abbe1f7f9a1e84fe21b8587100c3b74a36e24233855f7f0f17e00ac1552bfd7e0000002056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b4210000000602010000000000000020ee9767b78a6ce5f0d753af61043266f3e5139910f0706da4d3210950b9f0695a00000053f851841e173a4c8908ac7230489e800000a011eb4d4c3a3be276820b4732166e964512357fc6c95344744621f3dce2140a4aa0ce76085c8aafb41c0ef1a91e297fa8f51dfd1738825a382077617aad9186897c000000204d98ab7395024fcee86793faec0fb1407b8a988774caee979e55594af248094700000046020100030000ca3f6737ecaa4957fc253a90b97a69f6cd85b35c3315b4d96150a22495604ee80faed22496bdf7ae9aa65ac1a81fed84f23fa2d8f3313a725ad494df659b8fbb0000002056232e967ce61fd9429822dc5ead2e878362903ce20fd7c1929abcd6ebd5170d00000046020100030000ea63ce51a897193cf2df6e91a9814fde7d812cc53a9b99dbfb8883099857b170a5a9f4b9699784b58dde3e02788d1b6d58ecfc228bfa79503663b7113004748e00000020abbe1f7f9a1e84fe21b8587100c3b74a36e24233855f7f0f17e00ac1552bfd7e000000460203000000fd75bcb031c9ac0e57cee2317aa7e86bd653931258f8439957c552dda4297c33505c81c3a5275e6bf8ab5bada94c6e2debac620dbe95fd221fe08236b9549c31b2000000206132777b0dcb4885220f39408aa27c32693b518e691b38188b187a25d6bdd398000000460203000000fd50d1a9c0ef89eb1eaf1332eff254b9a3acf5238c5b68641a88ed3d8aa3e47b40cacb5c364d3a0d4484b9f5799acd0280d284292b665855f4fa78d17630236dd0000000204df7fe53ef2166b4ba5f874aff489b40a57d737981804e13038f87b9d6715d14000000460203000000fc98c8a8cb46de7b1920ea33b91c9d7eb5bb0621cf5a9cc8443e9ed70f81ab7e20551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7953f4000000209c6d8278b9f2ad00ad97366bed668c68113ef3eb08d27d30ce6d12489c75c5e4000000460203000000fbe855b478adef5ad6a85f42b0c259f07361a5fa5e76d323141ff4b75ed85b98c0ee9767b78a6ce5f0d753af61043266f3e5139910f0706da4d3210950b9f0695a0000002090185a8f6e377afc0e92713ddeb385ba25ed63ad13f21c6a9c44919d952e372c00000046020100030000d6330ec57ade6c9f2d723985abc70790c752b5719e59ed7dc22d57c80f1f081aed8b70427b97a4b2aee2e98cc8f88d7d1b8cbc1ef4e22827585ccc6e7467b32600000020551812f907afbe9f896f497ba3664b54ffce94073bae5aa8a1c90810dc7953f400000053f85184e7f2bc408920a26da277a1280000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000200faed22496bdf7ae9aa65ac1a81fed84f23fa2d8f3313a725ad494df659b8fbb000000460201000300002628411db3608a02b233dfc11c22c118b4331eeb040d6a119b899a08eacc88182bea7e6337e2cfc8b2a5fd0468ba7b66442526e9ab81ae459211c0b00e04cdba0000002090f0aaabb52cf5345d6a248edb78970db4f8f4157334c969133636473d962a6b00000053f85184f1d00c2d8907c0860e5a80dc0000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000203cbbb09e97eb85ba23ec1822eea6e057f24e8d4f6d698c4fa671df3b574f380b00000047020100030001009c6d8278b9f2ad00ad97366bed668c68113ef3eb08d27d30ce6d12489c75c5e45a452145b2730e85950ee225954945f40b3350c999c7e4fbfc2742eac5ab3cbc00000020d6330ec57ade6c9f2d723985abc70790c752b5719e59ed7dc22d57c80f1f081a000000460203000000fb1b767eb1e51fab7620a9c0db80be314e384ff1e62502a1d3ff6f00e1d4735b603747803acb776e564ed21b3df0cc9b7b9a020081239cb5eaf9fbbc09fe158339000000202adde0f4259adf28ad6319a20c04208de869d01fc80a88fac327ef8dd6b396b400000053f851841feff205892fd03576f6b6880000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47000000020afd73bf33a6607a7ee747603b26f016bf4c1e7a0b3e500cc2ffc10a8974efb2300000053f851840102a0fd8914542ba12a337c0000a07a6bf06e8e84f90933b79bf84ff45242eb04c9677f18cb0704836ad9467e0f96a02328404cb3d202860b72aac53ab103d38c365b60331824d80d164f6921e27a32000000205a452145b2730e85950ee225954945f40b3350c999c7e4fbfc2742eac5ab3cbc000000460203000000fbb9f067deb59063bb101cc49e1570b7bb8ad19e97cb0fcf831a150aeda4f892202adde0f4259adf28ad6319a20c04208de869d01fc80a88fac327ef8dd6b396b4000000205c81c3a5275e6bf8ab5bada94c6e2debac620dbe95fd221fe08236b9549c31b200000053f85184705801718918650127cc3dc80000a0d575306f01546673a74fceec78818c646a408915ed93f0731aaa8fa3283de615a08be9da6943e5bf840b8ffaaa5f88203ca01a5c609c2f9c6e427258f9f274eefe000000202bea7e6337e2cfc8b2a5fd0468ba7b66442526e9ab81ae459211c0b00e04cdba000000460203000000fd6c47ec9caf640a114e6c7c7e0e98f83f05f3cd8d9497d8f7bea56ea64dd507587aa9422af22e8c3d77b7aa096944a729a8532464631bdcb729021bdf4351937100000020ca3f6737ecaa4957fc253a90b97a69f6cd85b35c3315b4d96150a22495604ee8000000470201000300010090185a8f6e377afc0e92713ddeb385ba25ed63ad13f21c6a9c44919d952e372c4df7fe53ef2166b4ba5f874aff489b40a57d737981804e13038f87b9d6715d1400000020ed8b70427b97a4b2aee2e98cc8f88d7d1b8cbc1ef4e22827585ccc6e7467b326000000460203000000fb4b0ac9a996daf435c502bffa00cb111e7cd57348ee9c72b323148f37edcb6400afd73bf33a6607a7ee747603b26f016bf4c1e7a0b3e500cc2ffc10a8974efb23000000207aa9422af22e8c3d77b7aa096944a729a8532464631bdcb729021bdf4351937100000053f85184c47fa07489221920e76a48b40000a05aebe7d8638c97d07675c9788d96105320f67b48d81ce34b43c31a8e0f60a2d9a0395139c01d04c4b144535c688055fa4baab703373af103800733b60b3d857f61000000203513db97e8e9afff71727bce4b2ac01ff3b874e4814cbb31828829b730fd7eca0000004602010003000056232e967ce61fd9429822dc5ead2e878362903ce20fd7c1929abcd6ebd5170d4d98ab7395024fcee86793faec0fb1407b8a988774caee979e55594af248094700000020662b1587771c16e780e7988169f42aa3d6121c06bb5966459068ace8a93f15d900000053f851843af01d4f891a9dfe6a920ccc0000a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470000000203747803acb776e564ed21b3df0cc9b7b9a020081239cb5eaf9fbbc09fe15833900000053f85184ca02da2a8912f939c99edab80000a0db06ef684dcdf23ad7c5d1c1afdcf642d087e4c9643e52365589efe8d3bf0c62a0560ebca6bb0dc143e6ba187c62c764d01345f4008828066b71c0f10c49d08a4a000000202628411db3608a02b233dfc11c22c118b4331eeb040d6a119b899a08eacc8818000000460203000000fd1471e9e8a8ced25fd7fe0dacc8484996ded2a96d8890ce96168b2387174120c090f0aaabb52cf5345d6a248edb78970db4f8f4157334c969133636473d962a6b");
    // oldTrie10_5 root: 3513db97e8e9afff71727bce4b2ac01ff3b874e4814cbb31828829b730fd7eca

    @Test
    public void test1Simple() {
        TrieStoreImpl astore = new TrieStoreImpl(new HashMapDB());
        Repository repository = createRepository(astore);

        Repository track = repository.startTracking();

        TestUtils.getRandom().setSeed(0);
        int maxAccounts = 10;
        int maxStorageRows = 5;
        for(int i=0;i<maxAccounts ;i++) {
            // Create random accounts/contracts
            RskAddress addr = TestUtils.randomAddress();
            track.createAccount(addr);
            // Set some random balance
            AccountState a = track.getAccountState(addr);
            a.setNonce(TestUtils.randomBigInteger(4));
            // Balance between 1 and 100 SBTC
            a.addToBalance(TestUtils.randomCoin(18, 1000));
            track.updateAccountState(addr,a);
            if (i>=maxAccounts/2) {
                // half of them are contracts
                track.setupContract(addr);
                for (int s = 0; s < maxStorageRows; s++) {
                    track.addStorageBytes(addr, TestUtils.randomDataWord(), TestUtils.randomBytes(TestUtils.getRandom().nextInt(40) + 1));
                }
                track.saveCode(addr,randomCode(60));
            }
        }

        track.commit();
        TrieConverter tc = new TrieConverter();
        byte[] oldRoot = tc.getOrchidAccountTrieRoot((TrieImpl) repository.getMutableTrie().getTrie());
        Trie atrie = TrieImpl.deserialize(oldTrie10_5);

        Assert.assertThat(Hex.toHexString(oldRoot), is(atrie.getHash().toHexString()));
    }

    private static Repository createRepository(TrieStore store) {
        return new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));
    }

    @Test
    public void test() {
        final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        final BigInteger accountNonce = BigInteger.valueOf(9);

        Repository repository = new MutableRepository(new TrieImpl(new TrieStoreImpl(new HashMapDB()), true));
        AccountState accountState = repository.createAccount(COW);
        accountState.setNonce(accountNonce);
        repository.updateAccountState(COW, accountState);
        repository.commit();

        Assert.assertThat(repository.getAccountState(COW).getNonce(), is(accountNonce));

        TrieConverter converter = new TrieConverter();
        byte[] oldRoot = converter.getOrchidAccountTrieRoot((TrieImpl) repository.getMutableTrie().getTrie());
        // expected ab158b4a1d2411492194768fbd2669c069b60e5d0bcc859e51fe477855829ae7
        System.out.println(Hex.toHexString(oldRoot));
    }

}
