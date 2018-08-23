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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.config.MiningConfig;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.DifficultyUtils;
import co.rsk.validators.ProofOfWorkRule;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The MinerServer provides support to components that perform the actual mining.
 * It builds blocks to mine and publishes blocks once a valid nonce was found by the miner.
 *
 * @author Oscar Guindzberg
 */

@Component("MinerServer")
public class MinerServerImpl implements MinerServer {
    private static final long DELAY_BETWEEN_BUILD_BLOCKS_MS = TimeUnit.MINUTES.toMillis(1);

    private static final Logger logger = LoggerFactory.getLogger("minerserver");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final int CACHE_SIZE = 20;

    private final Ethereum ethereum;
    private final Blockchain blockchain;
    private final ProofOfWorkRule powRule;
    private final BlockToMineBuilder builder;
    private final BlockchainNetConfig blockchainConfig;

    private boolean isFallbackMining;
    private int fallbackBlocksGenerated;
    private Timer fallbackMiningTimer;
    private Timer refreshWorkTimer;
    private int secsBetweenFallbackMinedBlocks;
    private NewBlockListener blockListener;

    private boolean started;

    private byte[] extraData;

    @GuardedBy("lock")
    private LinkedHashMap<Keccak256, Block> blocksWaitingforPoW;
    @GuardedBy("lock")
    private Keccak256 latestParentHash;
    @GuardedBy("lock")
    private Block latestBlock;
    @GuardedBy("lock")
    private Coin latestPaidFeesWithNotify;
    @GuardedBy("lock")
    private volatile MinerWork currentWork; // This variable can be read at anytime without the lock.
    private final Object lock = new Object();

    private final RskAddress coinbaseAddress;
    private final BigDecimal minFeesNotifyInDollars;
    private final BigDecimal gasUnitInDollars;

    private final BlockProcessor nodeBlockProcessor;
    private final DifficultyCalculator difficultyCalculator;

    private boolean autoSwitchBetweenNormalAndFallbackMining;
    private boolean fallbackMiningScheduled;
    private final RskSystemProperties config;

    @Autowired
    public MinerServerImpl(
            RskSystemProperties config,
            Ethereum ethereum,
            Blockchain blockchain,
            BlockProcessor nodeBlockProcessor,
            DifficultyCalculator difficultyCalculator,
            ProofOfWorkRule powRule,
            BlockToMineBuilder builder,
            MiningConfig miningConfig) {
        this.config = config;
        this.ethereum = ethereum;
        this.blockchain = blockchain;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.difficultyCalculator = difficultyCalculator;
        this.powRule = powRule;
        this.builder = builder;
        this.blockchainConfig = config.getBlockchainConfig();

        blocksWaitingforPoW = createNewBlocksWaitingList();

        latestPaidFeesWithNotify = Coin.ZERO;
        latestParentHash = null;
        coinbaseAddress = miningConfig.getCoinbaseAddress();
        minFeesNotifyInDollars = BigDecimal.valueOf(miningConfig.getMinFeesNotifyInDollars());
        gasUnitInDollars = BigDecimal.valueOf(miningConfig.getMinFeesNotifyInDollars());

        // One more second to force continuous reduction in difficulty
        // TODO(mc) move to MiningConstants

        // It's not so important to add one because the timer has an average delay of 1 second.
        secsBetweenFallbackMinedBlocks =
                config.getAverageFallbackMiningTime();
        // default
        if (secsBetweenFallbackMinedBlocks == 0) {
            secsBetweenFallbackMinedBlocks = (blockchainConfig.getCommonConstants().getDurationLimit());
        }
        autoSwitchBetweenNormalAndFallbackMining = !blockchainConfig.getCommonConstants().getFallbackMiningDifficulty().equals(BlockDifficulty.ZERO);
    }

    // This method is used for tests
    public void setSecsBetweenFallbackMinedBlocks(int m) {
        secsBetweenFallbackMinedBlocks = m;
    }

