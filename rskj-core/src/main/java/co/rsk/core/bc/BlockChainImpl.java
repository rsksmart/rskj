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
import com.google.common.annotations.VisibleForTesting;
import co.rsk.blocks.BlockRecorder;
import co.rsk.net.Metrics;
import co.rsk.panic.PanicProcessor;
import co.rsk.validators.BlockValidator;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 29/07/2016.
 */

/**
 * Original comment:
 *
 * The Ethereum blockchain is in many ways similar to the Bitcoin blockchain,
 * although it does have some differences.
 * <p>
 * The main difference between Ethereum and Bitcoin with regard to the blockchain architecture
 * is that, unlike Bitcoin, Ethereum blocks contain a copy of both the transaction list
 * and the most recent state. Aside from that, two other values, the block number and
 * the difficulty, are also stored in the block.
 * </p>
 * The block validation algorithm in Ethereum is as follows:
 * <ol>
 * <li>Check if the previous block referenced exists and is valid.</li>
 * <li>Check that the timestamp of the block is greater than that of the referenced previous block and less than 15 minutes into the future</li>
 * <li>Check that the block number, difficulty, transaction root, uncle root and gas limit (various low-level Ethereum-specific concepts) are valid.</li>
 * <li>Check that the proof of work on the block is valid.</li>
 * <li>Let S[0] be the STATE_ROOT of the previous block.</li>
 * <li>Let TX be the block's transaction list, with n transactions.
 * For all in in 0...n-1, set S[i+1] = APPLY(S[i],TX[i]).
 * If any applications returns an error, or if the total gas consumed in the block
 * up until this point exceeds the GASLIMIT, return an error.</li>
 * <li>Let S_FINAL be S[n], but adding the block reward paid to the miner.</li>
 * <li>Check if S_FINAL is the same as the STATE_ROOT. If it is, the block is valid; otherwise, it is not valid.</li>
 * </ol>
 * See <a href="https://github.com/ethereum/wiki/wiki/White-Paper#blockchain-and-mining">Ethereum Whitepaper</a>
 *
 */

@Component
public class BlockChainImpl implements Blockchain, org.ethereum.facade.Blockchain {
    private static final Logger logger = LoggerFactory.getLogger("blockchain");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    @Autowired
    private Repository repository;

    @Autowired
    private BlockStore blockStore;

    @Autowired
    private ReceiptStore receiptStore;

    @Autowired
    private PendingState pendingState;

    @Autowired
    private EthereumListener listener;

    @Autowired
    private BlockValidator blockValidator;

    @Autowired
    private AdminInfo adminInfo;

    private volatile BlockChainStatus status = new BlockChainStatus(null, BigInteger.ZERO);
    private final Object connectLock = new Object();
    private final Object accessLock = new Object();
    private BlockExecutor blockExecutor;
    private BlockRecorder blockRecorder;
    private boolean isrsk;
    private boolean noValidation;

    public BlockChainImpl() {

    }

    @VisibleForTesting
    public BlockChainImpl(Repository repository, BlockStore blockStore, ReceiptStore receiptStore, PendingState pendingState, EthereumListener listener, AdminInfo adminInfo, BlockValidator blockValidator)
    {
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.pendingState = pendingState;
        this.listener = listener;
        this.adminInfo = adminInfo;

        this.blockValidator = blockValidator;

        init();
    }

    @PostConstruct
    public void init() {

        this.blockExecutor = new BlockExecutor(repository, this, blockStore, listener);

        if (this.pendingState != null)
            this.pendingState.start();
    }

    @Override
    public Repository getRepository() {
        return repository;
    }

    @Override
    public PendingState getPendingState() { return pendingState; }

    @VisibleForTesting
    public void setPendingState(PendingState pendingState) { this.pendingState = pendingState; }

    @Override
    public BlockStore getBlockStore() { return blockStore; }

    public EthereumListener getListener() { return listener; }

    public void setListener(EthereumListener listener) { this.listener = listener; }

    public BlockValidator getBlockValidator() { return blockValidator; }

    public AdminInfo getAdminInfo() { return adminInfo; }

