package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
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

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new TopRepository(new Trie(trieStore), trieStore);
        AccountState accountState = repository.createAccount(COW);
        accountState.setNonce(accountNonce);
        repository.updateAccountState(COW, accountState);
        repository.commit();

        Assert.assertThat(repository.getAccountState(COW).getNonce(), is(accountNonce));

        TrieConverter converter = new TrieConverter();
        byte[] oldRoot = converter.getOrchidAccountTrieRoot(repository.getTrie());
        // expected ab158b4a1d2411492194768fbd2669c069b60e5d0bcc859e51fe477855829ae7
        System.out.println(Hex.toHexString(oldRoot));
    }

}
