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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.DifficultyUtils;
import co.rsk.util.ListArrayUtil;
import co.rsk.validators.ProofOfWorkRule;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
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

public class MinerServerImpl implements MinerServer {
    private static final long DELAY_BETWEEN_BUILD_BLOCKS_MS = TimeUnit.MINUTES.toMillis(1);

    private static final Logger logger = LoggerFactory.getLogger("minerserver");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final int CACHE_SIZE = 20;

    private static final int EXTRA_DATA_MAX_SIZE = 32;
    private static final int EXTRA_DATA_VERSION = 1;

    private final Ethereum ethereum;
    private final MiningMainchainView mainchainView;
    private final ProofOfWorkRule powRule;
    private final BlockToMineBuilder builder;
    private final ActivationConfig activationConfig;
    private final MinerClock clock;
    private final BlockFactory blockFactory;

    private Timer refreshWorkTimer;
    private NewBlockListener blockListener;

    private boolean started;

    private byte[] extraData;

    @GuardedBy("lock")
    private final LinkedHashMap<Keccak256, Block> blocksWaitingForPoW;
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
    private final SubmissionRateLimitHandler submissionRateLimitHandler;

    public MinerServerImpl(
            RskSystemProperties config,
            Ethereum ethereum,
            MiningMainchainView mainchainView,
            BlockProcessor nodeBlockProcessor,
            ProofOfWorkRule powRule,
            BlockToMineBuilder builder,
            MinerClock clock,
            BlockFactory blockFactory,
            BuildInfo buildInfo,
            MiningConfig miningConfig) {
        this(config, ethereum, mainchainView, nodeBlockProcessor, powRule, builder,
                clock, blockFactory, buildInfo, miningConfig, SubmissionRateLimitHandler.ofMiningConfig(miningConfig));
    }

    @VisibleForTesting
    MinerServerImpl(
            RskSystemProperties config,
            Ethereum ethereum,
            MiningMainchainView mainchainView,
            BlockProcessor nodeBlockProcessor,
            ProofOfWorkRule powRule,
            BlockToMineBuilder builder,
            MinerClock clock,
            BlockFactory blockFactory,
            BuildInfo buildInfo,
            MiningConfig miningConfig,
            SubmissionRateLimitHandler submissionRateLimitHandler) {
        this.ethereum = ethereum;
        this.mainchainView = mainchainView;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.powRule = powRule;
        this.builder = builder;
        this.clock = clock;
        this.blockFactory = blockFactory;
        this.activationConfig = config.getActivationConfig();
        this.submissionRateLimitHandler = Objects.requireNonNull(submissionRateLimitHandler);

        blocksWaitingForPoW = createNewBlocksWaitingList();

        latestPaidFeesWithNotify = Coin.ZERO;
        latestParentHash = null;
        coinbaseAddress = miningConfig.getCoinbaseAddress();
        minFeesNotifyInDollars = BigDecimal.valueOf(miningConfig.getMinFeesNotifyInDollars());
        gasUnitInDollars = BigDecimal.valueOf(miningConfig.getGasUnitInDollars());

        extraData = buildExtraData(config, buildInfo);
    }

    private byte[] buildExtraData(RskSystemProperties config, BuildInfo buildInfo) {
        String identity = config.projectVersionModifier() + "-" + buildInfo.getBuildHash();
        return RLP.encodeList(RLP.encodeElement(RLP.encodeInt(EXTRA_DATA_VERSION)), RLP.encodeString(identity));
    }

