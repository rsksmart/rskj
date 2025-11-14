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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.core.bc.ReadWrittenKeysTracker;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OverrideablePrecompiledContracts;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultStateOverrideApplierTest {

    private final TestSystemProperties testSystemProperties = new TestSystemProperties();

    private static final BigInteger DEFAULT_BALANCE = BigInteger.valueOf(1000);
    private static final byte[] DEFAULT_CODE = TestUtils.generateBytes(1, 10);
    private static final BigInteger DEFAULT_NONCE = BigInteger.valueOf(5);
    private static final DataWord DEFAULT_STORAGE_KEY_ONE = DataWord.valueOf(1);
    private static final DataWord DEFAULT_STORAGE_KEY_TWO = DataWord.valueOf(2);
    private static final DataWord DEFAULT_STORAGE_VALUE_ONE = DataWord.valueOf(100);
    private static final DataWord DEFAULT_STORAGE_VALUE_TWO = DataWord.valueOf(200);

    private MutableRepository repository;
    private RskAddress address;
    private DefaultStateOverrideApplier stateOverrideApplier;

    @BeforeEach
    void setup() {
        stateOverrideApplier = new DefaultStateOverrideApplier(testSystemProperties.getActivationConfig());
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
    void applyWithBalance() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        BigInteger balance = BigInteger.TEN;
        accountOverride.setBalance(balance);

        // When
        stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);

        // Then
        assertEquals(accountOverride.getBalance(), repository.getBalance(address).asBigInteger());
    }

    @Test
    void applyWithNonce() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        long nonce = 7L;
        accountOverride.setNonce(nonce);

        // When
        stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);

        // Then
        assertEquals(accountOverride.getNonce(), repository.getNonce(address).longValue());
    }

    @Test
    void applyWithCode() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        byte[] code = new byte[]{0x1, 0x2};
        accountOverride.setCode(code);

        // When
        stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);

        // Then
        assertArrayEquals(accountOverride.getCode(), repository.getCode(address));
    }

    @Test
    void applyWithStateMustResetOtherValues() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setState(state);

        // Then
        assertNotNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));

        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);
        assertEquals(accountOverride.getState().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertNull(repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void applyWithStateDiffDoNotAlterOtherValues() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> stateDiff = new HashMap<>();
        stateDiff.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(stateDiff);

        // When
        // Add an existing key to ensure it is cleared
        stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);

        // Then
        assertEquals(accountOverride.getStateDiff().get(DEFAULT_STORAGE_KEY_ONE), repository.getStorageValue(address, DEFAULT_STORAGE_KEY_ONE));
        assertEquals(DEFAULT_STORAGE_VALUE_TWO, repository.getStorageValue(address, DEFAULT_STORAGE_KEY_TWO));
    }

    @Test
    void applyStateAndStateDiffThrowsException() {
        // Given
        AccountOverride accountOverride = new AccountOverride(address);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DEFAULT_STORAGE_KEY_ONE, DataWord.valueOf(10));
        accountOverride.setStateDiff(state);
        accountOverride.setState(state);

        // Then
        assertThrows(IllegalStateException.class, () -> {
            stateOverrideApplier.applyToRepository(mock(Block.class), repository, accountOverride, null);
        });
    }

    @Test
    void applyWithMovePrecompileTo_happyPath() {
        // Given
        RskAddress precompileAddress = new RskAddress("0x0000000000000000000000000000000000000004");
        RskAddress movePrecompileTo = new RskAddress("0x0000000000000000000000000000000000000001");

        DataWord precompileAddressInDataWord = DataWord.valueFromHex(precompileAddress.toHexString());
        DataWord movePrecompileToInDataWord = DataWord.valueFromHex(movePrecompileTo.toHexString());

        AccountOverride accountOverride = new AccountOverride(precompileAddress);
        accountOverride.setMovePrecompileToAddress(movePrecompileTo);

        PrecompiledContracts precompiledContracts = getPrecompiledContracts();

        OverrideablePrecompiledContracts overrideablePrecompiledContracts = new OverrideablePrecompiledContracts(precompiledContracts);

        long blockNumber = 1L;
        Block blockMock = mock(Block.class);
        when(blockMock.getNumber()).thenReturn(blockNumber);

        // When
        stateOverrideApplier.applyToRepository(blockMock, repository, accountOverride, overrideablePrecompiledContracts);

        // Then
        assertEquals(overrideablePrecompiledContracts.getContractForAddress(testSystemProperties.getActivationConfig().forBlock(blockNumber), precompileAddressInDataWord), overrideablePrecompiledContracts.getContractForAddress(testSystemProperties.getActivationConfig().forBlock(blockNumber), movePrecompileToInDataWord));
    }

    @Test
    void applyWithMovePrecompileTo_alreadyOverriddenContract_throwsExceptionAsExpected() {
        // Given
        RskAddress precompileAddress = new RskAddress("0x0000000000000000000000000000000000000004");
        RskAddress movePrecompileTo = new RskAddress("0x0000000000000000000000000000000000000001");

        AccountOverride accountOverride = new AccountOverride(precompileAddress);
        accountOverride.setMovePrecompileToAddress(movePrecompileTo);

        PrecompiledContracts precompiledContracts = getPrecompiledContracts();

        OverrideablePrecompiledContracts overrideablePrecompiledContracts = new OverrideablePrecompiledContracts(precompiledContracts);

        long blockNumber = 1L;
        Block blockMock = mock(Block.class);
        when(blockMock.getNumber()).thenReturn(blockNumber);

        // When
        stateOverrideApplier.applyToRepository(blockMock, repository, accountOverride, overrideablePrecompiledContracts);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            // This is the second time calling the exact same method so the contract should be already overridden
            stateOverrideApplier.applyToRepository(blockMock, repository, accountOverride, overrideablePrecompiledContracts);
        });

        // Then
        assertEquals("Account " + precompileAddress.toHexString() + " has already been overridden by a precompile", exception.getMessage());
    }

    private PrecompiledContracts getPrecompiledContracts() {
        TestSystemProperties config = new TestSystemProperties();
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                null, null, null, signatureCache);
        return new PrecompiledContracts(config, bridgeSupportFactory, signatureCache);
    }

}
