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
import co.rsk.net.handler.TxPendingValidator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.listener.EthereumListener;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.util.RLP;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.BIUtil.toBI;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class PendingStateImpl implements PendingState {
    private static final Logger logger = LoggerFactory.getLogger("pendingstate");
    private static final byte[] emptyUncleHashList = sha3(RLP.encodeList(new byte[0]));

    private Map<ByteArrayWrapper, Transaction> pendingTransactions = new HashMap<>();
    private Map<ByteArrayWrapper, Transaction> wireTransactions = new HashMap<>();
    private Map<ByteArrayWrapper, Long> transactionBlocks = new HashMap<>();
    private Map<ByteArrayWrapper, Long> transactionTimes = new HashMap<>();

    private int outdatedThreshold = 0;
    private int outdatedTimeout = 0;

    private ScheduledExecutorService cleanerTimer;
    private ScheduledFuture<?> cleanerFuture;

    @Autowired
    private BlockStore blockStore;

    @Autowired
    private ProgramInvokeFactory programInvokeFactory;

    @Autowired
    private EthereumListener listener;

    private final Blockchain blockChain;
    private final Repository repository;

    private Block bestBlock;

    private Repository pendingStateRepository;
    private TxPendingValidator validator = new TxPendingValidator();

    @Autowired
    public PendingStateImpl(Blockchain blockChain,
                            BlockStore blockStore,
                            Repository repository) {
        this.blockChain = blockChain;
        this.blockStore = blockStore;
        this.repository = repository;
    }

    public PendingStateImpl(Blockchain blockChain,
                            Repository repository,
                            BlockStore blockStore,
                            ProgramInvokeFactory programInvokeFactory,
                            EthereumListener listener,
                            int outdatedThreshold,
                            int outdatedTimeout) {
        this(blockChain, blockStore, repository);
        this.programInvokeFactory = programInvokeFactory;
        this.outdatedThreshold = outdatedThreshold;
        this.outdatedTimeout = outdatedTimeout;
        this.listener = listener;

        init();
    }

    @Override
    @PostConstruct
    public final void init() {
        if (this.repository != null)
            this.pendingStateRepository = repository.startTracking();

        if (this.blockChain != null)
            this.bestBlock = blockChain.getBestBlock();

        if (this.outdatedThreshold == 0)
            this.outdatedThreshold = RskSystemProperties.CONFIG.txOutdatedThreshold();
        if (this.outdatedTimeout == 0)
            this.outdatedTimeout = RskSystemProperties.CONFIG.txOutdatedTimeout();

        if (this.outdatedTimeout > 0)
            this.cleanerTimer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PendingStateCleanerTimer"));
    }

    @Override
    public void start() {
        if (this.outdatedTimeout <= 0 || this.cleanerTimer == null)
            return;

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
        if (bestBlock == null)
            bestBlock = blockChain.getBestBlock();

        return bestBlock;
    }

    @Override
    public synchronized List<Transaction> addWireTransactions(List<Transaction> transactions) {
        List<Transaction> added = new ArrayList<>();
        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        logger.info("Trying add {} wire transactions using block {} {}", transactions.size(), bnumber, getBestBlock().getShortHash());

        for (Transaction tx : transactions) {
            if (!shouldAcceptTx(tx))
                continue;

            logger.info("Trying add wire transaction nonce {} hash {}", tx.getHash(), toBI(tx.getNonce()));

            ByteArrayWrapper hash = new ByteArrayWrapper(tx.getHash());

            if (pendingTransactions.containsKey(hash) || wireTransactions.containsKey(hash)) {
                logger.info("TX already exists: {} ", tx);
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

        logger.info("Wire transaction list added: {} new, {} valid of received {}, #of known txs: {}", added.size(), added.size(), transactions.size(), transactions.size());

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
        if (!shouldAcceptTx(tx))
            return;

        logger.trace("add pending transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        ByteArrayWrapper hash = new ByteArrayWrapper(tx.getHash());
        Long bnumber = Long.valueOf(getCurrentBestBlockNumber());

        if (pendingTransactions.containsKey(hash))
            return;

        pendingTransactions.put(hash, tx);
        transactionBlocks.put(hash, bnumber);
        final long timestampSeconds = this.getCurrentTimeInSeconds();
        transactionTimes.put(hash, timestampSeconds);

        executeTransaction(tx);

        if (listener != null)
            EventDispatchThread.invokeLater(() -> {
                listener.onPendingTransactionsReceived(Collections.singletonList(tx));
                listener.onPendingStateChanged(PendingStateImpl.this);
            });
    }

    @Override
    public synchronized void processBest(Block block) {
        logger.trace("Processing best block {} {}", block.getNumber(), block.getShortHash());

        BlockFork fork = new BlockFork();
        fork.calculate(getBestBlock(), block, blockStore);

        for (Block blk : fork.getOldBlocks())
            retractBlock(blk);

        for (Block blk : fork.getNewBlocks())
            acceptBlock(blk);

        removeObsoleteTransactions(block.getNumber(), this.outdatedThreshold, this.outdatedTimeout);

        updateState();
        bestBlock = block;

        if (listener != null)
            EventDispatchThread.invokeLater(() -> listener.onPendingStateChanged(PendingStateImpl.this));
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
        List<ByteArrayWrapper> toremove = new ArrayList<>();
        final long timestampSeconds = this.getCurrentTimeInSeconds();

        for (Map.Entry<ByteArrayWrapper, Long> entry : transactionBlocks.entrySet()) {
            long block = entry.getValue().longValue();

            if (block < currentBlock - depth) {
                toremove.add(entry.getKey());
                logger.info(
                        "Clear outdated transaction, block.number: [{}] hash: [{}]",
                        block,
                        entry.getKey().toString());
            }
        }

        removeTransactionList(toremove);

        if (timeout > 0)
            this.removeObsoleteTransactions(timestampSeconds - timeout);
    }

    @VisibleForTesting
    public synchronized void removeObsoleteTransactions(long timeSeconds) {
        List<ByteArrayWrapper> toremove = new ArrayList<>();

        for (Map.Entry<ByteArrayWrapper, Long> entry : transactionTimes.entrySet()) {
            long txtime = entry.getValue().longValue();

            if (txtime <= timeSeconds) {
                toremove.add(entry.getKey());
                logger.info(
                        "Clear outdated transaction, hash: [{}]",
                        entry.getKey().toString());
            }
        }

        removeTransactionList(toremove);
    }

    private void removeTransactionList(List<ByteArrayWrapper> toremove) {
        for (ByteArrayWrapper key : toremove) {
            pendingTransactions.remove(key);
            wireTransactions.remove(key);
            transactionBlocks.remove(key);
            transactionTimes.remove(key);
        }
    }

    @Override
    public synchronized void clearPendingState(List<Transaction> txs) {
        for (Transaction tx : txs) {
            byte[] bhash = tx.getHash();
            ByteArrayWrapper hash = new ByteArrayWrapper(bhash);
            pendingTransactions.remove(hash);
            logger.info("Clear pending transaction, hash: [{}]", Hex.toHexString(bhash));
        }
    }

    @Override
    public synchronized void clearWire(List<Transaction> txs) {
        for (Transaction tx: txs) {
            byte[] bhash = tx.getHash();
            ByteArrayWrapper hash = new ByteArrayWrapper(bhash);
            wireTransactions.remove(hash);
            logger.info("Clear wire transaction, hash: [{}]", Hex.toHexString(bhash));
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

    public void updateState() {
        logger.trace("update state");
        pendingStateRepository = repository.startTracking();

        TransactionSortedSet sorted = new TransactionSortedSet();
        sorted.addAll(pendingTransactions.values());

        for (Transaction tx : sorted.toArray(new Transaction[0]))
            executeTransaction(tx);
    }

    private void executeTransaction(Transaction tx) {
        logger.info("Apply pending state tx: {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        Block best = blockChain.getBestBlock();

        TransactionExecutor executor = new TransactionExecutor(
                tx, best.getCoinbase(), pendingStateRepository,
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
        if (bestBlock == null)
            bestBlock = this.blockChain.getBestBlock();

        if (bestBlock == null)
            return 0;

        return bestBlock.getNumber();
    }

    private Block createFakePendingBlock(Block best) {
        Trie txsTrie = new TrieImpl();

        // creating fake lightweight calculated block with no hashes calculations
        return new Block(best.getHash(),
                            emptyUncleHashList, // uncleHash
                            new byte[32], //coinbase
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
                            new byte[32],  // stateRoot
                            Collections.<Transaction>emptyList(), // tx list
                            Collections.<BlockHeader>emptyList(), // uncle list
                            ByteUtil.bigIntegerToBytes(BigInteger.ZERO)); //minimum gas price
    }

    private boolean shouldAcceptTx(Transaction tx) {
        if (bestBlock == null)
            return true;
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
                return FastByteComparisons.compareTo(tx1.getHash(), 0, 32, tx2.getHash(), 0, 32);
            });
        }
    }
}