    private LinkedHashMap<Keccak256, Block> createNewBlocksWaitingList() {
        return new LinkedHashMap<Keccak256, Block>(CACHE_SIZE) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Keccak256, Block> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    @VisibleForTesting
    Map<Keccak256, Block> getBlocksWaitingForPoW() {
        return blocksWaitingForPoW;
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
            if (refreshWorkTimer != null) {
                refreshWorkTimer.cancel();
                refreshWorkTimer = null;
            }
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
            buildBlockToMine(false);

            if (refreshWorkTimer != null) {
                refreshWorkTimer.cancel();
            }

            refreshWorkTimer = new Timer("Refresh work for mining");
            refreshWorkTimer.schedule(new RefreshBlock(), DELAY_BETWEEN_BUILD_BLOCKS_MS, DELAY_BETWEEN_BUILD_BLOCKS_MS);
        }
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
            if (submissionRateLimitHandler.isEnabled()) {
                if (!submissionRateLimitHandler.isSubmissionAllowed()) {
                    String message = "Cannot publish block, block submission rate limit exceeded";
                    logger.warn(message);

                    return new SubmitBlockResult("ERROR", message);
                }

                submissionRateLimitHandler.onSubmit();
            }

            Block workingBlock = blocksWaitingForPoW.get(key);

            if (workingBlock == null) {
                String message = "Cannot publish block, could not find hash " + blockHashForMergedMining + " in the cache";
                logger.warn(message);

                return new SubmitBlockResult("ERROR", message);
            }

            newBlock = blockFactory.cloneBlockForModification(workingBlock);

            logger.debug("blocksWaitingForPoW size {}", blocksWaitingForPoW.size());
        }

        logger.info("Received block {} {}", newBlock.getNumber(), newBlock.getHash());

        newBlock.setBitcoinMergedMiningHeader(blockWithHeaderOnly.cloneAsHeader().bitcoinSerialize());
        newBlock.setBitcoinMergedMiningCoinbaseTransaction(compressCoinbase(coinbase.bitcoinSerialize(), lastTag));
        newBlock.setBitcoinMergedMiningMerkleProof(MinerUtils.buildMerkleProof(activationConfig, proofBuilderFunction, newBlock.getNumber()));
        newBlock.seal();