    @VisibleForTesting
    public void setBlockValidator(BlockValidator validator) {
        this.blockValidator = validator;
    }

    @Override
    public long getSize() {
        return status.getBestBlock().getNumber() + 1;
    }

    /**
     * Try to add a block to a blockchain
     *
     * @param block        A block to try to add
     * @return IMPORTED_BEST if the block is the new best block
     *      IMPORTED_NOT_BEST if it was added to alternative chain
     *      NO_PARENT  the block parent is unknown yet
     *      INVALID_BLOCK   the block has invalida data/state
     *      EXISTS  the block was already processed
     */
    @Override
    public ImportResult tryToConnect(Block block) {
        if (block == null)
            return ImportResult.INVALID_BLOCK;
        
        block.seal();

        if (blockRecorder != null)
            blockRecorder.writeBlock(block);

        try {
            logger.info("Try connect block hash: {}, number: {}",
                    Hex.toHexString(block.getHash()).substring(0, 6),
                    block.getNumber());

            synchronized (connectLock) {
                logger.info("Start try connect");
                long saveTime = System.nanoTime();
                ImportResult result = internalTryToConnect(block);
                long totalTime = System.nanoTime() - saveTime;
                logger.info("block: num: [{}] hash: [{}], processed after: [{}]nano, result {}", block.getNumber(), block.getShortHash(), totalTime, result);
                return result;
            }
        } catch (Throwable th) {
            logger.error("Unexpected error: ", th);
            panicProcessor.panic("bcerror", th.toString());
            return ImportResult.INVALID_BLOCK;
        }
    }

    private ImportResult internalTryToConnect(Block block) {
        if (blockStore.getBlockByHash(block.getHash()) != null && !BigInteger.ZERO.equals(blockStore.getTotalDifficultyForHash(block.getHash()))) {
            logger.debug("Block already exist in chain hash: {}, number: {}",
                    Hex.toHexString(block.getHash()).substring(0, 6),
                    block.getNumber());

            return ImportResult.EXIST;
        }

        Block bestBlock;
        BigInteger bestTotalDifficulty;

        logger.info("get current state");

        // get current state
        synchronized (accessLock) {
            bestBlock = status.getBestBlock();
            bestTotalDifficulty = status.getTotalDifficulty();
        }

        Block parent;
        BigInteger parentTotalDifficulty;

        // Incoming block is child of current best block
        if (bestBlock == null || bestBlock.isParentOf(block)) {
            parent = bestBlock;
            parentTotalDifficulty = bestTotalDifficulty;
        }
        // else, Get parent AND total difficulty
        else {
            logger.info("get parent and total difficulty");
            parent = blockStore.getBlockByHash(block.getParentHash());

            if (parent == null)
                return ImportResult.NO_PARENT;

            parentTotalDifficulty = blockStore.getTotalDifficultyForHash(parent.getHash());

            if (parentTotalDifficulty == null || parentTotalDifficulty.equals(BigInteger.ZERO))
                return ImportResult.NO_PARENT;
        }

        // Validate incoming block before its processing
        if (!isValid(block)) {
            long blockNumber = block.getNumber();
            logger.warn("Invalid block with number: {}", blockNumber);
            panicProcessor.panic("invalidblock", String.format("Invalid block %s %s", blockNumber, Hex.toHexString(block.getHash())));
            return ImportResult.INVALID_BLOCK;
        }

        BlockResult result = null;

        if (parent != null) {
            long saveTime = System.nanoTime();
            logger.info("execute start");

            if (this.noValidation)
                result = blockExecutor.executeAll(block, parent.getStateRoot());
            else
                result = blockExecutor.execute(block, parent.getStateRoot(), false);

            logger.info("execute done");

            boolean isValid = noValidation ? true : blockExecutor.validate(block, result);

            logger.info("validate done");

            if (!isValid)
                return ImportResult.INVALID_BLOCK;

            long totalTime = System.nanoTime() - saveTime;

            if (adminInfo != null)
                adminInfo.addBlockExecTime(totalTime);

            logger.info("block: num: [{}] hash: [{}], executed after: [{}]nano", block.getNumber(), block.getShortHash(), totalTime);
        }

        // the new accumulated difficulty
        BigInteger totalDifficulty = parentTotalDifficulty.add(block.getCumulativeDifficulty());
        logger.info("TD: updated to {}", totalDifficulty);

        // It is the new best block
        if (totalDifficulty.compareTo(status.getTotalDifficulty()) > 0) {
            if (bestBlock != null && !bestBlock.isParentOf(block)) {
                logger.info("Rebranching: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}", bestBlock.getShortHash(), block.getShortHash(), bestBlock.getNumber(), block.getNumber(), status.getTotalDifficulty().toString(), totalDifficulty.toString());
                BlockFork fork = new BlockFork();
                fork.calculate(bestBlock, block, blockStore);
                Metrics.rebranch(bestBlock, block, fork.getNewBlocks().size() + fork.getOldBlocks().size());
                blockStore.reBranch(block);
            }

            logger.trace("Start switchToBlockChain");
            switchToBlockChain(block, totalDifficulty);
            logger.trace("Start saveReceipts");
            saveReceipts(block, result);
            logger.trace("Start processBest");
            processBest(block);
            logger.trace("Start onBlock");
            onBlock(block, result);
            logger.trace("Start flushData");
            flushData();

            logger.trace("Better block {} {}", block.getNumber(), block.getShortHash());

            logger.debug("block added to the blockChain: index: [{}]", block.getNumber());
            if (block.getNumber() % 100 == 0)
                logger.info("*** Last block added [ #{} ]", block.getNumber());

            return ImportResult.IMPORTED_BEST;
        }
        // It is not the new best block
        else {
            if (bestBlock != null && !bestBlock.isParentOf(block))
                logger.info("No rebranch: {} ~> {} From block {} ~> {} Difficulty {} Challenger difficulty {}", bestBlock.getShortHash(), block.getShortHash(), bestBlock.getNumber(), block.getNumber(), status.getTotalDifficulty().toString(), totalDifficulty.toString());

            logger.trace("Start extendAlternativeBlockChain");
            extendAlternativeBlockChain(block, totalDifficulty);
            logger.trace("Start saveReceipts");
            saveReceipts(block, result);
            logger.trace("Start onBlock");
            onBlock(block, result);
            logger.trace("Start flushData");
            flushData();

            if (bestBlock != null && block.getNumber() > bestBlock.getNumber())
                logger.warn("Strange block number state");

            logger.trace("Block not imported {} {}", block.getNumber(), block.getShortHash());

            return ImportResult.IMPORTED_NOT_BEST;
        }
    }

