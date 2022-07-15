package co.rsk.db;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.core.bc.ReadWrittenKeysTracker;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class RepositoryTrackingTest {
    public static final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");

    private MutableRepository repository;
    private MutableTrieImpl mutableTrie;
    private TrieStore trieStore;
    private IReadWrittenKeysTracker tracker;

    @Before
    public void setUp() {
        trieStore = new TrieStoreImpl(new HashMapDB());
        mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        tracker = new ReadWrittenKeysTracker();
        repository = new MutableRepository(mutableTrie, tracker);
    }

    void assertRepositoryHasSize(int readRepoSize, int writtenRepoSize) {
        assertEquals(readRepoSize, tracker.getTemporalReadKeys().size());
        assertEquals(writtenRepoSize, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void tracksWriteInCreatedAccount () {
        repository.createAccount(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    public void tracksWriteInSetupContract () {
        repository.createAccount(COW);
        tracker.clear();

        repository.setupContract(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    public void tracksReadInIsExists () {
        repository.isExist(COW);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    public void tracksReadInGetAccountState () {
        repository.getAccountState(COW);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    public void tracksWriteInDelete () {
        repository.createAccount(COW);
        tracker.clear();

        repository.delete(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    public void tracksWriteInAddBalance () {
        repository.createAccount(COW);
        tracker.clear();

        repository.addBalance(COW, new Coin(BigInteger.ONE));

        assertRepositoryHasSize(1, 1);
    }

    @Test
    public void doesntTrackWriteInAddBalanceZero () {
        repository.createAccount(COW);
        tracker.clear();

        repository.addBalance(COW, Coin.ZERO);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    public void tracksReadOnGetStorageBytes () {
        repository.createAccount(COW);
        tracker.clear();

        byte[] cowKey = Hex.decode("A1A2A3");

        repository.getStorageBytes(COW, DataWord.valueOf(cowKey));

        assertRepositoryHasSize(1, 0);
    }

    @Test
    public void tracksWriteOnAddStorageBytes () {
        repository.createAccount(COW);
        tracker.clear();

        byte[] cowValue = Hex.decode("A4A5A6");
        byte[] cowKey = Hex.decode("A1A2A3");

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        assertRepositoryHasSize(1, 1);
    }

    @Test
    public void tracksWriteOnAddStorageDifferentBytes () {
        repository.createAccount(COW);
        tracker.clear();

        byte[] cowValue = Hex.decode("A4A5A6");
        byte[] cowKey = Hex.decode("A1A2A3");
        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);
        repository.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);

        assertRepositoryHasSize(1, 2);
    }

    @Test
    public void doesntTrackWriteOnAddStorageSameBytes () {
        repository.createAccount(COW);

        byte[] cowValue = Hex.decode("A4A5A6");
        byte[] cowKey = Hex.decode("A1A2A3");

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        tracker.clear();

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        assertRepositoryHasSize(1, 0);
    }
}
