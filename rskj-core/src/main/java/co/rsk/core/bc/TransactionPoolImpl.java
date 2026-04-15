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
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.handler.quota.NoOpTxQuotaChecker;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.net.handler.quota.TxQuotaCheckerImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.listener.NoOpEthereumListener;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final SignatureCache signatureCache;
    private Block bestBlock;
    private final TransactionPoolValidator transactionPoolValidator;


    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final RepositoryLocator repositoryLocator;
    private final BlockFactory blockFactory;
    private final EthereumListener listener;
    private final TransactionExecutorFactory transactionExecutorFactory;

    private final int outdatedThreshold;
    private final int outdatedTimeout;

    private final ScheduledExecutorService cleanerTimer;
    private final ScheduledExecutorService accountTxRateLimitCleanerTimer;
    private final TxQuotaChecker quotaChecker;
    private final GasPriceTracker gasPriceTracker;



    @java.lang.SuppressWarnings("squid:S107")
    public TransactionPoolImpl(RskSystemProperties config, RepositoryLocator repositoryLocator, BlockStore blockStore, BlockFactory blockFactory, EthereumListener listener, TransactionExecutorFactory transactionExecutorFactory, SignatureCache signatureCache, int outdatedThreshold, int outdatedTimeout, TxQuotaCheckerImpl txQuotaChecker, GasPriceTracker gasPriceTracker) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.repositoryLocator = Objects.requireNonNull(repositoryLocator, "repositoryLocator must not be null");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore must not be null");
        this.blockFactory = Objects.requireNonNull(blockFactory, "blockFactory must not be null");
        this.transactionExecutorFactory = Objects.requireNonNull(transactionExecutorFactory, "transactionExecutorFactory must not be null");
        this.signatureCache = Objects.requireNonNull(signatureCache, "signatureCache must not be null");
        this.gasPriceTracker = Objects.requireNonNull(gasPriceTracker, "gasPriceTracker must not be null");
        if (outdatedThreshold <0) {
            throw new IllegalArgumentException("outdatedThreshold must be >= 0");
        }
        if (outdatedTimeout <= 0) {
            throw new IllegalArgumentException("outdatedTimeout must be > 0");
        }
        if (config.isAccountTxRateLimitEnabled() && txQuotaChecker == null) {
            throw new IllegalArgumentException("txQuotaChecker must not be null when account tx rate limit is enabled");
        }
        if (this.config.accountTxRateLimitCleanerPeriod() <= 0 ) {
            throw new IllegalArgumentException("AccountTxRateLimitCleanerPeriod must be > 0");
        }

        this.listener = listener != null ? listener :  NoOpEthereumListener.INSTANCE;
        this.outdatedThreshold = outdatedThreshold;
        this.outdatedTimeout = outdatedTimeout;

        this.pendingTransactions = new TransactionSet(this.signatureCache);
        this.queuedTransactions = new TransactionSet(this.signatureCache);
        this.transactionPoolValidator = new TransactionPoolValidator(config, this.signatureCache);

        this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TransactionPoolCleanerTimer"));
        this.accountTxRateLimitCleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TxQuotaCleanerTimer"));

        this.quotaChecker = txQuotaChecker != null ? txQuotaChecker : NoOpTxQuotaChecker.INSTANCE;
    }

    @Override
    public void start() {
        processBest(blockStore.getBestBlock());
        this.cleanerTimer.scheduleAtFixedRate(this::cleanUp, this.outdatedTimeout, this.outdatedTimeout, TimeUnit.SECONDS);
        if (isAccountTxRateLimitCleanerEnabled()) {
            this.accountTxRateLimitCleanerTimer.scheduleAtFixedRate(this.quotaChecker::cleanMaxQuotas, this.config.accountTxRateLimitCleanerPeriod(), this.config.accountTxRateLimitCleanerPeriod(), TimeUnit.MINUTES);
        }
    }

    @Override
    public void stop() {
        cleanerTimer.shutdown();
        accountTxRateLimitCleanerTimer.shutdown();
    }

    @Override
    public synchronized List<Transaction> getPendingTransactions() {
        this.removeObsoleteTransactions(this.getBestBlockNumber(),this.outdatedThreshold, this.outdatedTimeout);
        return Collections.unmodifiableList(pendingTransactions.getTransactions());
    }

    @Override
    public synchronized List<Transaction> getQueuedTransactions() {
        this.removeObsoleteTransactions(this.getBestBlockNumber(),this.outdatedThreshold, this.outdatedTimeout);
        return new ArrayList<>(queuedTransactions.getTransactions());
    }

    private boolean isAccountTxRateLimitCleanerEnabled() {
        return config.isAccountTxRateLimitEnabled() && config.accountTxRateLimitCleanerPeriod() > 0;
    }

    /**
     * Handles chain reorganization (fork) by updating the transaction pool and stays consistent with the current best chain.
     * <p>
     * Example: Old chain: A → B → C → D  || New chain: A → B → X → Y
     * If a fork occurs, transactions from blocks that were removed [C, D] from the main chain are reintroduced into the pool.
     * Transactions included in newly added blocks are removed [X, Y] from the pool, as they are now considered confirmed.
     * After applying fork changes, obsolete transactions are pruned based on the current
     * <p>
     * Note: The best block is updated before processing the fork to ensure that
     * any transaction validation operates on the latest best block state.
     *
     * @param newBlock the new best block of the blockchain
     */
    @Override
    public synchronized void processBest(Block newBlock) {
        logger.trace("Processing best block {} {}", newBlock.getNumber(), newBlock.getPrintableHash());

        BlockFork fork = calculateFork(newBlock);
        this.bestBlock = newBlock;

        if (fork != null) {
            fork.getOldBlocks().forEach(this::reintroduceBlockTransactions);
            fork.getNewBlocks().forEach(block -> removeTransactions(block.getTransactionsList()));
        }

        this.removeObsoleteTransactions(this.getBestBlockNumber(),this.outdatedThreshold, this.outdatedTimeout);
        this.notifyTransactionPoolChanged();
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

        this.notifyTransactionPoolUpdated(pendingTransactionsAdded);

        return TransactionPoolAddResult.ok(result.getQueuedTransactionsAdded(), pendingTransactionsAdded);
    }

    @Override
    public synchronized void removeTransactions(List<Transaction> txs) {
        txs.forEach(tx->removeTransactionByHash(tx.getHash()));
    }

    public void cleanUp() {
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        this.removeObsoleteByTime(timestampSeconds - this.outdatedTimeout);
    }

    @Override
    public void setBestBlock(Block bestBlock) {
        this.bestBlock = bestBlock;
    }

    @Override
    public synchronized PendingState getPendingState() {
        return getPendingState(getRepositoryAtBestBlock());
    }

    private PendingState getPendingState(RepositorySnapshot currentRepository) {
        this.removeObsoleteTransactions(this.getBestBlockNumber(),this.outdatedThreshold, this.outdatedTimeout);
        return new PendingState(currentRepository, new TransactionSet(pendingTransactions, signatureCache), (repository, tx) -> transactionExecutorFactory.newInstance(tx, 0, bestBlock.getCoinbase(), repository, createFakePendingBlock(bestBlock), 0), signatureCache);
    }

    private RepositorySnapshot getRepositoryAtBestBlock() {
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

    private void notifyTransactionPoolUpdated(List<Transaction> addedPendingTransactions) {
        if (!addedPendingTransactions.isEmpty()) {
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(addedPendingTransactions);
                listener.onTransactionPoolChanged(TransactionPoolImpl.this);
            });
        }
    }

    private void notifyTransactionPoolChanged() {
        EventDispatchThread.invokeLater(() -> listener.onTransactionPoolChanged(TransactionPoolImpl.this));
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
        var repository= getRepositoryAtBestBlock();
        var ctx = new TransactionPoolAddingContext(tx, repository, getPendingState(repository), bestBlock, signatureCache);

        Optional<TransactionPoolAddResult> duplicateResult = transactionPoolValidator.rejectIfTransactionAlreadyKnown(ctx.tx(), pendingTransactions, queuedTransactions);

        if (duplicateResult.isPresent()) {
            return duplicateResult.get();
        }

        AccountState senderState = ctx.repository().getAccountState(ctx.sender());
        Optional<TransactionPoolAddResult> validationResult = transactionPoolValidator.validateTransaction(ctx.tx(), ctx.bestBlock(), senderState);
        if (validationResult.isPresent()) {
            return validationResult.get();
        }

        logger.trace("add transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        Optional<Transaction> existingTx = pendingTransactions.getTransactionsWithSameNonce(ctx.sender(), ctx.nonce()).stream().findFirst();
        if (transactionPoolValidator.hasInsufficientGasPriceBump(ctx.tx(), existingTx)) {
            return TransactionPoolAddResult.withError("gas price not enough to bump transaction");
        }

        trackTransaction(tx);

        if (queueIfNonceTooHigh(tx, ctx.sender(), ctx.repository())) {
            signatureCache.storeSender(tx);
            return TransactionPoolAddResult.okQueuedTransaction(tx);
        }

        if (isAccountQuotaExceeded(tx, existingTx, ctx.repository(), ctx.pendingState())) {
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

    private void trackTransaction(Transaction tx) {
        Keccak256 hash = tx.getHash();
        transactionBlocks.put(hash, getBestBlockNumber());
        transactionTimes.put(hash, getCurrentTimeInSeconds());
    }

    private boolean isAccountQuotaExceeded(Transaction tx, Optional<Transaction> existingTx, RepositorySnapshot repository, PendingState pendingState) {

        if (!config.isAccountTxRateLimitEnabled()) {
            return false;
        }
        TxQuotaCheckerImpl.CurrentContext context =
                new TxQuotaCheckerImpl.CurrentContext(
                        this.bestBlock,
                        pendingState,
                        repository,
                        this.gasPriceTracker
                );

        return !quotaChecker.acceptTx(tx, existingTx.orElse(null), context);

    }

    private boolean queueIfNonceTooHigh(Transaction tx,  RskAddress rskAddress, RepositorySnapshot repository) {
        BigInteger expectedNonce = this.getPendingState(repository).getNonce(rskAddress);
        BigInteger txNonce = tx.getNonceAsInteger();
        if (txNonce.compareTo(expectedNonce) > 0) {
            this.queuedTransactions.addTransaction(tx);
            return true;
        }
        return false;
    }

    private BlockFork calculateFork(Block newBestBlock) {
        return this.bestBlock == null ? null : new BlockchainBranchComparator(blockStore).calculateFork(this.bestBlock, newBestBlock);
    }

    @VisibleForTesting
    public void reintroduceBlockTransactions(Block block) {
        List<Transaction> txs = block.getTransactionsList();
        logger.trace("Retracting block {} {} with {} txs", block.getNumber(), block.getPrintableHash(), txs.size());
        this.addTransactions(txs);
    }


    @VisibleForTesting
    public synchronized void removeObsoleteTransactions(long currentBlock, int depth, int timeout) {
        removeObsoleteByBlock(currentBlock, depth);
        if (timeout > 0) {
            long cutoffTime = getCurrentTimeInSeconds() - timeout;
            removeObsoleteByTime(cutoffTime);
        }
    }

    private synchronized void removeObsoleteByBlock(long currentBlock, int depth) {
        List<Keccak256> toRemove = new ArrayList<>();
        for (Map.Entry<Keccak256, Long> entry : transactionBlocks.entrySet()) {
            long block = entry.getValue();
            if (block < currentBlock - depth) {
                toRemove.add(entry.getKey());
                logger.trace("Clear outdated transaction, block.number: [{}] hash: [{}]", block, entry.getKey());
            }
        }
        toRemove.forEach(this::removeTransactionByHash);
    }

    private synchronized void removeObsoleteByTime(long cutoffTimeInSeconds) {
        List<Keccak256> toRemove = new ArrayList<>();
        for (Map.Entry<Keccak256, Long> entry : transactionTimes.entrySet()) {
            long time = entry.getValue();
            if (time <= cutoffTimeInSeconds) {
                toRemove.add(entry.getKey());
                logger.trace("Clear outdated transaction, hash: [{}]", entry.getKey());
            }
        }

        toRemove.forEach(this::removeTransactionByHash);
    }

    private synchronized void removeTransactionByHash(Keccak256 hash) {
        pendingTransactions.removeTransactionByHash(hash);
        queuedTransactions.removeTransactionByHash(hash);
        transactionBlocks.remove(hash);
        transactionTimes.remove(hash);
        logger.trace("Clear transaction, hash: [{}]", hash);
    }

    private long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private long getBestBlockNumber() {
        return bestBlock == null ? -1 :bestBlock.getNumber();
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

    //FOR TESTING

    public int getOutdatedThreshold() { return outdatedThreshold; }
    public int getOutdatedTimeout() { return outdatedTimeout; }

}
