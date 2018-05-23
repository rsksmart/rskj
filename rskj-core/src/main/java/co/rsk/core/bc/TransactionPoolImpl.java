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
import co.rsk.crypto.Keccak256;
import co.rsk.net.handler.TxPendingValidator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
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
    private static final byte[] emptyUncleHashList = HashUtil.keccak256(RLP.encodeList(new byte[0]));

    private final TransactionSet pendingTransactions = new TransactionSet();
    private final TransactionSet queuedTransactions = new TransactionSet();

    private final Map<Keccak256, Long> transactionBlocks = new HashMap<>();
    private final Map<Keccak256, Long> transactionTimes = new HashMap<>();

    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final Repository repository;
    private final ReceiptStore receiptStore;
    private final ProgramInvokeFactory programInvokeFactory;
    private final CompositeEthereumListener listener;
    private final int outdatedThreshold;
    private final int outdatedTimeout;

    private ScheduledExecutorService cleanerTimer;
    private ScheduledFuture<?> cleanerFuture;

    private Block bestBlock;

    private Repository poolRepository;
    private final TxPendingValidator validator;

    public TransactionPoolImpl(BlockStore blockStore,
                               ReceiptStore receiptStore,
                               CompositeEthereumListener listener,
                               ProgramInvokeFactory programInvokeFactory,
                               Repository repository,
                               RskSystemProperties config) {
        this(config,
                repository,
                blockStore,
                receiptStore,
                programInvokeFactory,
                listener,
                config.txOutdatedThreshold(),
                config.txOutdatedTimeout());
    }

    public TransactionPoolImpl(RskSystemProperties config,
                               Repository repository,
                               BlockStore blockStore,
                               ReceiptStore receiptStore,
                               ProgramInvokeFactory programInvokeFactory,
                               CompositeEthereumListener listener,
                               int outdatedThreshold,
                               int outdatedTimeout) {
        this.config = config;
        this.blockStore = blockStore;
        this.repository = repository;
        this.receiptStore = receiptStore;
        this.programInvokeFactory = programInvokeFactory;
        this.listener = listener;
        this.outdatedThreshold = outdatedThreshold;
        this.outdatedTimeout = outdatedTimeout;

        this.poolRepository = repository.startTracking();
        this.validator = new TxPendingValidator(config);

        if (this.outdatedTimeout > 0) {
            this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TransactionPoolCleanerTimer"));
        }
    }

    @Override
    public void start(Block initialBestBlock) {
        processBest(initialBestBlock);

        if (this.outdatedTimeout <= 0 || this.cleanerTimer == null) {
            return;
        }

        this.cleanerFuture = this.cleanerTimer.scheduleAtFixedRate(this::cleanUp, this.outdatedTimeout, this.outdatedTimeout, TimeUnit.SECONDS);

        this.listener.addListener(new OnBlockListener());
    }

    public void stop() {
        if (this.cleanerFuture != null) {
            this.cleanerFuture.cancel(false);
            this.cleanerFuture = null;
        }
    }

    public boolean hasCleanerFuture() {
        return this.cleanerFuture != null;
    }

    public void cleanUp() {
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        this.removeObsoleteTransactions(timestampSeconds - this.outdatedTimeout);
    }

    public int getOutdatedThreshold() { return outdatedThreshold; }

    public int getOutdatedTimeout() { return outdatedTimeout; }

    public Block getBestBlock() {
        return bestBlock;
    }

    @Override
    public synchronized Repository getRepository() { return this.poolRepository; }

    @Override
    public synchronized List<Transaction> addTransactions(final List<Transaction> txs) {
        List<Transaction> added = new ArrayList<>();

        for (Transaction tx : txs) {
            if (this.addTransaction(tx)) {
                added.add(tx);

                Optional<Transaction> succesor = this.getQueuedSuccesor(tx);

                while (succesor.isPresent()) {
                    Transaction found = succesor.get();
                    queuedTransactions.removeTransactionByHash(found.getHash());

                    if (!this.addTransaction(found)) {
                        break;
                    }

                    added.add(found);

                    succesor = this.getQueuedSuccesor(found);
                }
            }
        }

        return added;
    }

    private Optional<Transaction> getQueuedSuccesor(Transaction tx) {
        BigInteger next = tx.getNonceAsInteger().add(BigInteger.ONE);

        List<Transaction> txsaccount = this.queuedTransactions.getTransactionsWithSender(tx.getSender());

        if (txsaccount == null) {
            return Optional.empty();
        }

        return txsaccount
                .stream()
                .filter(t -> t.getNonceAsInteger().equals(next))
                .findFirst();
    }

    @Override
    public synchronized boolean addTransaction(final Transaction tx) {
        if (!shouldAcceptTx(tx)) {
            return false;
        }

        Keccak256 hash = tx.getHash();
        logger.trace("add transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        if (pendingTransactions.hasTransaction(tx)) {
            return false;
        }

        if (queuedTransactions.hasTransaction(tx)) {
            return false;
        }

        transactionBlocks.put(hash, bnumber);
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        transactionTimes.put(hash, timestampSeconds);

        BigInteger txnonce = tx.getNonceAsInteger();

        if (!txnonce.equals(this.getNextNonceByAccount(tx.getSender()))) {
            this.addQueuedTransaction(tx);

            return false;
        }

        if (!senderCanPayPendingTransactionsAndNewTx(tx)) {
            // discard this tx to prevent spam
            return false;
        }

        pendingTransactions.addTransaction(tx);

        executeTransaction(tx);

        if (listener != null) {
            listener.onPendingTransactionsReceived(Collections.singletonList(tx));
            listener.onTransactionPoolChanged(TransactionPoolImpl.this);
        }

        return true;
    }

    private BigInteger getNextNonceByAccount(RskAddress account) {
        BigInteger nextNonce = this.repository.getNonce(account);

        for (Transaction tx : this.pendingTransactions.getTransactionsWithSender(account)) {
            BigInteger txNonce = tx.getNonceAsInteger();

            if (txNonce.compareTo(nextNonce) >= 0) {
                nextNonce = txNonce.add(BigInteger.ONE);
            }
        }

        return nextNonce;
    }

    @Override
    public synchronized void processBest(Block block) {
        logger.trace("Processing best block {} {}", block.getNumber(), block.getShortHash());

        if (bestBlock != null) {
            BlockFork fork = new BlockFork();
            fork.calculate(bestBlock, block, blockStore);

            for (Block blk : fork.getOldBlocks()) {
                retractBlock(blk);
            }

            for (Block blk : fork.getNewBlocks()) {
                acceptBlock(blk);
            }
        }

        removeObsoleteTransactions(block.getNumber(), this.outdatedThreshold, this.outdatedTimeout);

        updateState();
        bestBlock = block;

        if (listener != null) {
            listener.onTransactionPoolChanged(TransactionPoolImpl.this);
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

        this.addTransactions(txs);
    }

    @VisibleForTesting
    public void removeObsoleteTransactions(long currentBlock, int depth, int timeout) {
        List<Keccak256> toremove = new ArrayList<>();
        final long timestampSeconds = this.getCurrentTimeInSeconds();

        for (Map.Entry<Keccak256, Long> entry : transactionBlocks.entrySet()) {
            long block = entry.getValue().longValue();

            if (block < currentBlock - depth) {
                toremove.add(entry.getKey());
                logger.trace(
                        "Clear outdated transaction, block.number: [{}] hash: [{}]",
                        block,
                        entry.getKey());
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
                logger.trace(
                        "Clear outdated transaction, hash: [{}]",
                        entry.getKey());
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
        removeObsoleteTransactions(this.getCurrentBestBlockNumber(), this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(pendingTransactions.getTransactions());
        return ret;
    }

    @Override
    public synchronized List<Transaction> getQueuedTransactions() {
        removeObsoleteTransactions(this.getCurrentBestBlockNumber(), this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(queuedTransactions.getTransactions());
        return ret;
    }

    public synchronized void updateState() {
        logger.trace("update state");
        poolRepository = repository.startTracking();

        TransactionSortedSet sorted = new TransactionSortedSet();
        sorted.addAll(pendingTransactions.getTransactions());

        for (Transaction tx : sorted.toArray(new Transaction[0])) {
            executeTransaction(tx);
        }
    }

    private void executeTransaction(Transaction tx) {
        logger.trace("Apply pending state tx: {} {}", toBI(tx.getNonce()), tx.getHash());

        TransactionExecutor executor = new TransactionExecutor(
                config, tx, 0, bestBlock.getCoinbase(), poolRepository,
                blockStore, receiptStore, programInvokeFactory, createFakePendingBlock(bestBlock)
        );

        executor.init();
        executor.execute();
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
        Trie txsTrie = new TrieImpl();

        // creating fake lightweight calculated block with no hashes calculations
        return new Block(best.getHash().getBytes(),
                            emptyUncleHashList, // uncleHash
                            RskAddress.nullAddress().getBytes(), //coinbase
                            new byte[32], // log bloom - from tx receipts
                            best.getDifficulty().getBytes(), // difficulty
                            best.getNumber() + 1, //number
                            ByteUtil.longToBytesNoLeadZeroes(Long.MAX_VALUE), // max Gas Limit
                            0,  // gas used
                            best.getTimestamp() + 1,  // block time
                            new byte[0],  // extra data
                            new byte[0],  // mixHash (to mine)
                            new byte[0],  // nonce   (to mine)
                            new byte[0],
                            new byte[0],
                            new byte[0],
                            new byte[32],  // receiptsRoot
                            txsTrie.getHash().getBytes(),  // TransactionsRoot-
                            new byte[32],  // stateRoot
                            Collections.<Transaction>emptyList(), // tx list
                            Collections.<BlockHeader>emptyList(), // uncle list
                            ByteUtil.bigIntegerToBytes(BigInteger.ZERO)); //minimum gas price
    }

    private boolean shouldAcceptTx(Transaction tx) {
        if (bestBlock == null) {
            return true;
        }

        AccountState state = repository.getAccountState(tx.getSender());

        if (state == null) {
            // if the sender doesn't have an account yet, they could never pay for the transaction.
            return false;
        }

        return validator.isValid(tx, bestBlock, state);
    }

    /**
     * @param newTx a transaction to be added to the pending list (nonce = last pending nonce + 1)
     * @return whether the sender balance is enough to pay for all pending transactions + newTx
     */
    private boolean senderCanPayPendingTransactionsAndNewTx(Transaction newTx) {
        List<Transaction> transactions = pendingTransactions.getTransactionsWithSender(newTx.getSender());

        Coin accumTxCost = Coin.ZERO;
        for (Transaction t : transactions) {
            accumTxCost = accumTxCost.add(getTxBaseCost(t));
        }

        Coin costWithNewTx = accumTxCost.add(getTxBaseCost(newTx));
        return costWithNewTx.compareTo(repository.getBalance(newTx.getSender())) <= 0;
    }

    private Coin getTxBaseCost(Transaction tx) {
        Coin gasCost = tx.getValue();
        if (bestBlock == null || tx.transactionCost(config, bestBlock) > 0) {
            BigInteger gasLimit = new BigInteger(1, tx.getGasLimit());
            gasCost = gasCost.add(tx.getGasPrice().multiply(gasLimit));
        }

        return gasCost;
    }

    public static class TransactionSortedSet extends TreeSet<Transaction> {
        private static final long serialVersionUID = -6064476246506094585L;

        public TransactionSortedSet() {
            super((tx1, tx2) -> {
                long nonceDiff = ByteUtil.byteArrayToLong(tx1.getNonce()) -
                        ByteUtil.byteArrayToLong(tx2.getNonce());
                if (nonceDiff != 0) {
                    return nonceDiff > 0 ? 1 : -1;
                }
                return tx1.getHash().compareTo(tx2.getHash());
            });
        }
    }

    private class OnBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            processBest(block);
        }
    }
}
