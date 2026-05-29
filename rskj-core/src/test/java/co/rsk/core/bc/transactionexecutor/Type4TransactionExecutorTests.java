package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.constants.BridgeConstants;
import org.ethereum.TestUtils;
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
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.stubbing.OngoingStubbing;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private RskAddress EMPTY_ADRESS = new RskAddress("0000000000000000000000000000000000000000");

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
        mockExecutionBlockGasLimit(6_800_000);
    }

    @Test
    void type4TransactionTryingToDeployContractFails() {
        mockFreeBrigeTxFalse();
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockAccountWithBalanceAndNonce(tracker, sender, 800_000, ONE_NONCE);
        assertThrows(IllegalArgumentException.class, () ->
            createSignedType4Transaction(
                    senderKey,
                    constants.getChainId(),
                    ONE_NONCE,
                    600_000,
                    1,
                    1,
                    null, // contract creation
                     0,
                    EMPTY_DATA));
    }

    @Test
    void type4TransactionProcessesAuthorizationListAndExecutesAValueTransferCall() {
        var authorityKey = new ECKey();
        var authorityAddress = new RskAddress(authorityKey.getAddress());

        MutableRepository cacheTracker = mock(MutableRepository.class);
        MutableRepository authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        when(tracker.getCode(receiver)).thenReturn(EMPTY_CODE);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockAccountWithBalanceAndNonce(tracker, sender, 800_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        mockFreeBrigeTxFalse();
        mockAddressAsNotAPrecompiled(receiver);

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

        mockAccountWithCode(tracker, receiver, EMPTY_CODE);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockFreeBrigeTxFalse();
        mockAddressAsNotAPrecompiled(receiver);

        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

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
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockFreeBrigeTxFalse();
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAccountWithCode(tracker, receiver, EMPTY_CODE);
        mockAddressAsNotAPrecompiled(receiver);
        mockFreeBrigeTxFalse();


        byte[] existingDelegatedCode = SetCodeAuthorizationTransactionExecutor.createDelegatedCode(new RskAddress("0000000000000000000000000000000000000022"));

        when(authorizationTracker.getCode(authority)).thenReturn(existingDelegatedCode);
        when(authorizationTracker.getNonce(authority)).thenReturn(ZERO_NONCE);

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
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        assertEquals(txExecutor.getResult().getDeductedRefund(), GasCost.PER_AUTH_BASE_COST);
    }


     @Test
    void type4TransactionAuthorizationToPrecompiledAddressDoesNotFailEntireTransaction() {
        var precompiledDelegatedAddress = PrecompiledContracts.REMASC_ADDR;

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockFreeBrigeTxFalse();
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);

        mockAccountWithCode(tracker, receiver, EMPTY_CODE);
        mockAddressAsNotAPrecompiled(receiver);

        mockAuthorizationAccount(
                authorizationTracker,
                authority,
                ZERO_NONCE,
                EMPTY_CODE
        );

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
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, precompiledDelegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);

        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionWithEmptyDelegatedAddressIsProcessed() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockFreeBrigeTxFalse();
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAccountWithCode(tracker, receiver, EMPTY_CODE);
        mockAddressAsNotAPrecompiled(receiver);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                EMPTY_ADRESS,
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
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);

        mockFreeBrigeTxFalse();
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAccountWithCode(tracker, receiver, EMPTY_CODE);
        mockAddressAsNotAPrecompiled(receiver);

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

    private void mockFreeBrigeTxFalse() {
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
        Random random = new Random(TransactionExecutorTest.class.hashCode());
        return new RskAddress(TestUtils.generateBytesFromRandom(random,20));
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
}
