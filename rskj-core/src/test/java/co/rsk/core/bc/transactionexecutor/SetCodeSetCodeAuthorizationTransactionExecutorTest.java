/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.IReadWrittenKeysTracker;
import co.rsk.core.bc.ParallelizeTransactionHandler;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static org.ethereum.config.Constants.MAINNET_CHAIN_ID;
import static org.ethereum.config.Constants.REGTEST_CHAIN_ID;
import static org.ethereum.config.Constants.TESTNET_CHAIN_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


 class SetCodeSetCodeAuthorizationTransactionExecutorTest {

    private static final BigInteger ZERO_CHAIN_ID = ZERO;
    private static final BigInteger ONE_CHAIN_ID = ONE;

    private static final BigInteger NONCE_ONE_VALUE = ONE;
    private static final byte[] NONCE_ONE = new byte[] { 0x01 };
    private static final byte[] EMPTY_CODE = new byte[0];

    private Repository repository;
    private SetCodeAuthorizationTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SetCodeAuthorizationTransactionExecutor();
        repository = mock(Repository.class);
    }

    @Test
    void isDelegatedCode_shouldReturnTrue_whenCodeHasCorrectPrefixAndLength() {
        byte[] code = new byte[23];
        code[0] = (byte) 0xef;
        code[1] = 0x01;
        code[2] = 0x00;

        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
    }

    @Test
    void isDelegatedCode_shouldReturnFalse_whenCodeIsNull() {
        assertFalse(DelegationCodeResolver.isDelegatedCode(null));
    }

    @Test
    void isDelegatedCode_shouldReturnFalse_whenLengthIsInvalid() {
        assertFalse(DelegationCodeResolver.isDelegatedCode(new byte[10]));
    }

    @Test
    void isDelegatedCode_shouldReturnFalse_whenPrefixIsInvalid() {
        byte[] code = new byte[23];
        code[0] = 0x00;
        code[1] = 0x01;
        code[2] = 0x00;

        assertFalse(DelegationCodeResolver.isDelegatedCode(code));
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenNonceIsEmpty() {
       var tuple = new SetCodeAuthorization(
                        ZERO_CHAIN_ID,
                        randomAddress(),
                        new byte[0],
                        mock(ECDSASignature.class)
                );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Nonce is empty", ex.getMessage());
    }


    @Test
    void processAuthorizationTuple_shouldThrow_whenNonceExceedsLimit() {
        byte[] invalidNonce = new BigInteger("FFFFFFFFFFFFFFFF", 16).toByteArray();

       var tuple =
                new SetCodeAuthorization(
                        ZERO_CHAIN_ID,
                        randomAddress(),
                        invalidNonce,
                        mock(ECDSASignature.class)
                );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Nonce must be < 2^64 - 1", ex.getMessage());
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenChainIdIsInvalid() {
        var tuple =
                new SetCodeAuthorization(
                        BigInteger.valueOf(9999),
                        randomAddress(),
                        new byte[]{0x01},
                        mock(ECDSASignature.class)
                );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ONE_CHAIN_ID, tuple)
        );

        assertEquals("Invalid chain ID", ex.getMessage());
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenChainIdDoesNotMatchOuterTransaction() {
       var tuple =
                new SetCodeAuthorization(
                        BigInteger.valueOf(MAINNET_CHAIN_ID),
                        randomAddress(),
                        new byte[]{0x01},
                        mock(ECDSASignature.class)
                );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(
                        repository,
                        BigInteger.valueOf(TESTNET_CHAIN_ID),
                        tuple
                )
        );

        assertEquals("Chain ID mismatch", ex.getMessage());
    }

    @Test
    void processAuthorizationTuple_shouldAllowUniversalChainIdWithAnyOuterChainId() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        var tuple = createValidAuthorizationTuple(RskAddress.ZERO_ADDRESS,
                NONCE_ONE, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(NONCE_ONE_VALUE);

        long refund = executor.processAuthorizationTuple(repository, BigInteger.valueOf(33), tuple);
        verify(repository).saveCode(eq(authority), aryEq(EMPTY_CODE));
        verify(repository).increaseNonce(authority);
        assertEquals(0L, refund);
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenSignatureSIsTooHigh() {
        ECDSASignature signature = mock(ECDSASignature.class);

        when(signature.getS()).thenReturn(Constants.getSECP256K1N());

        var  tuple = new SetCodeAuthorization(
                        ZERO_CHAIN_ID,
                        randomAddress(),
                        new byte[]{0x01},
                        signature
                );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Signature s exceeds secp256k1n / 2", ex.getMessage());
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenSignatureRecoveryFails() {
        ECDSASignature signature = mock(ECDSASignature.class);

        when(signature.getS()).thenReturn(ONE);

        var tuple = new SetCodeAuthorization(
                        ZERO_CHAIN_ID,
                        randomAddress(),
                        new byte[]{0x01},
                        signature
                );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Signature recovery failed", ex.getMessage());
    }

    @Test
    void processAuthorizationTuple_shouldAllowNonceOneLessThanLimit() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        byte[] maxValidNonce = new BigInteger("FFFFFFFFFFFFFFFE", 16).toByteArray();

        var tuple = createValidAuthorizationTuple(
                RskAddress.ZERO_ADDRESS,
                maxValidNonce,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(new BigInteger(1, maxValidNonce));

        long refund = executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);

        verify(repository).saveCode(eq(authority), aryEq(EMPTY_CODE));
        verify(repository).increaseNonce(authority);
        assertEquals(0L, refund);
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenAuthorityHasNonDelegatedCode() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        var tuple = createValidAuthorizationTuple(
                randomAddress(),
                NONCE_ONE,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(new byte[]{0x01, 0x02, 0x03});
        when(repository.getNonce(authority)).thenReturn(ONE);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Authority contains non-delegated code", ex.getMessage());
        verify(repository, never()).saveCode(any(), any());
        verify(repository, never()).increaseNonce(any());
    }

    @Test
    void processAuthorizationTuple_shouldThrow_whenDelegatedCodeHasValidPrefixButInvalidLength() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        byte[] invalidDelegatedCode = new byte[22];
        invalidDelegatedCode[0] = (byte) 0xef;
        invalidDelegatedCode[1] = 0x01;
        invalidDelegatedCode[2] = 0x00;

        var tuple = createValidAuthorizationTuple(
                randomAddress(),
                NONCE_ONE,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(invalidDelegatedCode);
        when(repository.getNonce(authority)).thenReturn(ONE);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Authority contains non-delegated code", ex.getMessage());
        verify(repository, never()).saveCode(any(), any());
        verify(repository, never()).increaseNonce(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {MAINNET_CHAIN_ID, TESTNET_CHAIN_ID, REGTEST_CHAIN_ID})
    void processAuthorizationTuple_shouldAllowChainId_whenOuterChainIdMatches(long chainIdValue) {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        BigInteger chainId = BigInteger.valueOf(chainIdValue);

        var tuple = createValidAuthorizationTuple(
                RskAddress.ZERO_ADDRESS,
                NONCE_ONE,
                chainId,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        executor.processAuthorizationTuple(repository, chainId, tuple);

        verify(repository).saveCode(eq(authority), aryEq(EMPTY_CODE));
        verify(repository).increaseNonce(authority);
    }

    @Test
    void processAuthorizationTuple_shouldSaveExactDelegatedAddressInCode() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegated = randomAddress();

        var tuple = createValidAuthorizationTuple(
                delegated,
                NONCE_ONE,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);

        byte[] expectedCode = createDelegatedCode(delegated);

        verify(repository).saveCode(eq(authority), aryEq(expectedCode));
        verify(repository).increaseNonce(authority);
    }

    @Test
    void processAuthorizationTuple_shouldRejectWrongNonce() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        byte[] tupleNonce = new byte[]{0x00, 0x01};

        var tuple = createValidAuthorizationTuple(
                RskAddress.ZERO_ADDRESS,
                tupleNonce,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Authority nonce mismatch", ex.getMessage());
        verify(repository, never()).saveCode(any(), any());
        verify(repository, never()).increaseNonce(any());
    }

    @Test
    void processAuthorizationTuple_shouldClearCode_whenDelegatedAddressIsNullAddress() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        var tuple = createValidAuthorizationTuple(
                RskAddress.nullAddress(),
                NONCE_ONE,
                ZERO_CHAIN_ID,
                authorityKey
        );

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);

        verify(repository).saveCode(eq(authority), aryEq(EMPTY_CODE));
        verify(repository).increaseNonce(authority);
    }

    @Test
    void processAuthorizationTuple_shouldNotSaveOrIncreaseNonce_whenSignatureSIsTooHigh() {
        ECDSASignature signature = mock(ECDSASignature.class);
        when(signature.getS()).thenReturn(Constants.getSECP256K1N());

        var tuple = new SetCodeAuthorization(
                ZERO_CHAIN_ID,
                randomAddress(),
                NONCE_ONE,
                signature
        );

        assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        verify(repository, never()).saveCode(any(), any());
        verify(repository, never()).increaseNonce(any());
    }

    @Test
    void processAuthorizationTuple_shouldNotReadRepository_whenChainIdIsInvalid() {
        var tuple = new SetCodeAuthorization(
                BigInteger.valueOf(9999),
                randomAddress(),
                NONCE_ONE,
                mock(ECDSASignature.class)
        );

        assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        verify(repository, never()).getCode(any());
        verify(repository, never()).getNonce(any());
        verify(repository, never()).saveCode(any(), any());
        verify(repository, never()).increaseNonce(any());
    }

    @Test
    void verifyAuthorityNonce_shouldThrow_whenNonceDoesNotMatch() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        byte[] tupleNonce = new byte[]{0x01};
        byte[] repositoryNonce = new byte[]{0x02};

        var tuple = createValidAuthorizationTuple(randomAddress(), tupleNonce, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(new BigInteger(1, repositoryNonce));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple)
        );

        assertEquals("Authority nonce mismatch", ex.getMessage());
        verify(repository, never()).increaseNonce(any());
        verify(repository, never()).saveCode(any(), any());
    }


    @Test
    void calculateRefund_shouldReturnZeroForEmptyCode() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegatedAddress = RskAddress.ZERO_ADDRESS;
        byte[] tupleNonce = new byte[]{0x01};

        var tuple = createValidAuthorizationTuple(delegatedAddress, tupleNonce, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(new byte[]{});
        when(repository.getNonce(authority)).thenReturn(new BigInteger(1, tupleNonce));
        long refund = executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);
        assertEquals(0L, refund);
    }

    @Test
    void calculateRefund_shouldReturnExpectedRefundForDelegatedCode()  {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegatedAddress = randomAddress();
        var tuple = createValidAuthorizationTuple(randomAddress(), NONCE_ONE, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(createDelegatedCode(delegatedAddress));
        when(repository.getNonce(authority)).thenReturn(ONE);

        long refund = executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);
        assertEquals(9500L, refund);
    }

    @Test
    void writeDelegation_shouldClearCode_whenDelegatedAddressIsZero()  {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());

        var tuple = createValidAuthorizationTuple(RskAddress.ZERO_ADDRESS,
                NONCE_ONE, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);
        verify(repository).saveCode(eq(authority), aryEq(EMPTY_CODE));
        verify(repository).increaseNonce(authority);
    }

    @Test
    void writeDelegation_shouldSaveDelegatedCode() {
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegated = randomAddress();

        var tuple = createValidAuthorizationTuple(delegated, NONCE_ONE, ZERO_CHAIN_ID, authorityKey);

        when(repository.getCode(authority)).thenReturn(null);
        when(repository.getNonce(authority)).thenReturn(ONE);

        executor.processAuthorizationTuple(repository, ZERO_CHAIN_ID, tuple);
        verify(repository).saveCode(eq(authority), aryEq(createDelegatedCode(delegated)));
        verify(repository).increaseNonce(eq(authority));
    }

     @Test
     void processAuthorizationTuple_shouldInitializeDelegatedAuthorityAsCodeBearingAccount() {
         final BigInteger CHAIN_ID = BigInteger.valueOf(Constants.REGTEST_CHAIN_ID);
         SetCodeAuthorizationTransactionExecutor executor = new SetCodeAuthorizationTransactionExecutor();
         Repository repository = newRepository();

         ECKey authorityKey = new ECKey();
         RskAddress authority = new RskAddress(authorityKey.getAddress());

         RskAddress delegatedAddress = randomAddress();

         SetCodeAuthorization authorization = createValidAuthorizationTuple(delegatedAddress, new byte[] { 0x00 }, CHAIN_ID, authorityKey);

         executor.processAuthorizationTuple(repository, CHAIN_ID, authorization);

         byte[] expectedDelegationCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);

        assertArrayEquals(expectedDelegationCode, repository.getCode(authority), "The delegation indicator should be stored as the authority code");
        assertEquals(expectedDelegationCode.length, repository.getCodeLength(authority), "The authority should expose the delegation indicator code size");
        assertTrue(repository.isContract(authority), "The authority should have the repository storage-prefix marker");
        assertEquals(new Keccak256(HashUtil.keccak256(expectedDelegationCode)), repository.getCodeHashStandard(authority), "The standard code hash should match the delegation indicator");
     }

     @Test
     void processAuthorizationTuple_shouldTrackAuthorityRepositoryKeys() {
         final BigInteger chainId = BigInteger.valueOf(Constants.REGTEST_CHAIN_ID);

         IReadWrittenKeysTracker tracker = mock(IReadWrittenKeysTracker.class);

         MutableRepository repository = newRepository(tracker);

         SetCodeAuthorizationTransactionExecutor executor = new SetCodeAuthorizationTransactionExecutor();

         ECKey authorityKey = new ECKey();
         RskAddress authority = new RskAddress(authorityKey.getAddress());

         RskAddress delegatedAddress = randomAddress();

         SetCodeAuthorization authorization =
                 createValidAuthorizationTuple(
                         delegatedAddress,
                         new byte[] { 0x00 },
                         chainId,
                         authorityKey
                 );

         executor.processAuthorizationTuple(repository, chainId, authorization);

         TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

         ByteArrayWrapper accountKey = new ByteArrayWrapper(trieKeyMapper.getAccountKey(authority));
         ByteArrayWrapper codeKey = new ByteArrayWrapper(trieKeyMapper.getCodeKey(authority));
         ByteArrayWrapper storagePrefixKey = new ByteArrayWrapper(trieKeyMapper.getAccountStoragePrefixKey(authority));

         verify(tracker, atLeastOnce()).addNewReadKey(accountKey);
         verify(tracker, atLeastOnce()).addNewWrittenKey(accountKey);
         verify(tracker, atLeastOnce()).addNewWrittenKey(codeKey);
         verify(tracker, atLeastOnce()).addNewWrittenKey(storagePrefixKey);

         assertEquals(BigInteger.ONE, repository.getNonce(authority), "The authority nonce should be incremented after processing the authorization");
     }

     @Test
     void twoSetCodeTransactionsForSameAuthority_shouldNotExecuteInIndependentParallelSublists() {
         final BigInteger chainId = BigInteger.valueOf(Constants.REGTEST_CHAIN_ID);

         final short parallelSublists = 2;
         final short sequentialSublistNumber = parallelSublists;
         final long blockGasLimit = 8_160_000L;

         Block executionBlock = mock(Block.class);
         when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(blockGasLimit).toByteArray());

         ParallelizeTransactionHandler handler = ParallelizeTransactionHandler.create(parallelSublists, executionBlock, Constants.regtest().getMinSequentialSetGasLimit());

         ECKey authorityKey = new ECKey();
         RskAddress authority = new RskAddress(authorityKey.getAddress());
         RskAddress delegatedAddress = randomAddress();

         // Execute a real delegation installation and capture the repository dependencies reported to the tracker.
         IReadWrittenKeysTracker installTracker = mock(IReadWrittenKeysTracker.class);

         MutableRepository installRepository = newRepository(installTracker);

         SetCodeAuthorization installAuthorization = createValidAuthorizationTuple(delegatedAddress, new byte[] { 0x00 }, chainId, authorityKey);
         executor.processAuthorizationTuple(installRepository, chainId, installAuthorization);

         Set<ByteArrayWrapper> installReadKeys = captureReadKeys(installTracker);
         Set<ByteArrayWrapper> installWrittenKeys = captureWrittenKeys(installTracker);

         /*
          * Prepare the repository state required before clearing delegation:
          *
          * nonce                 = 1
          * delegation code       = present
          * storage-prefix marker = present
          */
         MutableTrieImpl clearTrie = new MutableTrieImpl(new TrieStoreImpl(new HashMapDB()), new Trie());
         Repository preparationRepository = new MutableRepository(clearTrie);
         preparationRepository.createAccount(authority);
         preparationRepository.setupContract(authority);
         preparationRepository.saveCode(authority, DelegationCodeResolver.createDelegatedCode(delegatedAddress));
         preparationRepository.setNonce(authority, BigInteger.ONE);

         /*
          * Execute a real clear authorization and capture only the keys
          * touched by the clear operation.
          */
         IReadWrittenKeysTracker clearTracker = mock(IReadWrittenKeysTracker.class);
         MutableRepository clearRepository = new MutableRepository(clearTrie, clearTracker);

         SetCodeAuthorization clearAuthorization = createValidAuthorizationTuple(RskAddress.ZERO_ADDRESS, new byte[] { 0x01 }, chainId, authorityKey);

         executor.processAuthorizationTuple(clearRepository, chainId, clearAuthorization);

         Set<ByteArrayWrapper> clearReadKeys = captureReadKeys(clearTracker);
         Set<ByteArrayWrapper> clearWrittenKeys = captureWrittenKeys(clearTracker);


         Account outerSender1 = new AccountBuilder().name("outer-sender-1").build();
         Account outerSender2 = new AccountBuilder().name("outer-sender-2").build();

         Transaction installTransaction =
                 new TransactionBuilder()
                         .nonce(1)
                         .sender(outerSender1)
                         .value(BigInteger.ZERO)
                         .gasLimit(BigInteger.valueOf(100_000))
                         .build();

         Transaction clearTransaction =
                 new TransactionBuilder()
                         .nonce(1)
                         .sender(outerSender2)
                         .value(BigInteger.ZERO)
                         .gasLimit(BigInteger.valueOf(100_000))
                         .build();

         long installGasUsed = GasCost.toGas(installTransaction.getGasLimit());
         long clearGasUsed = GasCost.toGas(clearTransaction.getGasLimit());

         Optional<Long> installSublistGas = handler.addTransaction(installTransaction, installReadKeys, installWrittenKeys, installGasUsed);

         Optional<Long> clearSublistGas = handler.addTransaction(clearTransaction, clearReadKeys, clearWrittenKeys, clearGasUsed);

         assertTrue(installSublistGas.isPresent(), "The delegation installation transaction should be accepted");
         assertTrue(clearSublistGas.isPresent(), "The delegation clearing transaction should be accepted");

         /*
          * {2} means both transactions were assigned to one parallel sublist.
          *
          * {1, 1} would mean that they were assigned to independent parallel
          * sublists and could execute concurrently.
          */
         assertArrayEquals(new short[] { 2 }, handler.getTransactionsPerSublistInOrder(), "Transactions modifying the same authority must not execute in independent parallel sublists");
         assertEquals(Arrays.asList(installTransaction, clearTransaction), handler.getTransactionsInOrder(), "Delegation installation should remain ordered before delegation clearing");
         assertEquals(installGasUsed + clearGasUsed, clearSublistGas.get(), "Both transactions should accumulate gas in the same sublist");
         assertEquals(0, handler.getGasUsedIn(sequentialSublistNumber), "The transactions may share a parallel sublist without using the global sequential sublist");
     }

     @Test
     void processAuthorizationTuple_shouldPreserveExistingStorageSubtreeWhenDelegationIsCleared() {
         final BigInteger chainId = BigInteger.valueOf(Constants.REGTEST_CHAIN_ID);

         MutableRepository repository =  new MutableRepository(new MutableTrieImpl(new TrieStoreImpl(new HashMapDB()), new Trie()));

         ECKey authorityKey = new ECKey();
         RskAddress authority = new RskAddress(authorityKey.getAddress());
         RskAddress delegatedAddress = randomAddress();

         SetCodeAuthorization installAuthorization = createValidAuthorizationTuple(delegatedAddress, new byte[] { 0x00 }, chainId, authorityKey);
         executor.processAuthorizationTuple(repository, chainId, installAuthorization);

         DataWord firstStorageKey = DataWord.valueOf(1);
         DataWord firstStorageValue = DataWord.valueOf(42);

         DataWord secondStorageKey = DataWord.valueOf(2);
         DataWord secondStorageValue = DataWord.valueOf(100);

         DataWord thirdStorageKey = DataWord.valueOf(3);
         DataWord thirdStorageValue = DataWord.valueOf(999);

         repository.addStorageRow(authority, firstStorageKey, firstStorageValue);
         repository.addStorageRow(authority, secondStorageKey, secondStorageValue);
         repository.addStorageRow(authority, thirdStorageKey, thirdStorageValue);

         byte[] storageRootBeforeClear = repository.getStorageStateRoot(authority);

         assertEquals(3, repository.getStorageKeysCount(authority), "The authority should have three storage slots before clearing delegation");

         SetCodeAuthorization clearAuthorization = createValidAuthorizationTuple(RskAddress.ZERO_ADDRESS, new byte[] { 0x01 }, chainId, authorityKey);
         executor.processAuthorizationTuple(repository, chainId, clearAuthorization);

         assertNull(repository.getCode(authority), "Clearing delegation should remove the delegation code entry");
         assertEquals(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY, repository.getCodeHashStandard(authority), "The cleared authority should expose the empty-code hash");
         assertEquals(0, repository.getCodeLength(authority), "The cleared authority should expose zero code length");
         assertTrue(repository.isContract(authority), "The storage-prefix marker should remain while the authority owns persistent storage");

         assertEquals(firstStorageValue, repository.getStorageValue(authority, firstStorageKey), "Clearing delegation should preserve the first storage slot");
         assertEquals(secondStorageValue, repository.getStorageValue(authority, secondStorageKey), "Clearing delegation should preserve the second storage slot");
         assertEquals(thirdStorageValue, repository.getStorageValue(authority, thirdStorageKey), "Clearing delegation should preserve the third storage slot");
         assertEquals(3, repository.getStorageKeysCount(authority), "Clearing delegation should preserve every slot in the authority storage subtree");

         assertArrayEquals(storageRootBeforeClear, repository.getStorageStateRoot(authority), "Clearing delegation should leave the complete authority storage subtree unchanged");
         assertEquals(BigInteger.TWO, repository.getNonce(authority), "Installing and clearing delegation should increment the authority nonce twice");
     }

     private Set<ByteArrayWrapper> captureReadKeys(IReadWrittenKeysTracker tracker) {
         ArgumentCaptor<ByteArrayWrapper> captor = ArgumentCaptor.forClass(ByteArrayWrapper.class);
         verify(tracker, atLeastOnce()).addNewReadKey(captor.capture());
         return new HashSet<>(captor.getAllValues());
     }

     private Set<ByteArrayWrapper> captureWrittenKeys(IReadWrittenKeysTracker tracker) {
         ArgumentCaptor<ByteArrayWrapper> captor = ArgumentCaptor.forClass(ByteArrayWrapper.class);
         verify(tracker, atLeastOnce()).addNewWrittenKey(captor.capture());
         return new HashSet<>(captor.getAllValues());
     }



    private SetCodeAuthorization createValidAuthorizationTuple(
            RskAddress delegatedAddress,
            byte[] nonce,
            BigInteger chainId,
            ECKey authorityKey
    ) {

        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(chainId),
                RLP.encodeElement(delegatedAddress.getBytes()),
                RLP.encodeElement(nonce)
        );

        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = 0x05;

        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);

        ECDSASignature signature = ECDSASignature.fromSignature(authorityKey.sign(HashUtil.keccak256(payload)));

        return new SetCodeAuthorization(
                        chainId,
                        delegatedAddress,
                        nonce,
                        signature
                );
    }

     private Repository newRepository() {
         return new MutableRepository(
                 new MutableTrieImpl(
                         new TrieStoreImpl(new HashMapDB()),
                         new Trie()
                 )
         );
     }

     private MutableRepository newRepository(IReadWrittenKeysTracker tracker) {
         return new MutableRepository(new MutableTrieImpl(new TrieStoreImpl(new HashMapDB()), new Trie()), tracker);
     }

    private byte[] createDelegatedCode(RskAddress delegatedAddress) {
        byte[] delegatedAddressBytes = delegatedAddress.getBytes();
        byte[] codeToSet = new byte[23];
        byte[] delegationPrefix = new byte[] {(byte) 0xef, 0x01, 0x00};
        System.arraycopy(delegationPrefix, 0, codeToSet, 0, 3);
        System.arraycopy(delegatedAddressBytes, 0, codeToSet, 3, 20);
        return codeToSet;
    }

    private RskAddress randomAddress() {
        ECKey key = new ECKey();
        return new RskAddress(key.getAddress());
    }
}
