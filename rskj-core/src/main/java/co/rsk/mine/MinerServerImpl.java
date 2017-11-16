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

package co.rsk.mine;

import co.rsk.config.MiningConfig;
import co.rsk.config.RskMiningConstants;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.crypto.Sha3Hash;
import co.rsk.net.BlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.DifficultyUtils;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.rpc.TypeConverter;
import co.rsk.validators.ProofOfWorkRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.ethereum.util.BIUtil.toBI;

/**
 * The MinerServer provides support to components that perform the actual mining.
 * It builds blocks to mine and publishes blocks once a valid nonce was found by the miner.
 * @author Oscar Guindzberg
 */

@Component("MinerServer")
public class MinerServerImpl implements MinerServer {
    private static final long DELAY_BETWEEN_BUILD_BLOCKS_MS = TimeUnit.MINUTES.toMillis(1);

    private static final Logger logger = LoggerFactory.getLogger("minerserver");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final int CACHE_SIZE = 20;

    private final Ethereum ethereum;
    private final BlockStore blockStore;
    private final Blockchain blockchain;
    private final PendingState pendingState;
    private final BlockExecutor executor;

    @GuardedBy("lock")
    private LinkedHashMap<Sha3Hash, Block> blocksWaitingforPoW;

    @GuardedBy("lock")
    private Sha3Hash latestblockHashWaitingforPoW;
    @GuardedBy("lock")
    private Sha3Hash latestParentHash;
    @GuardedBy("lock")
    private Block latestBlock;
    @GuardedBy("lock")
    private long latestPaidFeesWithNotify;
    @GuardedBy("lock")
    private volatile MinerWork currentWork; // This variable can be read at anytime without the lock.
    private final Object lock = new Object();

    private final byte[] coinbaseAddress;

    private final BigInteger minerMinGasPriceTarget;
    private final double minFeesNotifyInDollars;
    private final double gasUnitInDollars;

    private ProofOfWorkRule powRule;

    private MiningConfig miningConfig;

    private BlockValidationRule validationRules;

    private long timeAdjustment;

