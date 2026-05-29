package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.core.Repository;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.math.BigInteger;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static org.ethereum.config.Constants.MAINNET_CHAIN_ID;
import static org.ethereum.config.Constants.REGTEST_CHAIN_ID;
import static org.ethereum.config.Constants.TESTNET_CHAIN_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


 class SetCodeSetCodeAuthorizationTransactionExecutorTest {

    private static final BigInteger ZERO_CHAIN_ID = ZERO;
    private static final BigInteger ONE_CHAIN_ID = ONE;

    private static final BigInteger NONCE_ONE_VALUE = ONE;
    private static final byte[] NONCE_ONE = NONCE_ONE_VALUE.toByteArray();
    private static final byte[] EMPTY_CODE = HashUtil.keccak256(new byte[0]);

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

        assertTrue(SetCodeAuthorizationTransactionExecutor.isDelegatedCode(code));
    }
    @Test
    void isDelegatedCode_shouldReturnFalse_whenCodeIsNull() {
        assertFalse(SetCodeAuthorizationTransactionExecutor.isDelegatedCode(null));
    }

    @Test
    void isDelegatedCode_shouldReturnFalse_whenLengthIsInvalid() {
        assertFalse(SetCodeAuthorizationTransactionExecutor.isDelegatedCode(new byte[10]));
    }

    @Test
    void isDelegatedCode_shouldReturnFalse_whenPrefixIsInvalid() {
        byte[] code = new byte[23];
        code[0] = 0x00;
        code[1] = 0x01;
        code[2] = 0x00;

        assertFalse(SetCodeAuthorizationTransactionExecutor.isDelegatedCode(code));
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
        assertEquals(12500L, refund);
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

    private byte[] createDelegatedCode(RskAddress delegatedAddress) {
        byte[] delegatedAddressBytes = delegatedAddress.getBytes();
        byte[] codeToSet = new byte[SetCodeAuthorizationTransactionExecutor.DELEGATION_PREFIX_IN_BYTES.length + delegatedAddressBytes.length];

        System.arraycopy(SetCodeAuthorizationTransactionExecutor.DELEGATION_PREFIX_IN_BYTES, 0, codeToSet, 0, SetCodeAuthorizationTransactionExecutor.DELEGATION_PREFIX_IN_BYTES.length);
        System.arraycopy(delegatedAddressBytes, 0, codeToSet, SetCodeAuthorizationTransactionExecutor.DELEGATION_PREFIX_IN_BYTES.length, delegatedAddressBytes.length);
        return codeToSet;
    }

    private RskAddress randomAddress() {
        ECKey key = new ECKey();
        return new RskAddress(key.getAddress());
    }

}