        if (!isValid(newBlock)) {
            String message = "Invalid block supplied by miner: " + newBlock.getPrintableHash() + " " + newBlock.getPrintableHashForMergedMining() + " at height " + newBlock.getNumber();
            logger.error(message);

            return new SubmitBlockResult("ERROR", message);
        } else {
            ImportResult importResult = ethereum.addNewMinedBlock(newBlock);

            logger.info("Mined block import result is {}: {} {} at height {}", importResult, newBlock.getPrintableHash(), newBlock.getPrintableHashForMergedMining(), newBlock.getNumber());
            SubmittedBlockInfo blockInfo = new SubmittedBlockInfo(importResult, newBlock.getHash().getBytes(), newBlock.getNumber());

            return new SubmitBlockResult("OK", "OK", blockInfo);
        }
    }

    private boolean isValid(Block block) {
        try {
            return powRule.isValid(block);
        } catch (Exception e) {
            logger.error("Failed to validate PoW from block {}: {}", block.getPrintableHash(), e);
            return false;
        }
    }

    public static byte[] compressCoinbase(byte[] bitcoinMergedMiningCoinbaseTransactionSerialized) {
        return compressCoinbase(bitcoinMergedMiningCoinbaseTransactionSerialized, true);
    }

    public static byte[] compressCoinbase(byte[] bitcoinMergedMiningCoinbaseTransactionSerialized, boolean lastOccurrence) {
        List<Byte> coinBaseTransactionSerializedAsList = ListArrayUtil.asByteList(bitcoinMergedMiningCoinbaseTransactionSerialized);
        List<Byte> tagAsList = ListArrayUtil.asByteList(RskMiningConstants.RSK_TAG);

        int rskTagPosition;
        if (lastOccurrence) {
            rskTagPosition = Collections.lastIndexOfSubList(coinBaseTransactionSerializedAsList, tagAsList);
        } else {
            rskTagPosition = Collections.indexOfSubList(coinBaseTransactionSerializedAsList, tagAsList);
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

        logger.debug("Sending work for merged mining. Hash: {}", block.getPrintableHashForMergedMining());
        return new MinerWork(blockMergedMiningHash.toJsonString(), TypeConverter.toJsonHex(targetArray), String.valueOf(block.getFeesPaidToMiner()), notify, block.getParentHashJsonString());
    }

    public void setExtraData(byte[] clientExtraData) {
        RLPList decodedExtraData = RLP.decodeList(this.extraData);
        byte[] version = decodedExtraData.get(0).getRLPData();
        byte[] identity = decodedExtraData.get(1).getRLPData();

        int rlpClientExtraDataEncodingOverhead = 3;
        int clientExtraDataSize = EXTRA_DATA_MAX_SIZE
                - (version != null ? version.length : 0)
                - (identity != null ? identity.length : 0)
                - rlpClientExtraDataEncodingOverhead;
        byte[] clientExtraDataResized = Arrays.copyOf(clientExtraData, Math.min(clientExtraData.length, clientExtraDataSize));

        this.extraData = RLP.encodeList(version, RLP.encode(identity), RLP.encodeElement(clientExtraDataResized));
    }

    @VisibleForTesting
    public byte[] getExtraData() {
        return Arrays.copyOf(extraData, extraData.length);
    }

    /**
     * buildBlockToMine creates a block to mine using the block received as parent.
     * This method calls buildBlockToMine and that one uses the internal mainchainView
     * Hence, mainchainView must be updated to reflect the new mainchain status.
     * Note. This method is NOT intended to be used in any part of the mining flow and
     * is only here to be consumed from SnapshotManager.
     *
     * @param blockToMineOnTopOf     parent of the block to be built.
     * @param createCompetitiveBlock used for testing.
     */
    @Override
    public void buildBlockToMine(@Nonnull Block blockToMineOnTopOf, boolean createCompetitiveBlock) {
        mainchainView.addBest(blockToMineOnTopOf.getHeader());
        buildBlockToMine(createCompetitiveBlock);
    }

    /**
     * buildBlockToMine creates a block to mine using the current best block as parent.
     * best block is obtained from a blockchain view that has the latest mainchain blocks.
     *
     * @param createCompetitiveBlock used for testing.
     */
    @Override
    public void buildBlockToMine(boolean createCompetitiveBlock) {
        BlockHeader newBlockParentHeader = mainchainView.get().get(0);
        // See BlockChainImpl.calclBloom() if blocks has txs
        if (createCompetitiveBlock) {
            // Just for testing, mine on top of best block's parent
            newBlockParentHeader = mainchainView.get().get(1);
        }

        logger.info("Starting block to mine from parent {} {}", newBlockParentHeader.getNumber(), newBlockParentHeader.getHash());

        List<BlockHeader> mainchainHeaders = mainchainView.get();
        final Block newBlock = builder.build(mainchainHeaders, extraData).getBlock();
        clock.clearIncreaseTime();

        synchronized (lock) {
            Keccak256 parentHash = newBlockParentHeader.getHash();
            boolean notify = this.getNotify(newBlock, parentHash);

            if (notify) {
                latestPaidFeesWithNotify = newBlock.getFeesPaidToMiner();
            }

            latestParentHash = parentHash;
            latestBlock = newBlock;

            currentWork = updateGetWork(newBlock, notify);
            Keccak256 latestBlockHashWaitingForPoW = new Keccak256(newBlock.getHashForMergedMining());

            blocksWaitingForPoW.put(latestBlockHashWaitingForPoW, latestBlock);
            logger.debug("blocksWaitingForPoW size {}", blocksWaitingForPoW.size());
        }

        logger.debug("Built block {}. Parent {}", newBlock.getPrintableHashForMergedMining(), newBlockParentHeader.getPrintableHashForMergedMining());
        for (BlockHeader uncleHeader : newBlock.getUncleList()) {
            logger.debug("With uncle {}", uncleHeader.getPrintableHashForMergedMining());
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

    class NewBlockListener extends EthereumListenerAdapter {

        @Override
        // This event executes in the thread context of the caller.
        // In case of private miner, it's the "Private Mining timer" task
        public void onBestBlock(Block newBestBlock, List<TransactionReceipt> receipts) {
            if (isSyncing()) {
                return;
            }

            logger.trace("Start onBestBlock");

            logger.debug(
                    "There is a new best block: {}, number: {}",
                    newBestBlock.getPrintableHashForMergedMining(),
                    newBestBlock.getNumber());
            mainchainView.addBest(newBestBlock.getHeader());
            buildBlockToMine(false);

            logger.trace("End onBestBlock");
        }

        private boolean isSyncing() {
            return nodeBlockProcessor.hasBetterBlockToSync();
        }
    }

    /**
     * RefreshBlocks rebuilds the block to mine.
     */
    private class RefreshBlock extends TimerTask {
        @Override
        public void run() {
            try {
                buildBlockToMine(false);
            } catch (Throwable th) {
                logger.error("Unexpected error: {}", th);
                panicProcessor.panic("mserror", th.getMessage());
            }
        }
    }
}
