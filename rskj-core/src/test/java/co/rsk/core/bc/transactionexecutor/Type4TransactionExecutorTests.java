package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.constants.BridgeConstants;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Type4TransactionExecutorTests {

    private final BigInteger ZERO_NONCE = BigInteger.ZERO;
    private final BigInteger ONE_NONCE = BigInteger.ONE;
    private final byte[] EMPTY_CODE = EMPTY_BYTE_ARRAY;
    private final byte[] EMPTY_DATA = EMPTY_BYTE_ARRAY;
    private final RskAddress EMPTY_ADDRESS = new RskAddress("0000000000000000000000000000000000000000");

    private ActivationConfig activationConfig;
    private Constants constants;
    private Repository tracker;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private BlockFactory blockFactory;
    private ProgramInvokeFactory programInvokeFactory;
    private Block executionBlock;
    private PrecompiledContracts precompiledContracts;
    private TestSystemProperties config;
    private int txIndex;
    private RskAddress receiver;
    private RskAddress delegatedAddress;
    private RskAddress sender;
    private ECKey senderKey;

    @BeforeEach
    void setUp() {
        receiver = createRandomAddress();
        senderKey = new ECKey();
        sender = new RskAddress(senderKey.getAddress());
        delegatedAddress = createRandomAddress();

        txIndex = 1;
        activationConfig = ActivationConfigsForTest.all();
        constants = mock(Constants.class);
        tracker = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        blockFactory = mock(BlockFactory.class);
        programInvokeFactory = mock(ProgramInvokeFactory.class);
        executionBlock = mock(Block.class);
        precompiledContracts = mock(PrecompiledContracts.class);
        config = spy(new TestSystemProperties());

        when(config.getActivationConfig()).thenReturn(activationConfig);
        when(config.getNetworkConstants()).thenReturn(constants);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockExecutionBlockGasLimit(6_800_000);
    }

    @Test
    void type4TransactionProcessesAuthorizationListAndExecutesAValueTransferCall() {
        var authorityKey = new ECKey();
        var authorityAddress = new RskAddress(authorityKey.getAddress());

        MutableRepository cacheTracker = mock(MutableRepository.class);
        MutableRepository authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 800_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var setCodeAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );
        var tx = createSignedType4Transaction(
                        senderKey,
                        constants.getChainId(),
                        ONE_NONCE,
                        600_000,
                        1,
                        1,
                        receiver,
                        2,
                        EMPTY_DATA,
                        setCodeAuthorization);

        TransactionExecutor txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authorityAddress, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST );
    }

    @Test
    void type4TransactionChargesAuthorizationCostForValidAndInvalidTuples() {
        var validAuthorityKey = new ECKey();
        var invalidAuthorityKey = new ECKey();

        var validAuthority = new RskAddress(validAuthorityKey.getAddress());
        var invalidAuthority = new RskAddress(invalidAuthorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var validAuthorizationTracker = mock(MutableRepository.class);
        var invalidAuthorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, validAuthorizationTracker, invalidAuthorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(validAuthorizationTracker, validAuthority, ZERO_NONCE, EMPTY_CODE);
        mockAuthorizationAccount(invalidAuthorizationTracker, invalidAuthority, ONE_NONCE, EMPTY_CODE);

        var validAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                validAuthorityKey
        );

        var invalidAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                invalidAuthorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                validAuthorization, invalidAuthorization);

        TransactionExecutor txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);
        verifyValidAuthorityChanges(validAuthorizationTracker, validAuthority, delegatedAddress);
        verifyInvalidAuthorityChanges(invalidAuthorizationTracker, invalidAuthority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST * 2L);
    }

    @Test
    void type4TransactionRefundsWhenAuthorizationAccountAlreadyHasDelegatedCode() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        MutableRepository cacheTracker = mock(MutableRepository.class);
        MutableRepository authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockFreeBridgeTxFalse();

        byte[] existingDelegatedCode = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(new RskAddress("0000000000000000000000000000000000000022"));

        when(authorizationTracker.getCode(authority)).thenReturn(existingDelegatedCode);
        when(authorizationTracker.getNonce(authority)).thenReturn(ONE_NONCE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization);

        var txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        assertEquals(txExecutor.getResult().getDeductedRefund(), 9500L);
    }

    @Test
    void type4TransactionWithEmptyDelegatedAddressIsProcessed() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                EMPTY_ADDRESS,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, HashUtil.keccak256(new byte[0]));
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAppliesAuthorizationBeforeLowLevelExecution() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        var authorization = createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerStartTrackingInvocations(2);

        verifyAuthorizationAppliedBeforeExecution(
                sender,
                authority,
                delegatedAddress,
                authorizationTracker,
                cacheTracker,
                600_000,
                2
        );
    }

    @Test
    void type4TransactionWithDataExecutesValidFlow() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        byte[] txData = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                txData,
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionRejectsSenderWithArbitraryContractCode() {
        byte[] arbitraryContractCode = new byte[] { 0x60, 0x00, 0x60, 0x00 };
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, arbitraryContractCode);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), eq(Coin.valueOf(600_000).negate()));
    }

    @Test
    void type4TransactionReceiverIsSameAuthorityWithNoDataTransfersValue() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(authority, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                authority,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verify(cacheTracker).transfer(
                eq(sender),
                eq(authority),
                eq(Coin.valueOf(2))
        );
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionWithMultipleAuthorizationsFromSameAuthorityPersistsLastOne() {
        var firstDelegatedAddress = createRandomAddress();
        var secondDelegatedAddress = createRandomAddress();

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var firstAuthorizationTracker = mock(MutableRepository.class);
        var secondAuthorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, firstAuthorizationTracker, secondAuthorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(firstAuthorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        byte[] firstDelegatedCode = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(firstDelegatedAddress);
        mockAuthorizationAccount(secondAuthorizationTracker, authority, ONE_NONCE, firstDelegatedCode);

        var firstAuthorization = createValidAuthorizationTuple(
                firstDelegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var secondAuthorization = createValidAuthorizationTuple(
                secondDelegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                firstAuthorization, secondAuthorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());


        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);
        verifyValidAuthorityChanges(firstAuthorizationTracker, authority, firstDelegatedCode);
        byte[] secondDelegatedCode =
                SetCodeAuthorizationTransactionExecutor.createDelegatedCode(secondDelegatedAddress);
        verifyValidAuthorityChanges(secondAuthorizationTracker, authority, secondDelegatedCode);
        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST);
    }

    @Test
    void type4TransactionAuthorizationWithUniversalChainIdZeroIsAccepted() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                (byte) 0,
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAuthorizationWithMismatchedNonZeroChainIdIsRejectedButOuterTxContinues() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        byte mismatchedChainId = Constants.TESTNET_CHAIN_ID;

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                mismatchedChainId,
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                Constants.MAINNET_CHAIN_ID,
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyInvalidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAuthorizationDoesNotExecuteDelegateTargetDuringAuthorizationStep() {
        var precompiledDelegatedAddress = PrecompiledContracts.REMASC_ADDR;

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                precompiledDelegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, precompiledDelegatedAddress);

        verify(precompiledContracts, never()).getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(precompiledDelegatedAddress.getBytes()))
        );
        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionOuterPrecompiledFailureDoesNotRollbackProcessedAuthorization() throws VMException {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        var precompiledContract = mock(PrecompiledContracts.PrecompiledContract.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        when(precompiledContracts.getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(receiver.getBytes()))
        )).thenReturn(precompiledContract);

        when(precompiledContract.getGasForData(any())).thenReturn(1L);
        when(precompiledContract.execute(any(byte[].class))).thenThrow(new RuntimeException("outer tx precompiled failure"));


        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verify(cacheTracker, never()).rollback();
        verify(cacheTracker, never()).transfer(any(), any(), any());

        assertNotNull(txExecutor.getResult().getException());
    }

    @Test
    void type4TransactionRejectsReceiverAddressLongerThanTwentyBytes() {
        Transaction tx = mock(Transaction.class);

        RskAddress invalidReceiver = mock(RskAddress.class);
        byte[] invalidReceiverBytes = new byte[21];
        Arrays.fill(invalidReceiverBytes, (byte) 1);

        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.isType4()).thenReturn(true);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isContractCreation()).thenReturn(false);
        when(tx.getReceiveAddress()).thenReturn(invalidReceiver);
        when(invalidReceiver.getBytes()).thenReturn(invalidReceiverBytes);
        when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(600_000).toByteArray());
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(tx.getValue()).thenReturn(Coin.valueOf(2));
        when(tx.getNonce()).thenReturn(ONE_NONCE.toByteArray());
        when(tx.getSender(any())).thenReturn(sender);
        when(tx.getSender()).thenReturn(sender);
        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockExecutionBlockGasLimit(6_800_000);
        mockValidSender(sender,1_000_000, ONE_NONCE, EMPTY_CODE );

        var cacheTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker);
        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
    }

    @Test
    void type4TransactionAllowsSenderWithValidDelegatedCode() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        byte[] validDelegatedSenderCode = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(delegatedAddress);

        mockValidSender(sender,1_000_000, ONE_NONCE, validDelegatedSenderCode);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);
        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAllowsSenderToAlsoBeAuthorizer() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, sender, ONE_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                senderKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyValidAuthorityChanges(authorizationTracker, sender, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionWhenSenderIsAuthorityReadsUpdatedSenderNonceDuringAuthorization() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        // Initial outer tx nonce is 0.
        mockValidSender(sender,1_000_000, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);
        // Authorization tracker should see sender nonce AFTER outer tx increment.
        mockAuthorizationAccount(authorizationTracker, sender, ONE_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                senderKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        InOrder inOrder = inOrder(tracker, authorizationTracker);

        inOrder.verify(tracker).increaseNonce(sender);
        inOrder.verify(authorizationTracker).getNonce(sender);

        verifyValidAuthorityChanges(authorizationTracker, sender, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionRejectsSenderWithDelegatedCodePrefixOnly() {
        byte[] invalidDelegatedCode = new byte[] {(byte) 0xef, 0x01, 0x00};

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, invalidDelegatedCode);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tracker).getCode(sender);
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
        verify(authorizationTracker, never()).commit();
    }

    @Test
    void type4TransactionRejectsSenderWithDelegatedCodeWrongLength() {
        byte[] invalidDelegatedCodeWrongLength = new byte[] {(byte) 0xef, 0x01, 0x00, 0x01};

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, invalidDelegatedCodeWrongLength);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tracker).getCode(sender);
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
        verify(authorizationTracker, never()).commit();
    }

    @Test
    void nonType4TransactionStillRejectsInvalidInitCodeSize() {
        Transaction tx = mock(Transaction.class);

        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(false);
        when(tx.isInitCodeSizeInvalidForTx(any())).thenReturn(true);
        when(tx.getData()).thenReturn(new byte[] { 1, 2, 3 });
        when(tx.getHash()).thenReturn(null);
        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isInitCodeSizeInvalidForTx(any());
        verify(tracker, never()).increaseNonce(any());
    }

    @Test
    void type4TransactionDoesNotRunInitCodeSizeValidation() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = spy(createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                new byte[] { 1, 2, 3 },
                authorization
        ));


        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verify(tx, never()).isInitCodeSizeInvalidForTx(any());
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void typedTransactionIsRejectedWhenNotAllowedBeforeActivation() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(true);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.getHash()).thenReturn(null);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isTypedTransactionNotAllowed(any());
        verify(tracker, never()).increaseNonce(any());
        verify(tracker, never()).addBalance(any(), any());
    }

    @Test
    void type4TransactionRejectsContractCreationBeforeExecution() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(true);
        when(tx.isContractCreation()).thenReturn(true);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.getHash()).thenReturn(null);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isContractCreation();
        verify(tracker, never()).increaseNonce(any());
        verify(tracker, never()).addBalance(any(), any());
    }

    @Test
    void nonType4TransactionDoesNotValidateSenderCode() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(false);
        when(tx.isInitCodeSizeInvalidForTx(any())).thenReturn(false);
        when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(600_000).toByteArray());
        when(tx.getNonce()).thenReturn(ONE_NONCE.toByteArray());
        when(tx.getValue()).thenReturn(Coin.ZERO);
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(tx.getSender(any())).thenReturn(sender);
        when(tx.getReceiveAddress()).thenReturn(receiver);
        when(tx.acceptTransactionSignature(anyByte())).thenReturn(true);

        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockValidSender(sender, 1_000_000, ONE_NONCE, new byte[] { 0x60, 0x00 });
        mockExecutionBlockGasLimit(6_800_000);
        mockReceiver(receiver, EMPTY_CODE);

        var cacheTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker);
        var txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());
        // sender code must not block non-Type4 txs
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
    }

    @Test
    void type4TransactionWithOnlyInvalidAuthorizationsStillChargesGasAndExecutesOuterTx() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ONE_NONCE, EMPTY_CODE);
        var invalidAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                invalidAuthorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyInvalidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionWithInvalidAuthorizationDoesNotMutateAuthorityCodeOrNonce() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        byte[] arbitraryContractCode = new byte[] { 0x60, 0x00, 0x60, 0x00 };
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, arbitraryContractCode);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verify(authorizationTracker).rollback();
        verify(authorizationTracker, never()).saveCode(any(), any());
        verify(authorizationTracker, never()).increaseNonce(any());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTransfer(cacheTracker, sender, 2);
    }

    private void mockFreeBridgeTxFalse() {
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(constants.getBridgeConstants()).thenReturn(bridgeConstants);
    }

    private void verifyTrackerStartTrackingInvocations(int times) {
        verify(tracker, times(times)).startTracking();
    }

    private void verifyTrackerIncreaseNonceAndReduceBalance(RskAddress type4TransactionSender, long balanceToDecrease ) {
        verify(tracker).increaseNonce(type4TransactionSender);
        verify(tracker).addBalance(eq(type4TransactionSender), eq(Coin.valueOf(balanceToDecrease).negate()));
    }

    private void mockExecutionBlockGasLimit(long gasLimit) {
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(gasLimit).toByteArray());
    }

    private void mockAccountWithBalanceAndNonce(Repository repository, RskAddress sender, long balance, BigInteger nonce) {
        when(repository.getNonce(sender)).thenReturn(nonce);
        when(repository.getBalance(sender)).thenReturn(Coin.valueOf(balance));
    }

    private SetCodeAuthorization createValidAuthorizationTuple(
            RskAddress delegatedAddress,
            BigInteger nonce,
            byte chainId,
            ECKey authorityKey
    ) {
        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(chainId)),
                RLP.encodeElement(delegatedAddress.getBytes()),
                RLP.encodeElement(nonce.toByteArray())
        );

        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = 0x05;

        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);

        ECDSASignature signature =
                ECDSASignature.fromSignature(authorityKey.sign(HashUtil.keccak256(payload)));

        return new SetCodeAuthorization(
                BigInteger.valueOf(chainId),
                delegatedAddress,
                nonce.toByteArray(),
                signature
        );
    }

    private TransactionExecutor newExecutor(Transaction transaction) {

        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(mock(ReceivedTxSignatureCache.class));

        TransactionExecutorFactory factory =
                new TransactionExecutorFactory(
                        config,
                        blockStore,
                        receiptStore,
                        blockFactory,
                        programInvokeFactory,
                        precompiledContracts,
                        blockTxSignatureCache
                );

        return factory.newInstance(
                transaction,
                txIndex,
                executionBlock.getCoinbase(),
                tracker,
                executionBlock,
                0L
        );
    }

    private Transaction createSignedType4Transaction(
            ECKey senderKey,
            byte chainId,
            BigInteger nonce,
            long gasLimit,
            long maxPriorityFeePerGas,
            long maxFeePerGas,
            RskAddress receiveAddress,
            long value,
            byte[] data,
            SetCodeAuthorization... authorizations
    ) {

        Transaction transaction = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(chainId)
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .maxPriorityFeePerGas(Coin.valueOf(maxPriorityFeePerGas))
                .maxFeePerGas(Coin.valueOf(maxFeePerGas))
                .receiveAddress(receiveAddress)
                .value(Coin.valueOf(value))
                .data(data)
                .authorizationList(List.of(authorizations))
                .build();

        transaction.sign(senderKey.getPrivKeyBytes());

        return transaction;
    }

    private void mockAuthorizationAccount(
            MutableRepository authorizationTracker,
            RskAddress authorityAddress,
            BigInteger nonce,
            byte[] delegatedAddress
    ) {

        when(authorizationTracker.getCode(authorityAddress))
                .thenReturn(delegatedAddress);

        when(authorizationTracker.getNonce(authorityAddress))
                .thenReturn(nonce);
    }

    private void verifyTransfer(MutableRepository cacheTracker, RskAddress type4TransactionSender, long value) {
        verify(cacheTracker).transfer(eq(type4TransactionSender), eq(receiver), eq(Coin.valueOf(value)));
    }

    private void mockAccountWithCode(Repository repository, RskAddress address, byte[] code)  {
         when(repository.getCode(address)).thenReturn(code);
    }

    private void verifyTransactionCostBiggerOrEqualThan(Transaction tx, long expectedAuthorizationCost) {
        assertTrue(tx.transactionCost(
                constants,
                activationConfig.forBlock(executionBlock.getNumber()),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        ) >= expectedAuthorizationCost);
    }

    private void mockAddressAsNotAPrecompiled(RskAddress address) {
        when(precompiledContracts.getContractForAddress(any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(address.getBytes())))).thenReturn(null);
    }

    private RskAddress createRandomAddress() {
        return new RskAddress(new ECKey().getAddress());
    }

    private static void verifyValidAuthorityChanges(MutableRepository authorizationTracker, RskAddress authorityAddress, RskAddress delegatedAddress) {
        byte[] delegatedAddressWithPrefix = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(delegatedAddress);

        verify(authorizationTracker).saveCode(eq(authorityAddress), aryEq(delegatedAddressWithPrefix));
        verify(authorizationTracker).increaseNonce(authorityAddress);
        verify(authorizationTracker).commit();
        verify(authorizationTracker, never()).rollback();
    }

    private static void verifyInvalidAuthorityChanges(MutableRepository invalidAuthorizationTracker, RskAddress invalidAuthority, RskAddress delegatedAddress) {
        byte[] delegatedAddressWithPrefix = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(delegatedAddress);
        verify(invalidAuthorizationTracker).rollback();
        verify(invalidAuthorizationTracker, never()).commit();
        verify(invalidAuthorizationTracker, never()).increaseNonce(invalidAuthority);
        verify(invalidAuthorizationTracker, never()).saveCode(invalidAuthority, delegatedAddressWithPrefix);
    }

    private static void verifyValidAuthorityChanges(MutableRepository authorizationTracker, RskAddress authorityAddress, byte[] delegatedAddress) {
        verify(authorizationTracker).saveCode(eq(authorityAddress), aryEq(delegatedAddress));
        verify(authorizationTracker).increaseNonce(authorityAddress);
        verify(authorizationTracker).commit();
        verify(authorizationTracker, never()).rollback();
    }

    private void verifyAuthorizationAppliedBeforeExecution(
            RskAddress txSender,
            RskAddress authority,
            RskAddress delegatedAddress,
            MutableRepository authorizationTracker,
            MutableRepository cacheTracker,
            long gasCost,
            long transferValue
    ) {
        InOrder inOrder = inOrder(tracker, authorizationTracker, cacheTracker);

        inOrder.verify(tracker).increaseNonce(txSender);
        inOrder.verify(tracker).addBalance(eq(txSender), eq(Coin.valueOf(gasCost).negate()));

        inOrder.verify(authorizationTracker).saveCode(
                eq(authority),
                aryEq(SetCodeAuthorizationTransactionExecutor.createDelegatedCode(delegatedAddress))
        );

        inOrder.verify(authorizationTracker).increaseNonce(authority);

        inOrder.verify(authorizationTracker).commit();

        inOrder.verify(cacheTracker).transfer(eq(txSender), eq(receiver), eq(Coin.valueOf(transferValue)));
    }

    private ProgramInvoke mockSuccessfulProgramInvoke(
            Transaction tx,
            MutableRepository cacheTracker
    ) {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);

        when(programInvokeFactory.createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                eq(cacheTracker),
                eq(blockStore),
                any(SignatureCache.class)
        )).thenReturn(programInvoke);

        when(programInvoke.getRepository()).thenReturn(cacheTracker);
        when(programInvoke.getOwnerAddress()).thenReturn(DataWord.valueOf(tx.getReceiveAddress().getBytes()));
        when(programInvoke.getCallerAddress()).thenReturn(DataWord.valueOf(sender.getBytes()));
        when(programInvoke.getBalance()).thenReturn(DataWord.ZERO);
        when(programInvoke.getCallValue()).thenReturn(DataWord.valueOf(tx.getValue().getBytes()));
        when(programInvoke.getDataSize()).thenReturn(DataWord.valueOf(tx.getData().length));

        return programInvoke;
    }

    private void mockReceiver(RskAddress receiver, byte[] code) {
        mockAccountWithCode(tracker, receiver, code);
        mockAddressAsNotAPrecompiled(receiver);
    }

    private void mockValidSender(RskAddress sender, long balance, BigInteger nonce, byte[] code) {
        mockAccountWithBalanceAndNonce(tracker, sender, balance, nonce);
        mockAccountWithCode(tracker, sender, code);
    }
}
