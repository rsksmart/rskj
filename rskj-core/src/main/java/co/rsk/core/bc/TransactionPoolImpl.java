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
import co.rsk.crypto.Keccak256;
import co.rsk.net.handler.TxPendingValidator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.EthereumListener;
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

    private final Map<Keccak256, Transaction> pendingTransactions = new HashMap<>();
    private final Map<Keccak256, Transaction> queuedTransactions = new HashMap<>();
    private final Map<Keccak256, Long> transactionBlocks = new HashMap<>();
    private final Map<Keccak256, Long> transactionTimes = new HashMap<>();

    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final Repository repository;
    private final ReceiptStore receiptStore;
    private final ProgramInvokeFactory programInvokeFactory;
    private final EthereumListener listener;
    private final int outdatedThreshold;
    private final int outdatedTimeout;

    private ScheduledExecutorService cleanerTimer;
    private ScheduledFuture<?> cleanerFuture;

    private Block bestBlock;

    private Repository pendingStateRepository;
    private final TxPendingValidator validator = new TxPendingValidator();

    public TransactionPoolImpl(BlockStore blockStore,
                               ReceiptStore receiptStore,
                               EthereumListener listener,
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
                               EthereumListener listener,
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

        this.pendingStateRepository = repository.startTracking();

        if (this.outdatedTimeout > 0) {
            this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PendingStateCleanerTimer"));
        }
    }

    @Override
    public void start(Block initialBestBlock) {
        processBest(initialBestBlock);

        if (this.outdatedTimeout <= 0 || this.cleanerTimer == null) {
            return;
        }

        this.cleanerFuture = this.cleanerTimer.scheduleAtFixedRate(this::cleanUp, this.outdatedTimeout, this.outdatedTimeout, TimeUnit.SECONDS);
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

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public int getOutdatedThreshold() { return outdatedThreshold; }

    public int getOutdatedTimeout() { return outdatedTimeout; }

    public Block getBestBlock() {
        return bestBlock;
    }

    @Override
    public synchronized Repository getRepository() { return this.pendingStateRepository; }

    @Override
    public synchronized List<Transaction> addTransactions(final List<Transaction> txs) {
        List<Transaction> added = new ArrayList<>();

        for (Transaction tx : txs) {
            if (this.addTransaction(tx)) {
                added.add(tx);
            }
        }

        return added;
    }

    @Override
    public synchronized boolean addTransaction(final Transaction tx) {
        if (!shouldAcceptTx(tx)) {
            return false;
        }

        Keccak256 hash = tx.getHash();
        logger.trace("add transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        if (pendingTransactions.containsKey(hash)) {
            return false;
        }

        if (queuedTransactions.containsKey(hash)) {
            return false;
        }

        transactionBlocks.put(hash, bnumber);
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        transactionTimes.put(hash, timestampSeconds);

        BigInteger txnonce = tx.getNonceAsInteger();

        if (!txnonce.equals(pendingStateRepository.getNonce(tx.getSender()))) {
            queuedTransactions.put(hash, tx);
            return false;
        }

        pendingTransactions.put(hash, tx);

        executeTransaction(tx);

        if (listener != null) {
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(Collections.singletonList(tx));
                listener.onPendingStateChanged(TransactionPoolImpl.this);
            });
        }

        return true;
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
            EventDispatchThread.invokeLater(() -> listener.onPendingStateChanged(TransactionPoolImpl.this));
        }
    }

    @VisibleForTesting
    public void acceptBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        clearPendingState(txs);
    }

    @VisibleForTesting
    public void retractBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        for (Transaction tx : txs) {
            this.addTransaction(tx);
        }
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
            pendingTransactions.remove(key);
            transactionBlocks.remove(key);
            transactionTimes.remove(key);
        }
    }

    @Override
    public synchronized void clearPendingState(List<Transaction> txs) {
        for (Transaction tx : txs) {
            pendingTransactions.remove(tx.getHash());
            logger.trace("Clear pending transaction, hash: [{}]", tx.getHash());
        }
    }

    @Override
    public synchronized List<Transaction> getPendingTransactions() {
        removeObsoleteTransactions(this.getCurrentBestBlockNumber(), this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(pendingTransactions.values());
        return ret;
    }

    @Override
    public synchronized List<Transaction> getQueuedTransactions() {
        removeObsoleteTransactions(this.getCurrentBestBlockNumber(), this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(queuedTransactions.values());
        return ret;
    }

    public synchronized void updateState() {
        logger.trace("update state");
        pendingStateRepository = repository.startTracking();

        TransactionSortedSet sorted = new TransactionSortedSet();
        sorted.addAll(pendingTransactions.values());

        for (Transaction tx : sorted.toArray(new Transaction[0])) {
            executeTransaction(tx);
        }
    }

    private void executeTransaction(Transaction tx) {
        logger.trace("Apply pending state tx: {} {}", toBI(tx.getNonce()), tx.getHash());

        TransactionExecutor executor = new TransactionExecutor(
                config, tx, 0, bestBlock.getCoinbase(), pendingStateRepository,
                blockStore, receiptStore, programInvokeFactory, createFakePendingBlock(bestBlock)
        );

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
    }

    private long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private long getCurrentBestBlockNumber() {
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

        return validator.isValid(tx, bestBlock.getGasLimitAsInteger());
    }

    public static class TransactionSortedSet extends TreeSet<Transaction> {
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
}
