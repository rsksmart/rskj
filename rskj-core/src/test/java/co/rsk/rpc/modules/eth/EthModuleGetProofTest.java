/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.rpc.dto.ProofResultDTO;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for EthModule.getProof method.
 */
class EthModuleGetProofTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    private EthModule ethModule;
    private Blockchain blockchain;
    private RepositoryLocator repositoryLocator;
    private RskAddress testAddress;

    @BeforeEach
    void setUp() {
        blockchain = mock(Blockchain.class);
        repositoryLocator = mock(RepositoryLocator.class);
        testAddress = TestUtils.generateAddress("testAccount");

        // Create a real trie-backed repository for testing
        Trie trie = new Trie();
        MutableRepository repository = new MutableRepository(new TrieStoreImpl(new HashMapDB()), trie);

        // Create account with some balance
        repository.createAccount(testAddress);
        repository.addBalance(testAddress, Coin.valueOf(1000000L));

        // Mock blockchain to return a block
        Block mockBlock = mock(Block.class);
        BlockHeader mockHeader = mock(BlockHeader.class);
        when(mockBlock.getHeader()).thenReturn(mockHeader);
        when(blockchain.getBestBlock()).thenReturn(mockBlock);
        when(blockchain.getBlockByNumber(anyLong())).thenReturn(mockBlock);

        // Mock repository locator
        when(repositoryLocator.snapshotAt(any(BlockHeader.class))).thenReturn(repository);

        ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(),
                config.getNetworkConstants().getChainId(),
                blockchain,
                null, // transactionPool
                null, // reversibleTransactionExecutor
                null, // executionBlockRetriever
                repositoryLocator,
                null, // ethModuleWallet
                null, // ethModuleTransaction
                new BridgeSupportFactory(null, config.getNetworkConstants().getBridgeConstants(), 
                        config.getActivationConfig(), signatureCache),
                config.getGasEstimationCap(),
                config.getCallGasCap(),
                config.getActivationConfig(),
                null, // precompiledContracts
                false, // allowCallStateOverride
                null  // stateOverrideApplier
        );
    }

    @Test
    void testGetProofBasic() {
        ProofResultDTO result = ethModule.getProof(
                testAddress,
                Collections.emptyList(),
                "latest",
                false
        );

        assertNotNull(result);
        assertEquals(testAddress.toJsonString(), result.getAddress());
        assertNotNull(result.getBalance());
        assertNotNull(result.getNonce());
        assertNotNull(result.getCodeHash());
        assertNotNull(result.getStorageHash());
        assertNotNull(result.getAccountProof());
        assertNotNull(result.getStorageProof());
        assertEquals(0, result.getStorageProof().length);
    }

    @Test
    void testGetProofWithStorageKey() {
        DataWord storageKey = DataWord.valueOf(0);

        ProofResultDTO result = ethModule.getProof(
                testAddress,
                List.of(storageKey),
                "latest",
                false
        );

        assertNotNull(result);
        assertEquals(1, result.getStorageProof().length);
        assertNotNull(result.getStorageProof()[0].getKey());
        assertNotNull(result.getStorageProof()[0].getValue());
        assertNotNull(result.getStorageProof()[0].getProof());
    }

    @Test
    void testGetProofForNonExistentAccount() {
        RskAddress nonExistentAddress = TestUtils.generateAddress("nonExistent");

        ProofResultDTO result = ethModule.getProof(
                nonExistentAddress,
                Collections.emptyList(),
                "latest",
                false
        );

        assertNotNull(result);
        assertEquals(nonExistentAddress.toJsonString(), result.getAddress());
        // Balance should be 0x0 for non-existent account
        assertEquals("0x0", result.getBalance());
        // Nonce should be 0x0
        assertEquals("0x0", result.getNonce());
    }

    @Test
    void testGetProofBalanceFormatting() {
        ProofResultDTO result = ethModule.getProof(
                testAddress,
                Collections.emptyList(),
                "latest",
                false
        );

        assertNotNull(result);
        // Balance should be hex formatted
        assertTrue(result.getBalance().startsWith("0x"));
        // The balance we set was 1000000L = 0xf4240
        assertEquals("0xf4240", result.getBalance());
    }

    @Test
    void testGetProofAtEarliestBlock() {
        ProofResultDTO result = ethModule.getProof(
                testAddress,
                Collections.emptyList(),
                "earliest",
                false
        );

        assertNotNull(result);
    }

    @Test
    void testGetProofWithRlpEncoding() {
        ProofResultDTO resultWithRlp = ethModule.getProof(
                testAddress,
                Collections.emptyList(),
                "latest",
                true  // use RLP encoding
        );

        ProofResultDTO resultWithoutRlp = ethModule.getProof(
                testAddress,
                Collections.emptyList(),
                "latest",
                false  // native format
        );

        assertNotNull(resultWithRlp);
        assertNotNull(resultWithoutRlp);

        // Both should have the same non-proof fields
        assertEquals(resultWithRlp.getAddress(), resultWithoutRlp.getAddress());
        assertEquals(resultWithRlp.getBalance(), resultWithoutRlp.getBalance());
        assertEquals(resultWithRlp.getNonce(), resultWithoutRlp.getNonce());
        assertEquals(resultWithRlp.getCodeHash(), resultWithoutRlp.getCodeHash());
    }

    @Test
    void testGetProofMultipleStorageKeys() {
        List<DataWord> storageKeys = List.of(
                DataWord.valueOf(0),
                DataWord.valueOf(1),
                DataWord.valueOf(2)
        );

        ProofResultDTO result = ethModule.getProof(
                testAddress,
                storageKeys,
                "latest",
                false
        );

        assertNotNull(result);
        assertEquals(3, result.getStorageProof().length);
    }
}
