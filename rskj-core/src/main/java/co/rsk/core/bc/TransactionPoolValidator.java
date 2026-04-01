package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.TransactionValidationResult;
import co.rsk.net.handler.TxPendingValidator;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.core.TransactionSet;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TransactionPoolValidator {

    private final RskSystemProperties config;
    private final TxPendingValidator validator;
    private final SignatureCache signatureCache;


    public TransactionPoolValidator(RskSystemProperties config,
                                    TxPendingValidator pendingValidator,
                                    SignatureCache signatureCache) {
        this.config = config;
        this.validator = pendingValidator;
        this.signatureCache = signatureCache;

    }

    public Optional<TransactionPoolAddResult> rejectIfTransactionAlreadyKnown(final Transaction tx,  final TransactionSet pendingTransactions,
                                                                               final TransactionSet queuedTransactions) {
        if (pendingTransactions.hasTransaction(tx)) {
            return Optional.of(TransactionPoolAddResult.withError(
                    "pending transaction with same hash already exists"
            ));
        }

        if (queuedTransactions.hasTransaction(tx)) {
            return Optional.of(TransactionPoolAddResult.withError(
                    "queued transaction with same hash already exists"
            ));
        }

        return Optional.empty();
    }

    public Optional<TransactionPoolAddResult> validateTransaction(
            Transaction tx,
            Block bestBlock,
            AccountState senderState
    ) {
        TransactionValidationResult validation = validator.isValid(tx, bestBlock, senderState);
        if (!validation.transactionIsValid()) {
            return Optional.of(TransactionPoolAddResult.withError(validation.getErrorMessage()));
        }
        return Optional.empty();
    }

    public boolean hasInsufficientGasPriceBump(Transaction newTx, Optional<Transaction> existingTx) {
        return existingTx.isPresent()
                && !isGasPriceBumpSufficient(newTx, existingTx.get());
    }

    /**
     * Checks whether the sender has enough balance to cover all their pending
     * transactions plus the given transaction. If a pending transaction with the
     * same nonce exists, it is treated as replaced and excluded from the cost.
     *
     * @return true if the sender can afford all pending transactions including {@code newTx}
     */
    public boolean canSenderAffordPendingTransactionsIncludingNew(
             Transaction newTx,
             List<Transaction> senderPendingTransactions,
             RepositorySnapshot currentRepository,
             Block bestBlock
     ) {

        RskAddress sender = newTx.getSender(signatureCache);
        Coin totalCost = getTxBaseCost(newTx, bestBlock);

        for (Transaction pendingTx : senderPendingTransactions) {
            boolean isSameNonce = Arrays.equals(pendingTx.getNonce(), newTx.getNonce());
            if (isSameNonce) {
                continue; // skip replaced transaction
            }
            totalCost = totalCost.add(getTxBaseCost(pendingTx, bestBlock));
        }
        Coin senderBalance = currentRepository.getBalance(sender);
        return totalCost.compareTo(senderBalance) <= 0;
    }

    private boolean isGasPriceBumpSufficient(Transaction newTx, Transaction oldTx) {
        //oldGasPrice * (100 + priceBump) / 100
        Coin oldGasPrice = oldTx.getGasPrice();
        Coin gasPriceBumped = oldGasPrice.multiply(BigInteger.valueOf(config.getGasPriceBump() + 100L)).divide(BigInteger.valueOf(100));

        return oldGasPrice.compareTo(newTx.getGasPrice()) < 0 && gasPriceBumped.compareTo(newTx.getGasPrice()) <= 0;
    }

    private Coin getTxBaseCost(Transaction tx,   Block bestBlock) {
        Coin gasCost = tx.getValue();
        if (bestBlock == null || getTransactionCost(tx, bestBlock.getNumber()) > 0) {
            BigInteger gasLimit = BigInteger.valueOf(GasCost.toGas(tx.getGasLimit()));
            gasCost = gasCost.add(tx.getGasPrice().multiply(gasLimit));
        }

        return gasCost;
    }

    private long getTransactionCost(Transaction tx, long number) {
        return tx.transactionCost(config.getNetworkConstants(), config.getActivationConfig().forBlock(number), signatureCache);
    }
}