    @Override
    public BlockChainStatus getStatus() {
        return status;
    }

    /**
     * Change the blockchain status, to a new best block with difficulty
     *
     * @param block        The new best block
     * @param totalDifficulty   The total difficulty of the new blockchain
     */
    @Override
    public void setStatus(Block block, BigInteger totalDifficulty) {
        synchronized (accessLock) {
            status = new BlockChainStatus(block, totalDifficulty);
            blockStore.saveBlock(block, totalDifficulty, true);
            repository.syncToRoot(block.getStateRoot());
        }
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return blockStore.getBlockByHash(hash);
    }

    @Override
    public void setExitOn(long exitOn) {

    }

    @Override
    public boolean isBlockExist(byte[] hash) {
        return blockStore.isBlockExist(hash);
    }

    @Override
    public List<BlockHeader> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse) {
        return null;
    }

    @Override
    public List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes) {
        return null;
    }

    @Override
    public List<Block> getBlocksByNumber(long number) {
        return blockStore.getChainBlocksByNumber(number);
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long number) {
        synchronized (accessLock) {
            return this.blockStore.getBlocksInformationByNumber(number);
        }
    }

    @Override
    public void removeBlocksByNumber(long number) {
        List<Block> blocks = this.getBlocksByNumber(number);

        for (Block block : blocks)
            blockStore.removeBlock(block);
    }

    public Block getBlockByNumber(long number) { return blockStore.getChainBlockByNumber(number); }

    @Override
    public void setBestBlock(Block block) {
        this.setStatus(block, status.getTotalDifficulty());
    }

    @Override
    public Block getBestBlock() {
        return this.status.getBestBlock();
    }

    @Override
    public boolean isRsk() {
        return this.isrsk;
    }

    @Override
    public void setRsk(boolean isrsk) {
        this.isrsk = isrsk;
    }

    public void setNoValidation(boolean noValidation) {
        this.noValidation = noValidation;
    }

    /**
     * Returns transaction info by hash
     *
     * @param hash      the hash of the transaction
     * @return transaction info, null if the transaction does not exist
     */
    @Override
    public TransactionInfo getTransactionInfo(byte[] hash) {
        TransactionInfo txInfo = receiptStore.get(hash);

        if (txInfo == null)
            return null;

        Transaction tx = this.getBlockByHash(txInfo.getBlockHash()).getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        return txInfo;
    }

    @Override
    public void close() {

    }

    @Override
    public BigInteger getTotalDifficulty() {
        return status.getTotalDifficulty();
    }

    @Override
    public void setTotalDifficulty(BigInteger totalDifficulty) {
        setStatus(status.getBestBlock(), totalDifficulty);
    }

    @Override
    public byte[] getBestBlockHash() {
        return status.getBestBlock().getHash();
    }

    @Override
    public void setBlockRecorder(BlockRecorder blockRecorder) {
        this.blockRecorder = blockRecorder;
    }

    @Override
    public ReceiptStore getReceiptStore() { return receiptStore; }

    private void switchToBlockChain(Block block, BigInteger totalDifficulty) {
        synchronized (accessLock) {
            storeBlock(block, totalDifficulty, true);
            status = new BlockChainStatus(block, totalDifficulty);
            repository.syncToRoot(block.getStateRoot());
        }
    }

    private void extendAlternativeBlockChain(Block block, BigInteger totalDifficulty) {
        storeBlock(block, totalDifficulty, false);
    }

    private void storeBlock(Block block, BigInteger totalDifficulty, boolean inBlockChain) {
        blockStore.saveBlock(block, totalDifficulty, inBlockChain);
        logger.info("Block saved: number: {}, hash: {}, TD: {}",
                block.getNumber(), block.getShortHash(), totalDifficulty);
    }

    private void saveReceipts(Block block, BlockResult result) {
        if (result == null)
            return;

        if (result.getTransactionReceipts().isEmpty())
            return;

        receiptStore.saveMultiple(block.getHash(), result.getTransactionReceipts());
    }

    private void processBest(final Block block) {
        EventDispatchThread.invokeLater(() -> pendingState.processBest(block));
    }

    private void onBlock(Block block, BlockResult result) {
        if (result != null && listener != null) {
            listener.trace(String.format("Block chain size: [ %d ]", this.getSize()));
            listener.onBlock(block, result.getTransactionReceipts());
        }
    }

    private boolean isValid(Block block) {
        if (block.isGenesis())
            return true;
        return blockValidator.isValid(block);
    }


    // Rolling counter that helps doing flush every RskSystemProperties.RSKCONFIG.flushNumberOfBlocks() flush attempts
    // We did this because flush is slow, and doing flush for every block degrades the node performance.
    private int nFlush = 0;

    private void flushData() {
        if (RskSystemProperties.RSKCONFIG.isFlushEnabled() && nFlush == 0)  {
            long saveTime = System.nanoTime();
            repository.flush();
            long totalTime = System.nanoTime() - saveTime;
            logger.info("repository flush: [{}]nano", totalTime);
            saveTime = System.nanoTime();
            blockStore.flush();
            totalTime = System.nanoTime() - saveTime;
            logger.info("blockstore flush: [{}]nano", totalTime);
        }
        nFlush++;
        nFlush = nFlush % RskSystemProperties.RSKCONFIG.flushNumberOfBlocks();
    }

    public static byte[] calcTxTrie(List<Transaction> transactions) {
        return Block.getTxTrie(transactions).getHash();
    }

    public static byte[] calcReceiptsTrie(List<TransactionReceipt> receipts) {
        Trie receiptsTrie = new TrieImpl();

        if (receipts == null || receipts.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (int i = 0; i < receipts.size(); i++)
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());

        return receiptsTrie.getHash();
    }
}
