/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.core.bc.ReadWrittenKeysTracker;
import co.rsk.db.MutableTrieImpl;
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

class DefaultStateOverrideApplierTest {

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
    private DefaultStateOverrideApplier stateOverrideApplier = new DefaultStateOverrideApplier();
    @BeforeEach
    void setup() {
        address = TestUtils.generateAddress("address");

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        IReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();
        repository = new MutableRepository(mutableTrie, tracker);

        populateRepository(repository, address);

        accountOverride = new AccountOverride(address);
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
        stateOverrideApplier.applyToRepository(repository,accountOverride);
        assertEquals(accountOverride.getBalance(), repository.getBalance(address).asBigInteger());
    }

    @Test
    void applyWithNonce() {
        long nonce = 7L;
        accountOverride.setNonce(nonce);
        stateOverrideApplier.applyToRepository(repository,accountOverride);
        assertEquals(accountOverride.getNonce(), repository.getNonce(address).longValue());
    }

    @Test
    void applyWithCode() {
        byte[] code = new byte[]{0x1, 0x2};
        accountOverride.setCode(code);
        stateOverrideApplier.applyToRepository(repository,accountOverride);
        assertArrayEquals(accountOverride.getCode(), repository.getCode(address));
    }

    @Test
    void applyWithStateMustResetOtherValues() {
        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setState(state);

        assertNotNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(repository,accountOverride);

        assertEquals(accountOverride.getState().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

    }

    @Test
    void applyWithStateDiffDoNotAlterOtherValues() {

        Map<DataWord, DataWord> stateDiff = new HashMap<>();
        stateDiff.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(stateDiff);

        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(repository,accountOverride);

        assertEquals(accountOverride.getStateDiff().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertEquals(DEFAULT_STORAGE_VALUE_TWO, repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void applyStateAndStateDiffThrowsException(){
        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(state);
        accountOverride.setState(state);
        assertThrows(IllegalStateException.class, () -> {
            stateOverrideApplier.applyToRepository(repository,accountOverride);
        });
    }

    @Test
    void testEqualsAndHashCode() {
        AccountOverride other = new AccountOverride(address);
        other.setBalance(BigInteger.TEN);
        other.setNonce(1L);
        other.setCode(new byte[]{1});
        other.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        other.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));

        accountOverride.setBalance(BigInteger.TEN);
        accountOverride.setNonce(1L);
        accountOverride.setCode(new byte[]{1});
        accountOverride.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        accountOverride.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));

        assertEquals(accountOverride, other);
        assertEquals(accountOverride.hashCode(), other.hashCode());
    }
}