    private LinkedHashMap<Keccak256, Block> createNewBlocksWaitingList() {
        return new LinkedHashMap<Keccak256, Block>(CACHE_SIZE) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Keccak256, Block> eldest) {
                return size() > CACHE_SIZE;
            }
        };

    }

    public int getFallbackBlocksGenerated() {
        return fallbackBlocksGenerated;
    }

    public boolean isFallbackMining() {
        return isFallbackMining;
    }

    public void setFallbackMiningState() {
        if (isFallbackMining) {
            // setFallbackMining() can be called before start
            if (started) {
                if (fallbackMiningTimer == null) {
                    fallbackMiningTimer = new Timer("Private mining timer");
                }
                if (!fallbackMiningScheduled) {
                    fallbackMiningTimer.schedule(new FallbackMiningTask(), 1000, 1000);
                    fallbackMiningScheduled = true;
                }
                // Because the Refresh occurs only once every minute,
                // we need to create at least one first block to mine
                Block bestBlock = blockchain.getBestBlock();
                buildBlockToMine(bestBlock, false);
            } else {
                if (fallbackMiningTimer != null) {
                    fallbackMiningTimer.cancel();
                    fallbackMiningTimer = null;
                }
            }
        } else {
            fallbackMiningScheduled = false;
            if (fallbackMiningTimer != null) {
                fallbackMiningTimer.cancel();
                fallbackMiningTimer = null;
            }
        }
    }

    @Override
    public void setAutoSwitchBetweenNormalAndFallbackMining(boolean p) {
        autoSwitchBetweenNormalAndFallbackMining = p;
    }

    public void setFallbackMining(boolean p) {
        synchronized (lock) {
            if (isFallbackMining == p) {
                return;
            }

            isFallbackMining = p;
            setFallbackMiningState();

        }

    }

    @VisibleForTesting
    public Map<Keccak256, Block> getBlocksWaitingforPoW() {
        return blocksWaitingforPoW;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }

        synchronized (lock) {
            started = false;
            ethereum.removeListener(blockListener);
            refreshWorkTimer.cancel();
            refreshWorkTimer = null;
            setFallbackMiningState();
        }
    }

    @Override
    public void start() {
        if (started) {
            return;
        }

        synchronized (lock) {
            started = true;
            blockListener = new NewBlockListener();
            ethereum.addListener(blockListener);
            buildBlockToMine(blockchain.getBestBlock(), false);

            if (refreshWorkTimer != null) {
                refreshWorkTimer.cancel();
            }

            refreshWorkTimer = new Timer("Refresh work for mining");
            refreshWorkTimer.schedule(new RefreshBlock(), DELAY_BETWEEN_BUILD_BLOCKS_MS, DELAY_BETWEEN_BUILD_BLOCKS_MS);
            setFallbackMiningState();
        }
    }

    @Nullable
    public static byte[] readFromFile(File aFile) {
        try {
            try (FileInputStream fis = new FileInputStream(aFile)) {
                byte[] array = new byte[1024];
                int r = fis.read(array);
                array = java.util.Arrays.copyOfRange(array, 0, r);
                fis.close();
                return array;
            }
        } catch (IOException e) {
            return null;
        }
    }

    static byte[] privKey0;
    static byte[] privKey1;

    @Override
    public boolean generateFallbackBlock() {
        Block newBlock;
        synchronized (lock) {
            if (latestBlock == null) {
                return false;
            }

            // Iterate and find a block that can be privately mined.
            Block workingBlock = latestBlock;
            newBlock = workingBlock.cloneBlock();
        }

        boolean isEvenBlockNumber = (newBlock.getNumber() % 2) == 0;


        String path = config.fallbackMiningKeysDir();

        if (privKey0 == null) {
            privKey0 = readFromFile(new File(path, "privkey0.bin"));
        }

        if (privKey1 == null) {
            privKey1 = readFromFile(new File(path, "privkey1.bin"));
        }

        if (!isEvenBlockNumber && privKey1 == null) {
            return false;
        }

        if (isEvenBlockNumber && privKey0 == null) {
            return false;
        }

        ECKey privateKey;

        if (isEvenBlockNumber) {
            privateKey = ECKey.fromPrivate(privKey0);
        } else {
            privateKey = ECKey.fromPrivate(privKey1);
        }

        //
        // Set the timestamp now to control mining interval better
        //
        BlockHeader newHeader = newBlock.getHeader();

        newHeader.setTimestamp(this.getCurrentTimeInSeconds());
        Block parentBlock = blockchain.getBlockByHash(newHeader.getParentHash().getBytes());
        newHeader.setDifficulty(
                difficultyCalculator.calcDifficulty(newHeader, parentBlock.getHeader()));

        // fallback mining marker
        newBlock.setExtraData(new byte[]{42});
        byte[] signature = fallbackSign(newBlock.getHashForMergedMining(), privateKey);

        newBlock.setBitcoinMergedMiningHeader(signature);

        newBlock.seal();

        if (!isValid(newBlock)) {
            String message = "Invalid fallback block supplied by miner: " + newBlock.getShortHash() + " " + newBlock.getShortHashForMergedMining() + " at height " + newBlock.getNumber();
            logger.error(message);
            return false;
        } else {
            latestBlock = null; // never reuse if block is valid
            ImportResult importResult = ethereum.addNewMinedBlock(newBlock);
            fallbackBlocksGenerated++;
            logger.info("Mined block import result is {}: {} {} at height {}", importResult, newBlock.getShortHash(), newBlock.getShortHashForMergedMining(), newBlock.getNumber());
            return importResult.isSuccessful();
        }

    }

    private byte[] fallbackSign(byte[] hash, ECKey privKey) {
        ECKey.ECDSASignature signature = privKey.sign(hash);

        byte vdata = signature.v;
        byte[] rdata = signature.r.toByteArray();
        byte[] sdata = signature.s.toByteArray();

        byte[] vencoded = RLP.encodeByte(vdata);
        byte[] rencoded = RLP.encodeElement(rdata);
        byte[] sencoded = RLP.encodeElement(sdata);

        return RLP.encodeList(vencoded, rencoded, sencoded);
    }

    @Override
    public SubmitBlockResult submitBitcoinBlockPartialMerkle(
            String blockHashForMergedMining,
            BtcBlock blockWithHeaderOnly,
            BtcTransaction coinbase,
            List<String> merkleHashes,
            int blockTxnCount) {
        logger.debug("Received merkle solution with hash {} for merged mining", blockHashForMergedMining);

        return processSolution(
                blockHashForMergedMining,
                blockWithHeaderOnly,
                coinbase,
                (pb) -> pb.buildFromMerkleHashes(blockWithHeaderOnly, merkleHashes, blockTxnCount),
                true
        );
    }

    @Override
    public SubmitBlockResult submitBitcoinBlockTransactions(
            String blockHashForMergedMining,
            BtcBlock blockWithHeaderOnly,
            BtcTransaction coinbase,
            List<String> txHashes) {
        logger.debug("Received tx solution with hash {} for merged mining", blockHashForMergedMining);

        return processSolution(
                blockHashForMergedMining,
                blockWithHeaderOnly,
                coinbase,
                (pb) -> pb.buildFromTxHashes(blockWithHeaderOnly, txHashes),
                true
        );
    }

    @Override
    public SubmitBlockResult submitBitcoinBlock(String blockHashForMergedMining, BtcBlock bitcoinMergedMiningBlock) {
        return submitBitcoinBlock(blockHashForMergedMining, bitcoinMergedMiningBlock, true);
    }

    SubmitBlockResult submitBitcoinBlock(String blockHashForMergedMining, BtcBlock bitcoinMergedMiningBlock, boolean lastTag) {
        logger.debug("Received block with hash {} for merged mining", blockHashForMergedMining);

        return processSolution(
                blockHashForMergedMining,
                bitcoinMergedMiningBlock,
                bitcoinMergedMiningBlock.getTransactions().get(0),
                (pb) -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                lastTag
        );
    }

    private SubmitBlockResult processSolution(
            String blockHashForMergedMining,
            BtcBlock blockWithHeaderOnly,
            BtcTransaction coinbase,
            Function<MerkleProofBuilder, byte[]> proofBuilderFunction,
            boolean lastTag) {
        Block newBlock;
        Keccak256 key = new Keccak256(TypeConverter.removeZeroX(blockHashForMergedMining));

        synchronized (lock) {
            Block workingBlock = blocksWaitingforPoW.get(key);

            if (workingBlock == null) {
                String message = "Cannot publish block, could not find hash " + blockHashForMergedMining + " in the cache";
                logger.warn(message);

                return new SubmitBlockResult("ERROR", message);
            }

            // clone the block
            newBlock = workingBlock.cloneBlock();

            logger.debug("blocksWaitingForPoW size {}", blocksWaitingforPoW.size());
        }

        logger.info("Received block {} {}", newBlock.getNumber(), newBlock.getHash());

        newBlock.setBitcoinMergedMiningHeader(blockWithHeaderOnly.cloneAsHeader().bitcoinSerialize());
        newBlock.setBitcoinMergedMiningCoinbaseTransaction(compressCoinbase(coinbase.bitcoinSerialize(), lastTag));
        newBlock.setBitcoinMergedMiningMerkleProof(MinerUtils.buildMerkleProof(blockchainConfig, proofBuilderFunction, newBlock.getNumber()));
        newBlock.seal();

        if (!isValid(newBlock)) {
            String message = "Invalid block supplied by miner: " + newBlock.getShortHash() + " " + newBlock.getShortHashForMergedMining() + " at height " + newBlock.getNumber();
            logger.error(message);

            return new SubmitBlockResult("ERROR", message);
        } else {
            ImportResult importResult = ethereum.addNewMinedBlock(newBlock);

            logger.info("Mined block import result is {}: {} {} at height {}", importResult, newBlock.getShortHash(), newBlock.getShortHashForMergedMining(), newBlock.getNumber());
            SubmittedBlockInfo blockInfo = new SubmittedBlockInfo(importResult, newBlock.getHash().getBytes(), newBlock.getNumber());

            return new SubmitBlockResult("OK", "OK", blockInfo);
        }
    }

    private boolean isValid(Block block) {
        try {
            return powRule.isValid(block);
        } catch (Exception e) {
            logger.error("Failed to validate PoW from block {}: {}", block.getShortHash(), e);
            return false;
        }
    }

    public static byte[] compressCoinbase(byte[] bitcoinMergedMiningCoinbaseTransactionSerialized) {
        return compressCoinbase(bitcoinMergedMiningCoinbaseTransactionSerialized, true);
    }

    public static byte[] compressCoinbase(byte[] bitcoinMergedMiningCoinbaseTransactionSerialized, boolean lastOccurrence) {
        List<Byte> coinBaseTransactionSerializedAsList = java.util.Arrays.asList(ArrayUtils.toObject(bitcoinMergedMiningCoinbaseTransactionSerialized));
        List<Byte> tagAsList = java.util.Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));

        int rskTagPosition;
        if (lastOccurrence) {
            rskTagPosition = Collections.lastIndexOfSubList(coinBaseTransactionSerializedAsList, tagAsList);
        } else {
            rskTagPosition = Collections.indexOfSubList(coinBaseTransactionSerializedAsList, tagAsList);
        }

        int remainingByteCount = bitcoinMergedMiningCoinbaseTransactionSerialized.length - rskTagPosition - RskMiningConstants.RSK_TAG.length - RskMiningConstants.BLOCK_HEADER_HASH_SIZE;
        if (remainingByteCount > RskMiningConstants.MAX_BYTES_AFTER_MERGED_MINING_HASH) {
            throw new IllegalArgumentException("More than 128 bytes after RSK tag");
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

    @Override
    public RskAddress getCoinbaseAddress() {
        return coinbaseAddress;
    }

    /**
     * getWork returns the latest MinerWork for miners. Subsequent calls to this function with no new work will return
     * currentWork with the notify flag turned off. (they will be different objects too).
     *
     * This method must be called with MinerServer started. That and the fact that work is never set to null
     * will ensure that currentWork is not null.
     *
     * @return the latest MinerWork available.
     */
    @Override
    public MinerWork getWork() {
        MinerWork work = currentWork;

        if (work.getNotify()) {
            /**
             * Set currentWork.notify to false for the next time this function is called.
             * By doing it this way, we avoid taking the lock every time, we just take it once per MinerWork.
             * We have to take the lock to reassign currentWork, but it might have happened that
             * the currentWork got updated when we acquired the lock. In that case, we should just return the new
             * currentWork, regardless of what it is.
             */
            synchronized (lock) {
                if (currentWork != work) {
                    return currentWork;
                }
                currentWork = new MinerWork(currentWork.getBlockHashForMergedMining(), currentWork.getTarget(),
                        currentWork.getFeesPaidToMiner(), false, currentWork.getParentBlockHash());
            }
        }
        return work;
    }

    @VisibleForTesting
    public void setWork(MinerWork work) {
        this.currentWork = work;
    }

    public MinerWork updateGetWork(@Nonnull final Block block, @Nonnull final boolean notify) {
        Keccak256 blockMergedMiningHash = new Keccak256(block.getHashForMergedMining());

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());
        byte[] targetUnknownLengthArray = targetBI.toByteArray();
        byte[] targetArray = new byte[32];
        System.arraycopy(targetUnknownLengthArray, 0, targetArray, 32 - targetUnknownLengthArray.length, targetUnknownLengthArray.length);

        logger.debug("Sending work for merged mining. Hash: {}", block.getShortHashForMergedMining());
        return new MinerWork(blockMergedMiningHash.toJsonString(), TypeConverter.toJsonHex(targetArray), String.valueOf(block.getFeesPaidToMiner()), notify, block.getParentHashJsonString());
    }

    public void setExtraData(byte[] extraData) {
        this.extraData = extraData;
    }

    /**
     * buildBlockToMine creates a block to mine based on the given block as parent.
     *
     * @param newBlockParent         the new block parent.
     * @param createCompetitiveBlock used for testing.
     */
    @Override
    public void buildBlockToMine(@Nonnull Block newBlockParent, boolean createCompetitiveBlock) {
        // See BlockChainImpl.calclBloom() if blocks has txs
        if (createCompetitiveBlock) {
            // Just for testing, mine on top of bestblock's parent
            newBlockParent = blockchain.getBlockByHash(newBlockParent.getParentHash().getBytes());
        }

        logger.info("Starting block to mine from parent {} {}", newBlockParent.getNumber(), newBlockParent.getHash());

        final Block newBlock = builder.build(newBlockParent, extraData);

        if (autoSwitchBetweenNormalAndFallbackMining) {
            setFallbackMining(ProofOfWorkRule.isFallbackMiningPossible(config, newBlock.getHeader()));
        }

        synchronized (lock) {
            Keccak256 parentHash = newBlockParent.getHash();
            boolean notify = this.getNotify(newBlock, parentHash);

            if (notify) {
                latestPaidFeesWithNotify = newBlock.getFeesPaidToMiner();
            }

            latestParentHash = parentHash;
            latestBlock = newBlock;

            currentWork = updateGetWork(newBlock, notify);
            Keccak256 latestBlockHashWaitingForPoW = new Keccak256(newBlock.getHashForMergedMining());

            blocksWaitingforPoW.put(latestBlockHashWaitingForPoW, latestBlock);
            logger.debug("blocksWaitingForPoW size {}", blocksWaitingforPoW.size());
        }

        logger.debug("Built block {}. Parent {}", newBlock.getShortHashForMergedMining(), newBlockParent.getShortHashForMergedMining());
        for (BlockHeader uncleHeader : newBlock.getUncleList()) {
            logger.debug("With uncle {}", uncleHeader.getShortHashForMergedMining());
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
    private boolean getNotify(Block block, Keccak256 parentHash) {
        if (!parentHash.equals(latestParentHash)) {
            return true;
        }

        // note: integer divisions might truncate values
        BigInteger percentage = BigInteger.valueOf(100L + RskMiningConstants.NOTIFY_FEES_PERCENTAGE_INCREASE);
        Coin minFeesNotify = latestPaidFeesWithNotify.multiply(percentage).divide(BigInteger.valueOf(100L));
        Coin feesPaidToMiner = block.getFeesPaidToMiner();
        BigDecimal feesPaidToMinerInDollars = new BigDecimal(feesPaidToMiner.asBigInteger()).multiply(gasUnitInDollars);
        return feesPaidToMiner.compareTo(minFeesNotify) > 0
                && feesPaidToMinerInDollars.compareTo(minFeesNotifyInDollars) >= 0;

    }

    @Override
    public Optional<Block> getLatestBlock() {
        return Optional.ofNullable(latestBlock);
    }

    @Override
    @VisibleForTesting
    public long getCurrentTimeInSeconds() {
        // this is not great, but it was the simplest way to extract BlockToMineBuilder
        return builder.getCurrentTimeInSeconds();
    }

    @Override
    public long increaseTime(long seconds) {
        // this is not great, but it was the simplest way to extract BlockToMineBuilder
        return builder.increaseTime(seconds);
    }

    class NewBlockListener extends EthereumListenerAdapter {

        @Override
        /**
         * onBlock checks if we have to mine over a new block. (Only if the blockchain's best block changed).
         * This method will be called on every block added to the blockchain, even if it doesn't go to the best chain.
         * TODO(???): It would be cleaner to just send this when the blockchain's best block changes.
         * **/
        // This event executes in the thread context of the caller.
        // In case of private miner, it's the "Private Mining timer" task
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            if (isSyncing()) {
                return;
            }

            logger.trace("Start onBlock");
            Block bestBlock = blockchain.getBestBlock();
            MinerWork work = currentWork;
            String bestBlockHash = bestBlock.getHashJsonString();

            if (!work.getParentBlockHash().equals(bestBlockHash)) {
                logger.debug("There is a new best block: {}, number: {}", bestBlock.getShortHashForMergedMining(), bestBlock.getNumber());
                buildBlockToMine(bestBlock, false);
            } else {
                logger.debug("New block arrived but there is no need to build a new block to mine: {}", block.getShortHashForMergedMining());
            }

            logger.trace("End onBlock");
        }

        private boolean isSyncing() {
            return nodeBlockProcessor.hasBetterBlockToSync();
        }
    }

    private class FallbackMiningTask extends TimerTask {
        @Override
        public void run() {
            try {
                Block bestBlock = blockchain.getBestBlock();
                long curtimestampSeconds = getCurrentTimeInSeconds();


                if (curtimestampSeconds > bestBlock.getTimestamp() + secsBetweenFallbackMinedBlocks) {
                    generateFallbackBlock();
                }
            } catch (Throwable th) {
                logger.error("Unexpected error: {}", th);
                panicProcessor.panic("mserror", th.getMessage());
            }
        }
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
