/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.TransactionValidationResult;
import co.rsk.net.handler.TxPendingValidator;
import co.rsk.net.handler.quota.TxQuotaChecker;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.ethereum.util.BIUtil.toBI;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class TransactionPoolImpl implements TransactionPool {
    private static final Logger logger = LoggerFactory.getLogger("txpool");

    private final TransactionSet pendingTransactions;
    private final TransactionSet queuedTransactions;

    private final Map<Keccak256, Long> transactionBlocks = new HashMap<>();
    private final Map<Keccak256, Long> transactionTimes = new HashMap<>();

    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final RepositoryLocator repositoryLocator;
    private final BlockFactory blockFactory;
    private final EthereumListener listener;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final SignatureCache signatureCache;
    private final int outdatedThreshold;
    private final int outdatedTimeout;

    private ScheduledExecutorService cleanerTimer;
    private ScheduledFuture<?> cleanerFuture;

    private ScheduledExecutorService accountTxRateLimitCleanerTimer;

    private Block bestBlock;

    private final TxPendingValidator validator;

    private final TxQuotaChecker quotaChecker;

    private final GasPriceTracker gasPriceTracker;

    private final TransactionPoolValidator transactionPoolValidator;

    @java.lang.SuppressWarnings("squid:S107")
    public TransactionPoolImpl(RskSystemProperties config, RepositoryLocator repositoryLocator, BlockStore blockStore, BlockFactory blockFactory, EthereumListener listener, TransactionExecutorFactory transactionExecutorFactory, SignatureCache signatureCache, int outdatedThreshold, int outdatedTimeout, TxQuotaChecker txQuotaChecker, GasPriceTracker gasPriceTracker) {
        this.config = config;
        this.blockStore = blockStore;
        this.repositoryLocator = repositoryLocator;
        this.blockFactory = blockFactory;
        this.listener = listener;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.signatureCache = signatureCache;
        this.outdatedThreshold = outdatedThreshold;
        this.outdatedTimeout = outdatedTimeout;
        this.quotaChecker = txQuotaChecker;
        this.gasPriceTracker = gasPriceTracker;
        pendingTransactions = new TransactionSet(this.signatureCache);
        queuedTransactions = new TransactionSet(this.signatureCache);

        this.validator = new TxPendingValidator(config.getNetworkConstants(), config.getActivationConfig(), config.getNumOfAccountSlots(), signatureCache);

        if (this.outdatedTimeout > 0) {
            this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TransactionPoolCleanerTimer"));
        }

        if (this.quotaChecker != null && this.config.isAccountTxRateLimitEnabled() && this.config.accountTxRateLimitCleanerPeriod() > 0) {
            this.accountTxRateLimitCleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TxQuotaCleanerTimer"));
        }
        this.transactionPoolValidator = new TransactionPoolValidator(config, this.validator, this.signatureCache);
    }

    @Override
    public void start() {
        processBest(blockStore.getBestBlock());

        if (this.outdatedTimeout > 0 && this.cleanerTimer != null) {
            this.cleanerFuture = this.cleanerTimer.scheduleAtFixedRate(this::cleanUp, this.outdatedTimeout, this.outdatedTimeout, TimeUnit.SECONDS);
        }

        if (this.accountTxRateLimitCleanerTimer != null) {
            this.accountTxRateLimitCleanerTimer.scheduleAtFixedRate(this.quotaChecker::cleanMaxQuotas, this.config.accountTxRateLimitCleanerPeriod(), this.config.accountTxRateLimitCleanerPeriod(), TimeUnit.MINUTES);
        }
    }

    @Override
    public void stop() {
        if (cleanerTimer != null) {
            cleanerTimer.shutdown();
        }

        if (accountTxRateLimitCleanerTimer != null) {
            accountTxRateLimitCleanerTimer.shutdown();
        }
    }

    public boolean hasCleanerFuture() {
        return this.cleanerFuture != null;
    }

    public void cleanUp() {
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        this.removeObsoleteTransactions(timestampSeconds - this.outdatedTimeout);
    }

    public int getOutdatedThreshold() {
        return outdatedThreshold;
    }

    public int getOutdatedTimeout() {
        return outdatedTimeout;
    }


    @Override
    public void setBestBlock(Block bestBlock) {
        this.bestBlock = bestBlock;
    }

    @Override
    public synchronized PendingState getPendingState() {
        return getPendingState(getCurrentRepository());
    }

    private PendingState getPendingState(RepositorySnapshot currentRepository) {
        removeObsoleteTransactions(this.outdatedThreshold, this.outdatedTimeout);
        return new PendingState(currentRepository, new TransactionSet(pendingTransactions, signatureCache), (repository, tx) -> transactionExecutorFactory.newInstance(tx, 0, bestBlock.getCoinbase(), repository, createFakePendingBlock(bestBlock), 0), signatureCache);
    }

    @Override
    public synchronized List<Transaction> addTransactions(final List<Transaction> txs) {
        List<Transaction> pendingTransactionsAdded = new ArrayList<>();

        for (Transaction tx : txs) {
            TransactionPoolAddResult result = addTransaction(tx);
            if (result.hasPendingTransactions()) {
                pendingTransactionsAdded.addAll(result.getPendingTransactionsAdded());
            }
        }
        return pendingTransactionsAdded;
    }

    @Override
    public synchronized TransactionPoolAddResult addTransaction(final Transaction tx) {
        TransactionPoolAddResult result = processAndAddTransactionToPool(tx);

        if (!result.transactionsWereAdded()) {
            return result;
        }

        List<Transaction> pendingTransactionsAdded = new ArrayList<>();
        if(result.hasPendingTransactions()) {
            pendingTransactionsAdded.addAll(collectPendingTransactionsUnlockedBy(tx));
        }

        this.emitEvents(pendingTransactionsAdded);

        return TransactionPoolAddResult.ok(result.getQueuedTransactionsAdded(), pendingTransactionsAdded);
    }

    private RepositorySnapshot getCurrentRepository() {
        return repositoryLocator.snapshotAt(this.bestBlock.getHeader());
    }

    private List<Transaction> promoteNextNonceQueuedTransactionsToPending(Transaction tx) {
        List<Transaction> promotedTransactions = new ArrayList<>();
        Optional<Transaction> nextTxOpt = findNextQueuedTransaction(tx);

        while (nextTxOpt.isPresent()) {
            Transaction nextTx = nextTxOpt.get();
            queuedTransactions.removeTransactionByHash(nextTx.getHash());

            TransactionPoolAddResult result = processAndAddTransactionToPool(nextTx);

            if (!result.hasPendingTransactions()) {
                break;
            }
            promotedTransactions.add(nextTx);
            nextTxOpt = findNextQueuedTransaction(nextTx);
        }
        return promotedTransactions;
    }

    private void emitEvents(List<Transaction> addedPendingTransactions) {
        if (listener != null && !addedPendingTransactions.isEmpty()) {
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(addedPendingTransactions);
                listener.onTransactionPoolChanged(TransactionPoolImpl.this);
            });
        }
    }

    private List<Transaction> collectPendingTransactionsUnlockedBy(Transaction tx) {
        List<Transaction> pendingTransactionsAdded = new ArrayList<>();
        pendingTransactionsAdded.add(tx);
        pendingTransactionsAdded.addAll(promoteNextNonceQueuedTransactionsToPending(tx));
        return pendingTransactionsAdded;
    }

    private Optional<Transaction> findNextQueuedTransaction(Transaction tx) {
        RskAddress sender = tx.getSender(signatureCache);
        BigInteger nextNonce = tx.getNonceAsInteger().add(BigInteger.ONE);

        return queuedTransactions.getTransactionsWithSender(sender).stream()
                .filter(queuedTx -> queuedTx.getNonceAsInteger().equals(nextNonce))
                .findFirst();
    }


    private TransactionPoolAddResult processAndAddTransactionToPool(final Transaction tx) {
        RepositorySnapshot repository = getCurrentRepository();
        PendingState pendingState = getPendingState(repository);
        var ctx = new TransactionPoolAddingContext(tx, repository, pendingState, bestBlock, signatureCache);

        Optional<TransactionPoolAddResult> duplicateResult = transactionPoolValidator.rejectIfTransactionAlreadyKnown(ctx.tx(), pendingTransactions, queuedTransactions);

        if (duplicateResult.isPresent()) {
            return duplicateResult.get();
        }

        AccountState senderState = repository.getAccountState(ctx.sender());
        Optional<TransactionPoolAddResult> validationResult = transactionPoolValidator.validateTransaction(ctx.tx(), ctx.bestBlock(), senderState);
        if (validationResult.isPresent()) {
            return validationResult.get();
        }

        logger.trace("add transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        Optional<Transaction> existingTx = pendingTransactions.getTransactionsWithSameNonce(ctx.sender(), ctx.nonce()).stream().findFirst();
        if (transactionPoolValidator.hasInsufficientGasPriceBump(ctx.tx(), existingTx)) {
            return TransactionPoolAddResult.withError("gas price not enough to bump transaction");
        }

        transactionBlocks.put(tx.getHash(), this.getCurrentBestBlockNumber());
        transactionTimes.put(tx.getHash(),  this.getCurrentTimeInSeconds());

        if (queueIfNonceTooHigh(tx, ctx.sender())) {
            signatureCache.storeSender(tx);
            return TransactionPoolAddResult.okQueuedTransaction(tx);
        }

        if (isAccountQuotaExceeded(tx, existingTx)) {
            return TransactionPoolAddResult.withError("account exceeds quota");
        }

        if (!transactionPoolValidator.canSenderAffordPendingTransactionsIncludingNew(
                ctx.tx(),
                pendingTransactions.getTransactionsWithSender(ctx.sender()), ctx.repository(), ctx.bestBlock()
        )) {
            return TransactionPoolAddResult.withError("insufficient funds to pay for pending and new transactions");
        }

        pendingTransactions.addTransaction(tx);
        signatureCache.storeSender(tx);

        return TransactionPoolAddResult.okPendingTransaction(tx);
    }



    private boolean isAccountQuotaExceeded(Transaction tx, Optional<Transaction> existingTx) {
        RepositorySnapshot currentRepository = this.getCurrentRepository();
        if (!config.isAccountTxRateLimitEnabled()) {
            return false;
        }
        TxQuotaChecker.CurrentContext context =
                new TxQuotaChecker.CurrentContext(
                        this.bestBlock,
                        getPendingState(),
                        currentRepository,
                        this.gasPriceTracker
                );

        return !quotaChecker.acceptTx(tx, existingTx.orElse(null), context);

    }

    private boolean queueIfNonceTooHigh(Transaction tx,  RskAddress rskAddress) {
        RepositorySnapshot currentRepository = this.getCurrentRepository();
        BigInteger expectedNonce = this.getPendingState(currentRepository).getNonce(rskAddress);
        BigInteger txNonce = tx.getNonceAsInteger();
        if (txNonce.compareTo(expectedNonce) > 0) {
            addQueuedTransaction(tx);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void processBest(Block newBlock) {
        logger.trace("Processing best block {} {}", newBlock.getNumber(), newBlock.getPrintableHash());

        BlockFork fork = getFork(this.bestBlock, newBlock);

        //we need to update the bestBlock before calling retractBlock
        //or else the transactions would be validated against outdated account state.
        this.setBestBlock(newBlock);

        if (fork != null) {
            for (Block blk : fork.getOldBlocks()) {
                retractBlock(blk);
            }

            for (Block blk : fork.getNewBlocks()) {
                acceptBlock(blk);
            }
        }

        removeObsoleteTransactions(this.outdatedThreshold, this.outdatedTimeout);

        if (listener != null) {
            EventDispatchThread.invokeLater(() -> listener.onTransactionPoolChanged(TransactionPoolImpl.this));
        }
    }

    private BlockFork getFork(Block oldBestBlock, Block newBestBlock) {
        if (oldBestBlock != null) {
            BlockchainBranchComparator branchComparator = new BlockchainBranchComparator(blockStore);
            return branchComparator.calculateFork(oldBestBlock, newBestBlock);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public void acceptBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        removeTransactions(txs);
    }

    @VisibleForTesting
    public void retractBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        logger.trace("Retracting block {} {} with {} txs", block.getNumber(), block.getPrintableHash(), txs.size());

        this.addTransactions(txs);
    }

    private void removeObsoleteTransactions(int depth, int timeout) {
        this.removeObsoleteTransactions(this.getCurrentBestBlockNumber(), depth, timeout);
    }

    @VisibleForTesting
    public void removeObsoleteTransactions(long currentBlock, int depth, int timeout) {
        List<Keccak256> toremove = new ArrayList<>();
        final long timestampSeconds = this.getCurrentTimeInSeconds();

        for (Map.Entry<Keccak256, Long> entry : transactionBlocks.entrySet()) {
            long block = entry.getValue().longValue();

            if (block < currentBlock - depth) {
                toremove.add(entry.getKey());
                logger.trace("Clear outdated transaction, block.number: [{}] hash: [{}]", block, entry.getKey());
            }
        }

        removeTransactionList(toremove);

        if (timeout > 0) {
            this.removeObsoleteTransactions(timestampSeconds - timeout);
        }
    }

    @VisibleForTesting
    public synchronized void removeObsoleteTransactions(long timeSeconds) {
        List<Keccak256> toremove = new ArrayList<>();

        for (Map.Entry<Keccak256, Long> entry : transactionTimes.entrySet()) {
            long txtime = entry.getValue().longValue();

            if (txtime <= timeSeconds) {
                toremove.add(entry.getKey());
                logger.trace("Clear outdated transaction, hash: [{}]", entry.getKey());
            }
        }

        removeTransactionList(toremove);
    }

    private void removeTransactionList(List<Keccak256> toremove) {
        for (Keccak256 key : toremove) {
            pendingTransactions.removeTransactionByHash(key);
            queuedTransactions.removeTransactionByHash(key);

            transactionBlocks.remove(key);
            transactionTimes.remove(key);
        }
    }

    @Override
    public synchronized void removeTransactions(List<Transaction> txs) {
        for (Transaction tx : txs) {
            Keccak256 khash = tx.getHash();
            pendingTransactions.removeTransactionByHash(khash);
            queuedTransactions.removeTransactionByHash(khash);

            logger.trace("Clear transaction, hash: [{}]", khash);
        }
    }

    @Override
    public synchronized List<Transaction> getPendingTransactions() {
        removeObsoleteTransactions(this.outdatedThreshold, this.outdatedTimeout);
        return Collections.unmodifiableList(pendingTransactions.getTransactions());
    }

    @Override
    public synchronized List<Transaction> getQueuedTransactions() {
        removeObsoleteTransactions(this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(queuedTransactions.getTransactions());
        return ret;
    }

    private void addQueuedTransaction(Transaction tx) {
        this.queuedTransactions.addTransaction(tx);
    }

    private long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private long getCurrentBestBlockNumber() {
        if (bestBlock == null) {
            return -1;
        }

        return bestBlock.getNumber();
    }

    private Block createFakePendingBlock(Block best) {
        // creating fake lightweight calculated block with no hashes calculations
        return blockFactory.newBlock(blockFactory.getBlockHeaderBuilder()
                                        .setParentHash(best.getHash().getBytes())
                                        .setDifficulty(best.getDifficulty())
                                        .setNumber(best.getNumber() + 1)
                                        .setGasLimit(ByteUtil.longToBytesNoLeadZeroes(Long.MAX_VALUE))
                                        .setTimestamp(best.getTimestamp() + 1)
                                        .build(),
                Collections.emptyList(), Collections.emptyList());
    }

}
