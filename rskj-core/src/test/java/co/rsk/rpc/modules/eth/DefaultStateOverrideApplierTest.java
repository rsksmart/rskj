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
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.ethereum.TestUtils.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultStateOverrideApplierTest {

    private static final BigInteger DEFAULT_BALANCE = BigInteger.valueOf(1000);
    private static final byte[] DEFAULT_CODE = TestUtils.generateBytes(1,10);
    private static final BigInteger DEFAULT_NONCE = BigInteger.valueOf(5);
    private static final DataWord DEFAULT_STORAGE_KEY_ONE = DataWord.valueOf(1);
    private static final DataWord DEFAULT_STORAGE_KEY_TWO = DataWord.valueOf(2);
    private static final DataWord DEFAULT_STORAGE_VALUE_ONE = DataWord.valueOf(100);
    private static final DataWord DEFAULT_STORAGE_VALUE_TWO = DataWord.valueOf(200);

    private MutableRepository repository;
    private RskAddress address;
    private final DefaultStateOverrideApplier stateOverrideApplier = new DefaultStateOverrideApplier(2, 1);

    @BeforeEach
    void setup() {
        address = TestUtils.generateAddress("address");

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        IReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();
        repository = new MutableRepository(mutableTrie, tracker);

        populateRepository(repository, address);
    }

    private void populateRepository(Repository repository, RskAddress address) {
        repository.addBalance(address, new Coin(DEFAULT_BALANCE));
        repository.setNonce(address, DEFAULT_NONCE);
        repository.saveCode(address, DEFAULT_CODE);
        repository.addStorageRow(address, DEFAULT_STORAGE_KEY_ONE, DEFAULT_STORAGE_VALUE_ONE);
        repository.addStorageRow(address, DEFAULT_STORAGE_KEY_TWO, DEFAULT_STORAGE_VALUE_TWO);
    }

    @Test
    void applyToRepository_addressIsNull_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(null);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Address cannot be null", exception.getMessage());
    }

    @Test
    void applyToRepository_applyStateAndStateDiffTogether_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));

        accountOverride.setStateDiff(state);
        accountOverride.setState(state);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("AccountOverride.stateDiff and AccountOverride.state cannot be set at the same time", exception.getMessage());
    }

    @Test
    void applyToRepository_balanceLessThanZero_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        BigInteger balance = BigInteger.valueOf(-1L);
        accountOverride.setBalance(balance);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Balance must be equal or bigger than zero", exception.getMessage());
    }

    @Test
    void applyToRepository_validBalance_executesAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        BigInteger balance = BigInteger.TEN;
        accountOverride.setBalance(balance);

        // When
        stateOverrideApplier.applyToRepository(repository, accountOverride);

        // Then
        assertEquals(accountOverride.getBalance(), repository.getBalance(address).asBigInteger());
    }

    @Test
    void applyToRepository_nonceLessThanZero_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Long nonce = -1L;
        accountOverride.setNonce(nonce);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Nonce must be equal or bigger than zero", exception.getMessage());
    }

    @Test
    void applyToRepository_validNonce_executesAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        long nonce = 7L;
        accountOverride.setNonce(nonce);

        // When
        stateOverrideApplier.applyToRepository(repository, accountOverride);

        // Then
        assertEquals(accountOverride.getNonce(), repository.getNonce(address).longValue());
    }

    @Test
    void applyToRepository_codeSizeBiggerThanMax_throwsExceptionAsExpected() {
        // Given
        int maxOverridableCodeSize = 1;
        StateOverrideApplier stateOverrideApplier1 = new DefaultStateOverrideApplier(1, 0);

        AccountOverride accountOverride = new AccountOverride(address);

        byte[] code = "01".getBytes(); // Two Bytes
        accountOverride.setCode(code);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier1.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Code length in bytes exceeded. Max " + maxOverridableCodeSize, exception.getMessage());
    }

    @Test
    void applyToRepository_validCode_executesAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        byte[] code = new byte[]{0x1, 0x2};
        accountOverride.setCode(code);

        // When
        stateOverrideApplier.applyToRepository(repository, accountOverride);

        // Then
        assertArrayEquals(accountOverride.getCode(), repository.getCode(address));
    }

    @Test
    void applyToRepository_stateSizeBiggerThanMax_throwsExceptionAsExpected() {
        // Given
        int maxStateOverrideChanges = 1;
        StateOverrideApplier stateOverrideApplier1 = new DefaultStateOverrideApplier(0, maxStateOverrideChanges);

        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DataWord.valueOf(0), DataWord.ZERO);
        state.put(DataWord.valueOf(1), DataWord.ONE);

        accountOverride.setState(state);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier1.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Number of state changes exceeded. Max " + maxStateOverrideChanges, exception.getMessage());
    }

    @Test
    void applyToRepository_validState_MustResetOtherValues() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setState(state);

        // Then
        assertNotNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(repository, accountOverride);
        assertEquals(accountOverride.getState().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void applyToRepository_validStateDiff_DoNotAlterOtherValues() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> stateDiff = new HashMap<>();
        stateDiff.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(stateDiff);

        // When
        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(repository, accountOverride);

        // Then
        assertEquals(accountOverride.getStateDiff().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertEquals(DEFAULT_STORAGE_VALUE_TWO, repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void fromAccountOverrideParam_setMovePrecompileToAddress_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        RskAddress movePrecompileToAddress = TestUtils.generateAddress("aPrecompiledAddress");
        accountOverride.setMovePrecompileToAddress(movePrecompileToAddress);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            stateOverrideApplier.applyToRepository(repository, accountOverride);
        });

        // Then
        assertEquals(-32201, exception.getCode());
        assertEquals("Move precompile to address is not supported yet", exception.getMessage());
    }

}
