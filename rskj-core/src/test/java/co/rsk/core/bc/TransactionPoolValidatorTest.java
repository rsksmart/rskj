package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.TransactionValidationResult;
import co.rsk.net.handler.TxPendingValidator;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.core.TransactionSet;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TransactionPoolValidatorTest {

    @TempDir
    public Path tempDir;

    private RskSystemProperties config;
    private SignatureCache signatureCache;
    private TransactionPoolValidator transactionPoolValidator;

    private RskTestContext rskTestContext;

    @BeforeEach
    void setUp() {
        rskTestContext = new RskTestContext(tempDir, "--regtest") {
            @Override
            protected synchronized GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }

            @Override
            protected synchronized RepositoryLocator buildRepositoryLocator() {
                return spy(super.buildRepositoryLocator());
            }
        };

        RskSystemProperties rskSystemProperties = spy(rskTestContext.getRskSystemProperties());
        when(rskSystemProperties.getNumOfAccountSlots()).thenReturn(10);
        when(rskSystemProperties.getGasPriceBump()).thenReturn(10);
        signatureCache = mock(SignatureCache.class);
        transactionPoolValidator = new TransactionPoolValidator(rskSystemProperties, signatureCache);
    }

    @Test
    void rejectIfTransactionAlreadyKnown_whenPendingContainsTx_returnsError() {
        Transaction tx = mock(Transaction.class);
        TransactionSet pending = mock(TransactionSet.class);
        TransactionSet queued = mock(TransactionSet.class);

        when(pending.hasTransaction(tx)).thenReturn(true);

        Optional<TransactionPoolAddResult> result =  transactionPoolValidator.rejectIfTransactionAlreadyKnown(tx, pending, queued);

        assertTrue(result.isPresent());
        assertTrue(result.get().getErrorMessage().contains("pending transaction with same hash already exists"));
        verify(pending).hasTransaction(tx);
        verify(queued, never()).hasTransaction(tx);
    }

    @Test
    void rejectIfTransactionAlreadyKnown_whenQueuedContainsTx_returnsError() {
        Transaction tx = mock(Transaction.class);
        TransactionSet pending = mock(TransactionSet.class);
        TransactionSet queued = mock(TransactionSet.class);

        when(pending.hasTransaction(tx)).thenReturn(false);
        when(queued.hasTransaction(tx)).thenReturn(true);

        Optional<TransactionPoolAddResult> result = transactionPoolValidator.rejectIfTransactionAlreadyKnown(tx, pending, queued);

        assertTrue(result.isPresent());
        assertTrue(result.get().getErrorMessage().contains("queued transaction with same hash already exists"));
        verify(pending).hasTransaction(tx);
        verify(queued).hasTransaction(tx);
    }

    @Test
    void rejectIfTransactionAlreadyKnown_whenTxIsUnknown_returnsEmpty() {
        Transaction tx = mock(Transaction.class);
        TransactionSet pending = mock(TransactionSet.class);
        TransactionSet queued = mock(TransactionSet.class);

        when(pending.hasTransaction(tx)).thenReturn(false);
        when(queued.hasTransaction(tx)).thenReturn(false);

        Optional<TransactionPoolAddResult> result = transactionPoolValidator.rejectIfTransactionAlreadyKnown(tx, pending, queued);

        assertTrue(result.isEmpty());
    }

    @Test
    void validateTransaction_whenValidatorRejects_returnsError() throws Exception {
        Transaction tx = mock(Transaction.class);
        Block bestBlock = mock(Block.class);
        AccountState senderState = mock(AccountState.class);

        TxPendingValidator validator = mock(TxPendingValidator.class);
        TransactionValidationResult validation = mock(TransactionValidationResult.class);

        when(validation.transactionIsValid()).thenReturn(false);
        when(validation.getErrorMessage()).thenReturn("invalid tx");
        when(validator.isValid(tx, bestBlock, senderState)).thenReturn(validation);

        setField(transactionPoolValidator, "validator", validator);

        Optional<TransactionPoolAddResult> result =
                transactionPoolValidator.validateTransaction(tx, bestBlock, senderState);

        assertTrue(result.isPresent());
        assertTrue(result.get().getErrorMessage().contains("invalid tx"));
    }

    @Test
    void validateTransaction_whenValidatorAccepts_returnsEmpty() throws Exception {
        Transaction tx = mock(Transaction.class);
        Block bestBlock = mock(Block.class);
        AccountState senderState = mock(AccountState.class);

        TxPendingValidator validator = mock(TxPendingValidator.class);
        TransactionValidationResult validation = mock(TransactionValidationResult.class);

        when(validation.transactionIsValid()).thenReturn(true);
        when(validator.isValid(tx, bestBlock, senderState)).thenReturn(validation);

        setField(transactionPoolValidator, "validator", validator);

        Optional<TransactionPoolAddResult> result =
                transactionPoolValidator.validateTransaction(tx, bestBlock, senderState);

        assertTrue(result.isEmpty());
    }

    @Test
    void hasInsufficientGasPriceBump_whenNoExistingTx_returnsFalse() {
        Transaction newTx = mock(Transaction.class);

        boolean result = transactionPoolValidator.hasInsufficientGasPriceBump(newTx, Optional.empty());

        assertFalse(result);
    }

    @Test
    void hasInsufficientGasPriceBump_whenNewGasPriceIsBelowConfiguredBump_returnsTrue() {
        Transaction oldTx = mock(Transaction.class);
        Transaction newTx = mock(Transaction.class);

        when(oldTx.getGasPrice()).thenReturn(Coin.valueOf(100));
        when(newTx.getGasPrice()).thenReturn(Coin.valueOf(109));

        boolean result = transactionPoolValidator.hasInsufficientGasPriceBump(newTx, Optional.of(oldTx));

        assertTrue(result);
    }

    @Test
    void hasInsufficientGasPriceBump_whenNewGasPriceMeetsConfiguredBump_returnsFalse() {
        Transaction oldTx = mock(Transaction.class);
        Transaction newTx = mock(Transaction.class);

        when(oldTx.getGasPrice()).thenReturn(Coin.valueOf(100));
        when(newTx.getGasPrice()).thenReturn(Coin.valueOf(111));

        boolean result = transactionPoolValidator.hasInsufficientGasPriceBump(newTx, Optional.of(oldTx));

        assertFalse(result);
    }

    @Test
    void canSenderAffordPendingTransactionsIncludingNew_whenBalanceCoversNewAndPending_returnsTrue() {
        Transaction newTx = txWithNonceAndValue(new byte[]{2}, 50);
        Transaction pendingTx = txWithNonceAndValue(new byte[]{1}, 40);

        RskAddress sender = mock(RskAddress.class);
        RepositorySnapshot repository = mock(RepositorySnapshot.class);
        Block bestBlock = mock(Block.class);

        when(bestBlock.getNumber()).thenReturn(10L);
        when(newTx.getSender(signatureCache)).thenReturn(sender);
        when(repository.getBalance(sender)).thenReturn(Coin.valueOf(90));

        boolean result =
                transactionPoolValidator.canSenderAffordPendingTransactionsIncludingNew(
                        newTx,
                        List.of(pendingTx),
                        repository,
                        bestBlock
                );

        assertTrue(result);
    }


    @Test
    void canSenderAffordPendingTransactionsIncludingNew_whenBalanceDoesNotCoverTotal_returnsFalse() {
        Transaction newTx = txWithNonceAndValue(new byte[]{2}, 50);
        Transaction pendingTx = txWithNonceAndValue(new byte[]{1}, 40);

        RskAddress sender = mock(RskAddress.class);
        RepositorySnapshot repository = mock(RepositorySnapshot.class);
        Block bestBlock = mock(Block.class);

        when(bestBlock.getNumber()).thenReturn(10L);
        when(newTx.getSender(signatureCache)).thenReturn(sender);
        when(repository.getBalance(sender)).thenReturn(Coin.valueOf(89));

        boolean result =
                transactionPoolValidator.canSenderAffordPendingTransactionsIncludingNew(
                        newTx,
                        List.of(pendingTx),
                        repository,
                        bestBlock
                );

        assertFalse(result);
    }

    @Test
    void canSenderAffordPendingTransactionsIncludingNew_skipsPendingWithSameNonce() {
        byte[] nonce = new byte[]{1};

        Transaction newTx = txWithNonceAndValue(nonce, 50);
        Transaction sameNoncePending = txWithNonceAndValue(nonce, 1_000); // should be ignored
        Transaction otherPending = txWithNonceAndValue(new byte[]{2}, 30); // should be counted

        RskAddress sender = mock(RskAddress.class);
        RepositorySnapshot repository = mock(RepositorySnapshot.class);
        Block bestBlock = mock(Block.class);

        when(bestBlock.getNumber()).thenReturn(10L);
        when(newTx.getSender(signatureCache)).thenReturn(sender);

        // Expected total = newTx (50) + otherPending (30) = 80
        // sameNoncePending (1000) must be skipped
        when(repository.getBalance(sender)).thenReturn(Coin.valueOf(80));

        boolean result =
                transactionPoolValidator.canSenderAffordPendingTransactionsIncludingNew(
                        newTx,
                        List.of(sameNoncePending, otherPending),
                        repository,
                        bestBlock
                );

        assertTrue(result);
    }

    private Transaction txWithNonceAndValue(byte[] nonce, long value) {
        Transaction tx = mock(Transaction.class);
        when(tx.getNonce()).thenReturn(nonce);
        when(tx.getValue()).thenReturn(Coin.valueOf(value));
        when(tx.transactionCost(any(), any(), eq(signatureCache))).thenReturn(0L);
        return tx;
    }


    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

}
