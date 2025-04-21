package co.rsk.rpc.modules.eth.subscribe;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.core.bc.ReadWrittenKeysTracker;
import co.rsk.db.MutableTrieImpl;
import co.rsk.rpc.modules.eth.AccountOverride;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountOverrideTest {
    private static final BigInteger DEFAULT_BALANCE = BigInteger.valueOf(1000);
    private static final byte[] DEFAULT_CODE = TestUtils.generateBytes(1,10);
    private static final BigInteger DEFAULT_NONCE = BigInteger.valueOf(5);
    private static final DataWord DEFAULT_STORAGE_KEY_ONE = DataWord.valueOf(1);
    private static final DataWord DEFAULT_STORAGE_KEY_TWO = DataWord.valueOf(2);
    private static final DataWord DEFAULT_STORAGE_VALUE_ONE = DataWord.valueOf(100);
    private static final DataWord DEFAULT_STORAGE_VALUE_TWO = DataWord.valueOf(200);

    private AccountOverride accountOverride;
    private MutableRepository repository;
    private RskAddress address;

    @BeforeEach
    void setup() {
        address = TestUtils.generateAddress("address");

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        IReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();
        repository = new MutableRepository(mutableTrie, tracker);

        populateRepository(repository, address);

        accountOverride = new AccountOverride();
        accountOverride.setAddress(address);
    }

    private void populateRepository(Repository repository, RskAddress address) {
        repository.addBalance(address, new Coin(DEFAULT_BALANCE));
        repository.setNonce(address, DEFAULT_NONCE);
        repository.saveCode(address, DEFAULT_CODE);
        repository.addStorageRow(address, DEFAULT_STORAGE_KEY_ONE, DEFAULT_STORAGE_VALUE_ONE);
        repository.addStorageRow(address, DEFAULT_STORAGE_KEY_TWO, DEFAULT_STORAGE_VALUE_TWO);
    }

    @Test
    void applyWithBalance() {
        BigInteger balance = BigInteger.TEN;
        accountOverride.setBalance(balance);
        accountOverride.applyToRepository(repository);
        assertEquals(accountOverride.getBalance(), repository.getBalance(address).asBigInteger());
    }

    @Test
    void applyWithNonce() {
        long nonce = 7L;
        accountOverride.setNonce(nonce);
        accountOverride.applyToRepository(repository);
        assertEquals(accountOverride.getNonce(), repository.getNonce(address).longValue());
    }

    @Test
    void applyWithCode() {
        byte[] code = new byte[]{0x1, 0x2};
        accountOverride.setCode(code);
        accountOverride.applyToRepository(repository);
        assertArrayEquals(accountOverride.getCode(), repository.getCode(address));
    }

    @Test
    void applyWithStateMustResetOtherValues() {
        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setState(state);

        assertNotNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

        // Add an existing key to ensure it is cleared
        accountOverride.applyToRepository(repository);

        assertEquals(accountOverride.getState().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

    }

    @Test
    void applyWithStateDiffDoNotAlterOtherValues() {

        Map<DataWord, DataWord> stateDiff = new HashMap<>();
        stateDiff.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(stateDiff);

        // Add an existing key to ensure it is cleared
        accountOverride.applyToRepository(repository);

        assertEquals(accountOverride.getStateDiff().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertEquals(DEFAULT_STORAGE_VALUE_TWO, repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void applyWithoutAddressThrowsException() {
        accountOverride.setAddress(null);
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            accountOverride.applyToRepository(repository);
        });
        assertTrue(exception.getMessage().contains("AccountOverride.address must be set"));
    }

    @Test
    void applyStateAndStateDiffThrowsException(){
        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(state);
        accountOverride.setState(state);
        assertThrows(IllegalStateException.class, () -> {
            accountOverride.applyToRepository(repository);
        });
    }

    @Test
    void testEqualsAndHashCode() {
        AccountOverride other = new AccountOverride();
        other.setBalance(BigInteger.TEN);
        other.setNonce(1L);
        other.setCode(new byte[]{1});
        other.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        other.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));
        other.setAddress(address);

        accountOverride.setBalance(BigInteger.TEN);
        accountOverride.setNonce(1L);
        accountOverride.setCode(new byte[]{1});
        accountOverride.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        accountOverride.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));
        accountOverride.setAddress(address);

        assertEquals(accountOverride, other);
        assertEquals(accountOverride.hashCode(), other.hashCode());
    }
}