    @Autowired
    public MinerServerImpl(Ethereum ethereum, Blockchain blockchain, BlockStore blockStore, PendingState pendingState, Repository repository, MiningConfig miningConfig, @Qualifier("minerServerBlockValidation") BlockValidationRule validationRules) {
        this.ethereum = ethereum;
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.pendingState = pendingState;
        this.miningConfig = miningConfig;
        this.validationRules = validationRules;

        executor = new BlockExecutor(repository, blockchain, blockStore, null);

        blocksWaitingforPoW = new LinkedHashMap<Sha3Hash, Block>(CACHE_SIZE) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Sha3Hash, Block> eldest) {
                return size() > CACHE_SIZE;
            }
        };

        latestPaidFeesWithNotify = 0;
        latestParentHash = null;
        coinbaseAddress = miningConfig.getCoinbaseAddress();
        minFeesNotifyInDollars = miningConfig.getMinFeesNotifyInDollars();
        gasUnitInDollars = miningConfig.getGasUnitInDollars();
        minerMinGasPriceTarget = toBI(miningConfig.getMinGasPriceTarget());
        powRule = new ProofOfWorkRule();
    }

    @VisibleForTesting
    public Map<Sha3Hash, Block> getBlocksWaitingforPoW() {
        return blocksWaitingforPoW;
    }

    @Override
    public void start() {
        ethereum.addListener(new NewBlockListener());
        buildBlockToMine(blockchain.getBestBlock(), false);
        new Timer("Refresh work for mining").schedule(new RefreshBlock(), DELAY_BETWEEN_BUILD_BLOCKS_MS, DELAY_BETWEEN_BUILD_BLOCKS_MS);
    }

    @Override
    public void submitBitcoinBlock(String blockHashForMergedMining, co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock) {
        logger.debug("Received block with hash " + blockHashForMergedMining + " for merged mining");
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningBlock.getTransactions().get(0);
        co.rsk.bitcoinj.core.PartialMerkleTree bitcoinMergedMiningMerkleBranch = getBitcoinMergedMerkleBranch(bitcoinMergedMiningBlock);

        Block newBlock;
        Sha3Hash key = new Sha3Hash(TypeConverter.removeZeroX(blockHashForMergedMining));

        synchronized (lock) {
            Block workingBlock = blocksWaitingforPoW.get(key);

            if (workingBlock == null) {
                logger.warn("Cannot publish block, could not find hash " + blockHashForMergedMining + " in the cache");
                return;
            }

            // just in case, remove all references to this block.
            if (latestBlock == workingBlock) {
                latestBlock = null;
                latestblockHashWaitingforPoW = null;
                currentWork = null;
            }

            // clone the block
            newBlock = workingBlock.cloneBlock();

            logger.debug("blocksWaitingforPoW size " + blocksWaitingforPoW.size());
        }

        logger.info("Received block {} {}", newBlock.getNumber(), Hex.toHexString(newBlock.getHash()));

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());
        newBlock.setBitcoinMergedMiningCoinbaseTransaction(compressCoinbase(bitcoinMergedMiningCoinbaseTransaction.bitcoinSerialize()));
        newBlock.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleBranch.bitcoinSerialize());
        newBlock.seal();

        if (!isValid(newBlock)) {
            logger.error("Invalid block supplied by miner " + " : " + newBlock.getShortHash() + " " + newBlock.getShortHashForMergedMining() + " at height " + newBlock.getNumber() + ". Hash: " + newBlock.getShortHash());
        } else {
            ImportResult importResult = ethereum.addNewMinedBlock(newBlock);
            logger.info("Mined block import result is " + importResult + " : " + newBlock.getShortHash() + " " + newBlock.getShortHashForMergedMining() + " at height " + newBlock.getNumber() + ". Hash: " + newBlock.getShortHash());
        }
    }

    private boolean isValid(Block block) {
        try {
            if (!powRule.isValid(block)) {
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to validate PoW from block {}: {}", block.getShortHash(), e);
            return false;
        }
        return true;
    }

    public static byte[] compressCoinbase(byte[] bitcoinMergedMiningCoinbaseTransactionSerialized) {
        int rskTagPosition = Collections.lastIndexOfSubList(java.util.Arrays.asList(ArrayUtils.toObject(bitcoinMergedMiningCoinbaseTransactionSerialized)),
                java.util.Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG)));
        int remainingByteCount = bitcoinMergedMiningCoinbaseTransactionSerialized.length - rskTagPosition - RskMiningConstants.RSK_TAG.length - RskMiningConstants.BLOCK_HEADER_HASH_SIZE;
        if (remainingByteCount > RskMiningConstants.MAX_BYTES_AFTER_MERGED_MINING_HASH) {
            throw new IllegalArgumentException("More than 128 bytes after ROOOTSTOCK tag");
        }
        int sha256Blocks = rskTagPosition / 64;
        int bytesToHash = sha256Blocks * 64;
        SHA256Digest digest = new SHA256Digest();
        digest.update(bitcoinMergedMiningCoinbaseTransactionSerialized, 0, bytesToHash);
        byte[] hashedContent = digest.getEncodedState();
        byte[] trimmedHashedContent = new byte[RskMiningConstants.MIDSTATE_SIZE_TRIMMED];
        System.arraycopy(hashedContent, 8, trimmedHashedContent, 0, RskMiningConstants.MIDSTATE_SIZE_TRIMMED);
        byte[] unHashedContent = new byte[bitcoinMergedMiningCoinbaseTransactionSerialized.length - bytesToHash];
        System.arraycopy(bitcoinMergedMiningCoinbaseTransactionSerialized, bytesToHash, unHashedContent, 0, unHashedContent.length);
        return Arrays.concatenate(trimmedHashedContent, unHashedContent);
    }

    /**
     * getBitcoinMergedMerkleBranch returns the Partial Merkle Branch needed to validate that the coinbase tx
     * is part of the Merkle Tree.
     *
     * @param bitcoinMergedMiningBlock the bitcoin block that includes all the txs.
     * @return A Partial Merkle Branch in which you can validate the coinbase tx.
     */
    public static co.rsk.bitcoinj.core.PartialMerkleTree getBitcoinMergedMerkleBranch(co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock) {
        List<co.rsk.bitcoinj.core.BtcTransaction> txs = bitcoinMergedMiningBlock.getTransactions();
        List<co.rsk.bitcoinj.core.Sha256Hash> txHashes = new ArrayList<>(txs.size());
        for (co.rsk.bitcoinj.core.BtcTransaction tx : txs) {
            txHashes.add(tx.getHash());
        }
        /**
         *  We need to convert the txs to a bitvector to choose which ones
         *  will be included in the Partial Merkle Tree.
         *
         *  We need txs.size() / 8 bytes to represent this vector.
         *  The coinbase tx is the first one of the txs so we set the first bit to 1.
         */
        byte[] bitvector = new byte[(int) Math.ceil(txs.size() / 8.0)];
        co.rsk.bitcoinj.core.Utils.setBitLE(bitvector, 0);
        return co.rsk.bitcoinj.core.PartialMerkleTree.buildFromLeaves(bitcoinMergedMiningBlock.getParams(), bitvector, txHashes);
    }

    @Override
    public byte[] getCoinbaseAddress() {
        return coinbaseAddress;
    }

    /**
     * getWork returns the latest MinerWork for miners. Subsequent calls to this function with no new work will return
     * currentWork with the notify flag turned off. (they will be different objects too).
     *
     * @return the latest MinerWork available.
     */
    @Override
    public MinerWork getWork() {
        MinerWork work = currentWork;
        if (work == null) {
            return null;
        }
        
        if (work.getNotify()) {
            /**
             * Set currentWork.notify to false for the next time this function is called.
             * By doing it this way, we avoid taking the lock every time, we just take it once per MinerWork.
             * We have to take the lock to reassign currentWork, but it might have happened that
             * the currentWork got updated when we acquired the lock. In that case, we should just return the new
             * currentWork, regardless of what it is.
             */
            synchronized (lock) {
                if (currentWork != work ||  currentWork == null) {
                    return currentWork;
                }
                currentWork = new MinerWork(currentWork.getBlockHashForMergedMining(), currentWork.getTarget(),
                        Long.valueOf(currentWork.getFeesPaidToMiner()),
                        false, currentWork.getParentBlockHash());
            }
        }
        return work;
    }

    @VisibleForTesting
    public void setWork(MinerWork work) {
        this.currentWork = work;
    }

    public MinerWork updateGetWork(@Nonnull final Block block, @Nonnull final boolean notify) {
        Sha3Hash blockMergedMiningHash = new Sha3Hash(block.getHashForMergedMining());

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficultyBI());
        byte[] targetUnknownLengthArray = targetBI.toByteArray();
        byte[] targetArray = new byte[32];
        System.arraycopy(targetUnknownLengthArray, 0, targetArray, 32 - targetUnknownLengthArray.length, targetUnknownLengthArray.length);

        logger.debug("Sending work for merged mining. Hash: " + block.getShortHashForMergedMining());
        return new MinerWork(TypeConverter.toJsonHex(blockMergedMiningHash.getBytes()), TypeConverter.toJsonHex(targetArray), block.getFeesPaidToMiner(), notify, TypeConverter.toJsonHex(block.getParentHash()));
    }

    /**
     * buildBlockToMine creates a block to mine based on the given block as parent.
     *
     * @param newBlockParent              the new block parent.
     * @param createCompetitiveBlock used for testing.
     */
    @Override
    public void buildBlockToMine(@Nonnull Block newBlockParent, boolean createCompetitiveBlock) {
        // See BlockChainImpl.calclBloom() if blocks has txs
        if (createCompetitiveBlock) {
            // Just for testing, mine on top of bestblock's parent
            newBlockParent = blockchain.getBlockByHash(newBlockParent.getParentHash());
        }

        logger.info("Starting block to mine from parent {}", newBlockParent.getNumber() + " " + Hex.toHexString(newBlockParent.getHash()));

        List<BlockHeader> uncles;
        if (blockStore != null) {
            uncles = FamilyUtils.getUnclesHeaders(blockStore, newBlockParent.getNumber() + 1, newBlockParent.getHash(), this.miningConfig.getUncleGenerationLimit());
        } else {
            uncles = new ArrayList<>();
        }

        if (uncles.size() > this.miningConfig.getUncleListLimit()) {
            uncles = uncles.subList(0, this.miningConfig.getUncleListLimit());
        }

        final List<Transaction> txsToRemove = new ArrayList<>();

        BigInteger minimumGasPrice = new MinimumGasPriceCalculator().calculate(newBlockParent.getMinGasPriceAsInteger(), minerMinGasPriceTarget);
        final List<Transaction> txs = getTransactions(txsToRemove, newBlockParent, minimumGasPrice);

        final Block newBlock = createBlock(newBlockParent, uncles, txs, minimumGasPrice);

        removePendingTransactions(txsToRemove);
        executor.executeAndFill(newBlock, newBlockParent);

        synchronized (lock) {
            Sha3Hash parentHash = new Sha3Hash(newBlockParent.getHash());
            boolean notify = this.getNotify(newBlock, parentHash);

            if (notify) {
                latestPaidFeesWithNotify = newBlock.getFeesPaidToMiner();
            }

            latestParentHash = parentHash;
            currentWork = updateGetWork(newBlock, notify);

            latestblockHashWaitingforPoW = new Sha3Hash(newBlock.getHashForMergedMining());
            latestBlock = newBlock;
            blocksWaitingforPoW.put(latestblockHashWaitingforPoW, latestBlock);

            logger.debug("blocksWaitingForPoW size " + blocksWaitingforPoW.size());
        }

        logger.debug("Built block " + newBlock.getShortHashForMergedMining() + ". Parent " + newBlockParent.getShortHashForMergedMining());
        for (BlockHeader uncleHeader : uncles) {
            logger.debug("With uncle " + uncleHeader.getShortHashForMergedMining());
        }
    }

    /**
     * getNotifies determines whether miners should be notified or not. (Used for mining pools).
     *
     * @param block      the block to mine.
     * @param parentHash block's parent hash.
     * @return true if miners should be notified about this new block to mine.
     */
    @GuardedBy("lock")
    private boolean getNotify(Block block, Sha3Hash parentHash) {
        boolean notify;
        long feesPaidToMiner = block.getFeesPaidToMiner();

        notify = !parentHash.equals(latestParentHash);
        notify = notify || (feesPaidToMiner > (latestPaidFeesWithNotify * (100 + RskMiningConstants.NOTIFY_FEES_PERCENTAGE_INCREASE) / 100)) && (feesPaidToMiner * gasUnitInDollars) >= minFeesNotifyInDollars;

        return notify;
    }

    @Override
    public long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000 + this.timeAdjustment;
    }

    @Override
    public long increaseTime(long seconds) {
        if (seconds <= 0)
            return this.timeAdjustment;

        this.timeAdjustment += seconds;

        return this.timeAdjustment;
    }

    private void removePendingTransactions(List<Transaction> transactions) {
        if (transactions != null)
            for (Transaction tx : transactions)
                logger.info("Removing transaction {}", Hex.toHexString(tx.getHash()));

        pendingState.clearPendingState(transactions);
        pendingState.clearWire(transactions);
    }

    private List<Transaction> getTransactions(List<Transaction> txsToRemove, Block parent, BigInteger minGasPrice) {

        logger.info("Starting getTransactions");

        List<Transaction> txs = new MinerUtils().getAllTransactions(pendingState);
        logger.debug("txsList size {}", txs.size());

        Transaction remascTx = new RemascTransaction(parent.getNumber() + 1);
        txs.add(remascTx);

        Map<ByteArrayWrapper, BigInteger> accountNonces = new HashMap<>();

        Repository originalRepo = blockchain.getRepository().getSnapshotTo(parent.getStateRoot());

        return new MinerUtils().filterTransactions(txsToRemove, txs, accountNonces, originalRepo, minGasPrice);
    }

    class NewBlockListener extends EthereumListenerAdapter {

        @Override
        /**
         * onBlock checks if we have to mine over a new block. (Only if the blockchain's best block changed).
         * This method will be called on every block added to the blockchain, even if it doesn't go to the best chain.
         * TODO(???): It would be cleaner to just send this when the blockchain's best block changes.
         * **/
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            if (isSyncing())
                return;

            logger.trace("Start onBlock");
            Block bestBlock = blockchain.getBestBlock();
            MinerWork work = currentWork;
            String bestBlockHash = TypeConverter.toJsonHex(bestBlock.getHash());

            if (work == null || !work.getParentBlockHash().equals(bestBlockHash)) {
                logger.debug("There is a new best block: {}, number: {}", bestBlock.getShortHashForMergedMining(), bestBlock.getNumber());
                buildBlockToMine(bestBlock, false);
            } else {
                logger.debug("New block arrived but there is no need to build a new block to mine: " + block.getShortHashForMergedMining());
            }

            logger.trace("End onBlock");
        }

        private boolean isSyncing() {
            BlockProcessor processor = ethereum.getWorldManager().getNodeBlockProcessor();

            if (processor == null)
                return false;

            return processor.isSyncingBlocks();
        }
    }

    private BlockHeader createHeader(Block newBlockParent, List<BlockHeader> uncles, List<Transaction> txs, BigInteger minimumGasPrice) {
        final byte[] unclesListHash = HashUtil.sha3(BlockHeader.getUnclesEncodedEx(uncles));

        final long timestampSeconds = this.getCurrentTimeInSeconds();

        // Set gas limit before executing block
        BigInteger minGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getMininimum());
        BigInteger targetGasLimit = BigInteger.valueOf(miningConfig.getGasLimit().getTarget());
        BigInteger parentGasLimit = new BigInteger(1, newBlockParent.getGasLimit());
        BigInteger gasUsed = BigInteger.valueOf(newBlockParent.getGasUsed());
        boolean forceLimit = miningConfig.getGasLimit().isTargetForced();
        BigInteger gasLimit = new GasLimitCalculator().calculateBlockGasLimit(parentGasLimit,
                gasUsed, minGasLimit, targetGasLimit, forceLimit);

        final BlockHeader newHeader = new BlockHeader(newBlockParent.getHash(),
                unclesListHash,
                coinbaseAddress,
                new Bloom().getData(),
                new byte[]{1},
                newBlockParent.getNumber() + 1,
                gasLimit.toByteArray(),
                0,
                timestampSeconds,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                minimumGasPrice.toByteArray(),
                CollectionUtils.size(uncles)
        );
        newHeader.setDifficulty(newHeader.calcDifficulty(newBlockParent.getHeader()).toByteArray());
        newHeader.setTransactionsRoot(Block.getTxTrie(txs).getHash());
        return newHeader;
    }

    private Block createBlock(Block newBlockParent, List<BlockHeader> uncles, List<Transaction> txs, BigInteger minimumGasPrice) {
        final BlockHeader newHeader = createHeader(newBlockParent, uncles, txs, minimumGasPrice);
        final Block newBlock = new Block(newHeader, txs, uncles);
        return validationRules.isValid(newBlock) ? newBlock : new Block(newHeader, txs, null);
    }

    /**
     * RefreshBlocks rebuilds the block to mine.
     */
    private class RefreshBlock extends TimerTask {
        @Override
        public void run() {
            Block bestBlock = blockchain.getBestBlock();
            try {
                buildBlockToMine(bestBlock, false);
            } catch (Throwable th) {
                logger.error("Unexpected error: {}", th);
                panicProcessor.panic("mserror", th.getMessage());
            }
        }
    }
}
