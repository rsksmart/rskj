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
import co.rsk.core.commons.Keccak256;
import co.rsk.core.commons.RskAddress;
import co.rsk.net.handler.TxPendingValidator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
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

import static org.ethereum.crypto.HashUtil.keccak256;
import static org.ethereum.util.BIUtil.toBI;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class PendingStateImpl implements PendingState {
    private static final Logger logger = LoggerFactory.getLogger("pendingstate");
    private static final byte[] emptyUncleHashList = HashUtil.keccak256(RLP.encodeList(new byte[0]));

    private final Map<Keccak256, Transaction> pendingTransactions = new HashMap<>();
    private final Map<Keccak256, Transaction> wireTransactions = new HashMap<>();
    private final Map<Keccak256, Long> transactionBlocks = new HashMap<>();
    private final Map<Keccak256, Long> transactionTimes = new HashMap<>();

    private final RskSystemProperties config;
    private final Blockchain blockChain;
    private final BlockStore blockStore;
    private final Repository repository;
    private final ProgramInvokeFactory programInvokeFactory;
    private final EthereumListener listener;
    private final int outdatedThreshold;
    private final int outdatedTimeout;

    private ScheduledExecutorService cleanerTimer;
    private ScheduledFuture<?> cleanerFuture;

    private Block bestBlock;

    private Repository pendingStateRepository;
    private final TxPendingValidator validator = new TxPendingValidator();

    public PendingStateImpl(Blockchain blockChain,
                            BlockStore blockStore,
                            EthereumListener listener,
                            ProgramInvokeFactory programInvokeFactory,
                            Repository repository,
                            RskSystemProperties config) {
        this(config,
                blockChain,
                repository,
                blockStore,
                programInvokeFactory,
                listener,
                config.txOutdatedThreshold(),
                config.txOutdatedTimeout());
    }

    public PendingStateImpl(RskSystemProperties config,
                            Blockchain blockChain,
                            Repository repository,
                            BlockStore blockStore,
                            ProgramInvokeFactory programInvokeFactory,
                            EthereumListener listener,
                            int outdatedThreshold,
                            int outdatedTimeout) {
        this.config = config;
        this.blockChain = blockChain;
        this.blockStore = blockStore;
        this.repository = repository;
        this.programInvokeFactory = programInvokeFactory;
        this.listener = listener;
        this.outdatedThreshold = outdatedThreshold;
        this.outdatedTimeout = outdatedTimeout;

        this.pendingStateRepository = repository.startTracking();
        this.bestBlock = blockChain.getBestBlock();

        if (this.outdatedTimeout > 0) {
            this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PendingStateCleanerTimer"));
        }
    }

    @Override
    public void start() {
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

    public Blockchain getBlockChain() {
        return blockChain;
    }

    public int getOutdatedThreshold() { return outdatedThreshold; }

    public int getOutdatedTimeout() { return outdatedTimeout; }

    public synchronized Block getBestBlock() {
        if (bestBlock == null) {
            bestBlock = blockChain.getBestBlock();
        }

        return bestBlock;
    }

    @Override
    public synchronized List<Transaction> addWireTransactions(List<Transaction> transactions) {
        List<Transaction> added = new ArrayList<>();
        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        logger.trace("Trying add {} wire transactions using block {} {}", transactions.size(), bnumber, getBestBlock().getShortHash());

        for (Transaction tx : transactions) {
            if (!shouldAcceptTx(tx)) {
                continue;
            }

            logger.trace("Trying add wire transaction nonce {} hash {}", tx.getHash(), toBI(tx.getNonce()));

            Keccak256 hash = tx.getHash();

            if (pendingTransactions.containsKey(hash) || wireTransactions.containsKey(hash)) {
                logger.trace("TX already exists: {} ", tx);
                continue;
            }

            wireTransactions.put(hash, tx);
            transactionBlocks.put(hash, bnumber);
            final long timestampSeconds = this.getCurrentTimeInSeconds();
            transactionTimes.put(hash, timestampSeconds);

            added.add(tx);
        }

        if (listener != null && !added.isEmpty()) {
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(added);
                listener.onPendingStateChanged(PendingStateImpl.this);
            });
        }

        logger.trace("Wire transaction list added: {} new, {} valid of received {}, #of known txs: {}", added.size(), added.size(), transactions.size(), transactions.size());

        return added;
    }

    @Override
    public synchronized Repository getRepository() { return this.pendingStateRepository; }

    @Override
    public synchronized List<Transaction> getWireTransactions() {
        List<Transaction> txs = new ArrayList<>();
        txs.addAll(wireTransactions.values());
        return txs;
    }

    @Override
    public synchronized List<Transaction> getPendingTransactions() {
        List<Transaction> txs = new ArrayList<>();

        txs.addAll(pendingTransactions.values());

        return txs;
    }

    @Override
    public synchronized void addPendingTransaction(final Transaction tx) {
        if (!shouldAcceptTx(tx)) {
            return;
        }

        logger.trace("add pending transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        Keccak256 hash = tx.getHash();
        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        if (pendingTransactions.containsKey(hash)) {
            return;
        }

        pendingTransactions.put(hash, tx);
        transactionBlocks.put(hash, bnumber);
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        transactionTimes.put(hash, timestampSeconds);

        executeTransaction(tx);

        if (listener != null) {
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(Collections.singletonList(tx));
                listener.onPendingStateChanged(PendingStateImpl.this);
            });
        }
    }

    @Override
    public synchronized void processBest(Block block) {
        logger.trace("Processing best block {} {}", block.getNumber(), block.getShortHash());

        BlockFork fork = new BlockFork();
        fork.calculate(getBestBlock(), block, blockStore);

        for (Block blk : fork.getOldBlocks()) {
            retractBlock(blk);
        }

        for (Block blk : fork.getNewBlocks()) {
            acceptBlock(blk);
        }

        removeObsoleteTransactions(block.getNumber(), this.outdatedThreshold, this.outdatedTimeout);

        updateState();
        bestBlock = block;

        if (listener != null) {
            EventDispatchThread.invokeLater(() -> listener.onPendingStateChanged(PendingStateImpl.this));
        }
    }

    @VisibleForTesting
    public void acceptBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        clearPendingState(txs);
        clearWire(txs);
    }

    @VisibleForTesting
    public void retractBlock(Block block) {
        List<Transaction> txs = block.getTransactionsList();

        addWireTransactions(txs);
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
                        entry.getKey().toString());
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
                        entry.getKey().toString());
            }
        }

        removeTransactionList(toremove);
    }

    private void removeTransactionList(List<Keccak256> toremove) {
        for (Keccak256 key : toremove) {
            pendingTransactions.remove(key);
            wireTransactions.remove(key);
            transactionBlocks.remove(key);
            transactionTimes.remove(key);
        }
    }

    @Override
    public synchronized void clearPendingState(List<Transaction> txs) {
        for (Transaction tx : txs) {
            Keccak256 hash = tx.getHash();
            pendingTransactions.remove(hash);
            logger.trace("Clear pending transaction, hash: [{}]", hash);
        }
    }

    @Override
    public synchronized void clearWire(List<Transaction> txs) {
        for (Transaction tx: txs) {
            Keccak256 hash = tx.getHash();
            wireTransactions.remove(hash);
            logger.trace("Clear wire transaction, hash: [{}]", hash);
        }
    }

    @Override
    public synchronized List<Transaction> getAllPendingTransactions() {
        removeObsoleteTransactions(this.getCurrentBestBlockNumber(), this.outdatedThreshold, this.outdatedTimeout);
        List<Transaction> ret = new ArrayList<>();
        ret.addAll(pendingTransactions.values());
        ret.addAll(wireTransactions.values());
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

        Block best = blockChain.getBestBlock();

        TransactionExecutor executor = new TransactionExecutor(
                config, tx, 0, best.getCoinbase(), pendingStateRepository,
                blockStore, blockChain.getReceiptStore(), programInvokeFactory, createFakePendingBlock(best)
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
        if (bestBlock == null) {
            bestBlock = this.blockChain.getBestBlock();
        }

        if (bestBlock == null) {
            return 0;
        }

        return bestBlock.getNumber();
    }

    private Block createFakePendingBlock(Block best) {
        Trie txsTrie = new TrieImpl();

        // creating fake lightweight calculated block with no hashes calculations
        return new Block(best.getHash(),
                            new Keccak256(emptyUncleHashList), // uncleHash
                            RskAddress.nullAddress().getBytes(), //coinbase
                            new byte[32], // log bloom - from tx receipts
                            best.getDifficulty(), // difficulty
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
                            txsTrie.getHash(),  // TransactionsRoot-
                            Keccak256.zeroHash(),  // stateRoot
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
