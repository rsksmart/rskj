package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.TmpTrieStoreFactory;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
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
    @Test
    public void test() {
        final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        final BigInteger accountNonce = BigInteger.valueOf(9);

        TrieStore trieStore = TmpTrieStoreFactory.newInstance();
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        AccountState accountState = repository.createAccount(COW);
        accountState.setNonce(accountNonce);
        repository.updateAccountState(COW, accountState);
        repository.commit();

        Assert.assertThat(repository.getAccountState(COW).getNonce(), is(accountNonce));
    }
}
