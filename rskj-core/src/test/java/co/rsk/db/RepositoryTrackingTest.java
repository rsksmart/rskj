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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.math.BigInteger;


public class RepositoryTrackingTest {
    public static final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");

    private MutableRepository repository;
    private MutableTrieImpl mutableTrie;
    private TrieStore trieStore;
    private IReadWrittenKeysTracker tracker;

    @BeforeEach
    public void setUp() {
        trieStore = new TrieStoreImpl(new HashMapDB());
        mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        tracker = new ReadWrittenKeysTracker();
        repository = new MutableRepository(mutableTrie, tracker);
    }

    void assertRepositoryHasSize(int readRepoSize, int writtenRepoSize) {
        Assertions.assertEquals(readRepoSize, tracker.getTemporalReadKeys().size());
        Assertions.assertEquals(writtenRepoSize, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    void tracksWriteInCreatedAccount() {
        repository.createAccount(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    void tracksWriteInSetupContract() {
        repository.createAccount(COW);
        tracker.clear();

        repository.setupContract(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    void tracksReadInIsExists() {
        repository.isExist(COW);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    void tracksReadInGetAccountState() {
        repository.getAccountState(COW);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    void tracksWriteInDelete() {
        repository.createAccount(COW);
        tracker.clear();

        repository.delete(COW);

        assertRepositoryHasSize(0, 1);
    }

    @Test
    void tracksWriteInAddBalance() {
        repository.createAccount(COW);
        tracker.clear();

        repository.addBalance(COW, new Coin(BigInteger.ONE));

        assertRepositoryHasSize(1, 1);
    }

    @Test
    void doesntTrackWriteInAddBalanceZero() {
        repository.createAccount(COW);
        tracker.clear();

        repository.addBalance(COW, Coin.ZERO);

        assertRepositoryHasSize(1, 0);
    }

    @Test
    void tracksReadOnGetStorageBytes() {
        repository.createAccount(COW);
        tracker.clear();

        byte[] cowKey = Hex.decode("A1A2A3");

        repository.getStorageBytes(COW, DataWord.valueOf(cowKey));

        assertRepositoryHasSize(1, 0);
    }

    @Test
    void tracksWriteOnAddStorageBytes() {
        repository.createAccount(COW);
        tracker.clear();

        byte[] cowValue = Hex.decode("A4A5A6");
        byte[] cowKey = Hex.decode("A1A2A3");

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        assertRepositoryHasSize(1, 1);
    }

    @Test
    void tracksWriteOnAddStorageDifferentBytes() {
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
    void doesntTrackWriteOnAddStorageSameBytes() {
        repository.createAccount(COW);

        byte[] cowValue = Hex.decode("A4A5A6");
        byte[] cowKey = Hex.decode("A1A2A3");

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        tracker.clear();

        repository.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);

        assertRepositoryHasSize(1, 0);
    }
}
