package co.rsk.db;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.DataWord;
import org.junit.Test;

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

    @Test
    public void test1Simple() {
        TrieStoreImpl astore = new TrieStoreImpl(new HashMapDB());
        Repository repository = createRepositoryImplWithStore(config,astore);
        System.out.println(Hex.toHexString(repository.getRoot()));
        Repository track = repository.startTracking();

        TestUtils.getRandom().setSeed(0);
        int maxAccounts = 1000;
        int maxStorageRows = 50;
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
            if (i>maxAccounts/2) {
                // half of them are contracts
                for (int s = 0; s < maxStorageRows; s++) {
                    track.addStorageBytes(addr, TestUtils.randomDataWord(), TestUtils.randomBytes(TestUtils.getRandom().nextInt(40) + 1));
                }
                track.saveCode(addr,randomCode(60));
            }
        }

        track.commit();
        System.out.println(Hex.toHexString(repository.getRoot()));
    }

    public static RepositoryImpl createRepositoryImplWithStore(RskSystemProperties config,TrieStore store) {
        return new RepositoryImpl(null, config.detailsInMemoryStorageLimit(), config.databaseDir());

    }
}
