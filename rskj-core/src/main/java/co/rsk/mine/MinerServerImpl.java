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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.MiningConfig;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BtcBlockFacCache;
import co.rsk.core.bc.BtcMiningParentResolution;
import co.rsk.core.bc.CachedBtcBlockForFac;
import co.rsk.core.bc.FacBlockHashesCache;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.DifficultyUtils;
import co.rsk.util.HexUtils;
import co.rsk.util.ListArrayUtil;
import co.rsk.validators.ProofOfWorkRule;



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
    private final ForkBalanceBtcCacheConfig forkBalanceBtcCacheConfig;
    private final MinerClock clock;
    private final BlockFactory blockFactory;

    private Timer refreshWorkTimer;
    @Nullable
    private TimerTask pendingWorkBuildRetry;
    private NewBlockTxListener blockListener;

    private boolean started;

    private byte[] extraData;

    @GuardedBy("lock")
    private final LinkedHashMap<Keccak256, Block> blocksWaitingForPoW;
    @GuardedBy("lock")
    private final LinkedHashMap<Keccak256, Sha256Hash> btcMiningParentByWorkHash;
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

    private final boolean updateWorkOnNewTransaction;

    @Nullable
    private final FacBlockHashesCache facBlockHashesCache;

    @Nullable
    private final BtcBlockFacCache btcBlockFacCache;

    private final NetworkParameters bitcoinNetworkParameters;

    @GuardedBy("lock")
    @Nullable
    private BtcBlock regtestBtcMiningTip;

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
                clock, blockFactory, buildInfo, miningConfig, SubmissionRateLimitHandler.ofMiningConfig(miningConfig),
                null, null, config.getBitcoinjNetworkConstants());
    }

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
            MiningConfig miningConfig,
            @Nullable FacBlockHashesCache facBlockHashesCache) {
        this(config, ethereum, mainchainView, nodeBlockProcessor, powRule, builder,
                clock, blockFactory, buildInfo, miningConfig, SubmissionRateLimitHandler.ofMiningConfig(miningConfig),
                facBlockHashesCache, null, config.getBitcoinjNetworkConstants());
    }

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
            MiningConfig miningConfig,
            @Nullable FacBlockHashesCache facBlockHashesCache,
            @Nullable BtcBlockFacCache btcBlockFacCache,
            NetworkParameters bitcoinNetworkParameters) {
        this(config, ethereum, mainchainView, nodeBlockProcessor, powRule, builder,
                clock, blockFactory, buildInfo, miningConfig, SubmissionRateLimitHandler.ofMiningConfig(miningConfig),
                facBlockHashesCache, btcBlockFacCache, bitcoinNetworkParameters);
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
        this(config, ethereum, mainchainView, nodeBlockProcessor, powRule, builder,
                clock, blockFactory, buildInfo, miningConfig, submissionRateLimitHandler,
                null, null, config.getBitcoinjNetworkConstants());
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
            SubmissionRateLimitHandler submissionRateLimitHandler,
            @Nullable FacBlockHashesCache facBlockHashesCache,
            @Nullable BtcBlockFacCache btcBlockFacCache,
            NetworkParameters bitcoinNetworkParameters) {
        this.ethereum = ethereum;
        this.mainchainView = mainchainView;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.powRule = powRule;
        this.builder = builder;
        this.clock = clock;
        this.blockFactory = blockFactory;
        this.activationConfig = config.getActivationConfig();
        this.forkBalanceBtcCacheConfig = config.forkBalanceBtcCacheConfig();

        this.submissionRateLimitHandler = Objects.requireNonNull(submissionRateLimitHandler);
        if (this.submissionRateLimitHandler.isEnabled()) {
            logger.warn("Miner submission rate limit is enabled. Usually it is being used in test networks. Make sure it's intentional and not by mistake");
        }

        blocksWaitingForPoW = createNewBlocksWaitingList();
        btcMiningParentByWorkHash = createNewBtcParentByWorkMap();

        latestPaidFeesWithNotify = Coin.ZERO;
        latestParentHash = null;
        coinbaseAddress = miningConfig.getCoinbaseAddress();
        minFeesNotifyInDollars = BigDecimal.valueOf(miningConfig.getMinFeesNotifyInDollars());
        gasUnitInDollars = BigDecimal.valueOf(miningConfig.getGasUnitInDollars());

        updateWorkOnNewTransaction = config.updateWorkOnNewTransaction();

        extraData = buildExtraData(config, buildInfo);

        this.facBlockHashesCache = facBlockHashesCache;
        this.btcBlockFacCache = btcBlockFacCache;
        this.bitcoinNetworkParameters = Objects.requireNonNull(bitcoinNetworkParameters, "bitcoinNetworkParameters");
    }

    private byte[] buildExtraData(RskSystemProperties config, BuildInfo buildInfo) {
        String identity = config.projectVersionModifier() + "-" + buildInfo.getBuildHash();
        return RLP.encodeList(RLP.encodeElement(RLP.encodeInt(EXTRA_DATA_VERSION)), RLP.encodeString(identity));
    }

    private LinkedHashMap<Keccak256, Block> createNewBlocksWaitingList() {
        return new LinkedHashMap<Keccak256, Block>(CACHE_SIZE) {

            private static final long serialVersionUID = 8044729378421476319L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Keccak256, Block> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    private LinkedHashMap<Keccak256, Sha256Hash> createNewBtcParentByWorkMap() {
        return new LinkedHashMap<Keccak256, Sha256Hash>(CACHE_SIZE) {

            private static final long serialVersionUID = -5847293847129476319L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Keccak256, Sha256Hash> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    @VisibleForTesting
    public Map<Keccak256, Block> getBlocksWaitingForPoW() {
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
            cancelPendingWorkBuildRetry();
        }
    }

    private void cancelPendingWorkBuildRetry() {
        synchronized (lock) {
            if (pendingWorkBuildRetry != null) {
                pendingWorkBuildRetry.cancel();
                pendingWorkBuildRetry = null;
            }
        }
    }

    @Override
    public void start() {
        if (started) {
            return;
        }

        requireBitcoinRpcForV3MiningIfApplicable();

        synchronized (lock) {
            started = true;
            blockListener = new NewBlockTxListener(mainchainView, this::buildBlockToMine, nodeBlockProcessor, updateWorkOnNewTransaction);
            ethereum.addListener(blockListener);
            buildBlockToMine(false);

            if (refreshWorkTimer != null) {
                refreshWorkTimer.cancel();
            }

            refreshWorkTimer = new Timer("Refresh work for mining");
            refreshWorkTimer.schedule(new RefreshBlock(), DELAY_BETWEEN_BUILD_BLOCKS_MS, DELAY_BETWEEN_BUILD_BLOCKS_MS);
        }
    }

    /**
     * Mining pools must configure {@code miner.forkBalance.btcRpc.url} when the next block uses header v3.
     */
    @VisibleForTesting
    void requireBitcoinRpcForV3MiningIfApplicable() {
        if (bitcoinNetworkParameters instanceof RegTestParams) {
            return;
        }
        long nextBlockNumber = mainchainView.get().isEmpty()
                ? 0L
                : mainchainView.get().get(mainchainView.get().size() - 1).getNumber() + 1L;
        if (!activationConfig.isActive(ConsensusRule.RSKIP555, nextBlockNumber)) {
            return;
        }
        if (activationConfig.getHeaderVersion(nextBlockNumber) != (byte) 0x03) {
            return;
        }
        if (forkBalanceBtcCacheConfig.isBtcRpcEnabled()) {
            return;
        }
        throw new IllegalStateException(
                "miner.forkBalance.btcRpc.url must point to a local bitcoind when mining header v3 blocks "
                        + "(RSKIP555 active at next height " + nextBlockNumber + "). "
                        + "Validating-only nodes should leave this empty and keep miner.server.enabled=false.");
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

    public SubmitBlockResult submitBitcoinBlock(String blockHashForMergedMining, BtcBlock bitcoinMergedMiningBlock, boolean lastTag) {
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
        Keccak256 key = new Keccak256(HexUtils.removeHexPrefix(blockHashForMergedMining));

        synchronized (lock) {
            if (!submissionRateLimitHandler.isSubmissionAllowed()) {
                String message = "Cannot publish block, block submission rate limit exceeded";
                logger.warn(message);

                return new SubmitBlockResult("ERROR", message);
            }

            submissionRateLimitHandler.onSubmit();

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
        byte[] mergedMiningMerkleProof = MinerUtils.buildMerkleProof(activationConfig, proofBuilderFunction, newBlock.getNumber());
        newBlock.setBitcoinMergedMiningMerkleProof(mergedMiningMerkleProof);
        if (!applyForkBalanceProofFromBtcParentIfApplicable(
                newBlock, blockWithHeaderOnly, coinbase, key)) {
            return new SubmitBlockResult(
                    "ERROR",
                    "Header v3 requires a valid Bitcoin parent block for fork-balance proof (parent unavailable or failed validation).");
        }
        updateRegtestBtcMiningTip(blockWithHeaderOnly);
        newBlock.seal();

        if (!isValid(newBlock)) {
            String message = "Invalid block supplied by miner: " + newBlock.getPrintableHash() + " " + newBlock.getPrintableHashForMergedMining() + " at height " + newBlock.getNumber();
            logger.error(message);

            return new SubmitBlockResult("ERROR", message);
        }
        if (btcBlockFacCache != null) {
            btcBlockFacCache.recordFromMiningSubmit(
                    blockWithHeaderOnly, coinbase, mergedMiningMerkleProof, newBlock.getNumber());
        }
        ImportResult importResult = ethereum.addNewMinedBlock(newBlock);

        logger.info("Mined block import result is {}: {} {} at height {}", importResult, newBlock.getPrintableHash(), newBlock.getPrintableHashForMergedMining(), newBlock.getNumber());
        SubmittedBlockInfo blockInfo = new SubmittedBlockInfo(importResult, newBlock.getHash().getBytes(), newBlock.getNumber());

        return new SubmitBlockResult("OK", "OK", blockInfo);
    }

    /**
     * Rebuilds the fork-balance proof from the submitted merge-mined Bitcoin block and its parent (BTCB).
     */
    private boolean applyForkBalanceProofFromBtcParentIfApplicable(
            Block newBlock,
            BtcBlock mergedMinedBtcHeaderOrBlock,
            BtcTransaction coinbase,
            Keccak256 mergedMiningHashKey) {
        if (newBlock.getHeader().getVersion() != (byte) 0x03) {
            return true;
        }
        if (coinbase == null) {
            logger.error("Fork balance proof: coinbase must not be null for v3 header at height {}", newBlock.getNumber());
            return false;
        }
        Sha256Hash parentHash = mergedMinedBtcHeaderOrBlock.getPrevBlockHash();
        synchronized (lock) {
            Sha256Hash boundParent = btcMiningParentByWorkHash.get(mergedMiningHashKey);
            if (boundParent != null && !boundParent.equals(parentHash)) {
                logger.error(
                        "Fork balance proof: submitted BTC parent {} does not match work-bound parent {} at height {}",
                        parentHash,
                        boundParent,
                        newBlock.getNumber());
                return false;
            }
        }
        Optional<CachedBtcBlockForFac> cachedParent = resolveCachedBtcParent(parentHash);
        if (cachedParent.isEmpty()) {
            logger.warn(
                    "Fork balance proof: BTC parent {} not available for v3 header at height {}",
                    parentHash,
                    newBlock.getNumber());
            return false;
        }
        try {
            List<Keccak256> rskHashesForProofType = facBlockHashesCache != null
                    ? facBlockHashesCache.getMergedMiningHashesForProofType()
                    : Collections.singletonList(mergedMiningHashKey);
            byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                    rskHashesForProofType,
                    mergedMinedBtcHeaderOrBlock,
                    cachedParent.get());
            newBlock.getHeader().setForkBalanceProof(proof);
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Fork balance proof failed for block at height {}: {}", newBlock.getNumber(), e.getMessage());
            return false;
        }
    }

    private Optional<CachedBtcBlockForFac> resolveCachedBtcParent(Sha256Hash parentHash) {
        if (btcBlockFacCache == null) {
            return Optional.empty();
        }
        return btcBlockFacCache.resolveParent(parentHash);
    }

    @Override
    public BtcBlock buildBitcoinMergedMiningBlock(NetworkParameters params, MinerWork work) {
        BtcTransaction coinbase = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(params, work);
        if (shouldChainRegtestBtcBlocks(params)) {
            synchronized (lock) {
                BtcBlock parent = ensureRegtestBtcParent(params);
                return RegtestBtcMergeMiningHelper.buildChildOnParent(params, parent, coinbase);
            }
        }
        Optional<Sha256Hash> boundParent = resolveBoundBtcParentForWork(work);
        if (boundParent.isPresent() && btcBlockFacCache != null) {
            Optional<CachedBtcBlockForFac> miningParent = btcBlockFacCache.resolveParent(boundParent.get());
            if (miningParent.isPresent()) {
                BtcBlock parentBlock = miningParent.get().toBtcBlock(params);
                return MinerUtils.buildChildTemplateOnParent(params, parentBlock, coinbase);
            }
            logger.warn(
                    "Bound BTC parent {} is not available in fork-balance cache for work {}",
                    boundParent.get(),
                    work.getBlockHashForMergedMining());
        }
        return MinerUtils.getBitcoinMergedMiningBlock(params, coinbase);
    }

    private Optional<Sha256Hash> resolveBoundBtcParentForWork(MinerWork work) {
        String btcParentFromWork = work.getBtcParentBlockHash();
        if (btcParentFromWork != null && !btcParentFromWork.isEmpty()) {
            return Optional.of(Sha256Hash.wrap(HexUtils.removeHexPrefix(btcParentFromWork)));
        }
        synchronized (lock) {
            Keccak256 workKey = new Keccak256(HexUtils.removeHexPrefix(work.getBlockHashForMergedMining()));
            return Optional.ofNullable(btcMiningParentByWorkHash.get(workKey));
        }
    }

    private boolean shouldChainRegtestBtcBlocks(NetworkParameters params) {
        return params instanceof RegTestParams;
    }

    private BtcBlock ensureRegtestBtcParent(NetworkParameters params) {
        if (regtestBtcMiningTip != null) {
            return regtestBtcMiningTip;
        }
        BtcTransaction parentCoinbase = RegtestBtcMergeMiningHelper.neutralCoinbase(params, (byte) 0x51);
        BtcBlock parent = RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(
                params, parentCoinbase);
        if (btcBlockFacCache != null) {
            byte[] parentMerkleProof = MinerUtils.buildMerkleProof(
                    activationConfig,
                    pb -> pb.buildFromBlock(parent),
                    1L);
            btcBlockFacCache.recordFromFullBtcBlock(parent, parentMerkleProof);
        }
        regtestBtcMiningTip = parent;
        return parent;
    }

    private void updateRegtestBtcMiningTip(BtcBlock mergedMinedBtcBlock) {
        if (!(bitcoinNetworkParameters instanceof RegTestParams)) {
            return;
        }
        synchronized (lock) {
            regtestBtcMiningTip = mergedMinedBtcBlock;
        }
    }

    /**
     * Precomputes the fork-balance proof at work-build time so the merged-mining hash is stable before
     * the Bitcoin coinbase is assembled.
     *
     * @return the bound BTC parent hash when proof precomputation succeeds
     */
    private Optional<Sha256Hash> prepareForkBalanceProofAtWorkBuild(Block block) {
        if (block == null || block.getHeader() == null) {
            return Optional.empty();
        }
        if (block.getHeader().getVersion() != (byte) 0x03) {
            throw new IllegalStateException("prepareForkBalanceProofAtWorkBuild is only for header v3");
        }
        if (btcBlockFacCache == null) {
            logger.error(
                    "Fork balance proof: BtcBlockFacCache is not configured for v3 work at height {}",
                    block.getNumber());
            return Optional.empty();
        }
        try {
            synchronized (lock) {
                Optional<BtcMiningParentResolution> parentResolution = resolveBtcMiningParentForWorkBuild();
                if (parentResolution.isEmpty()) {
                    logger.error(
                            "Fork balance proof: BTC mining parent unavailable for v3 work at height {} "
                                    + "(configure miner.forkBalance.btcRpc.url and ensure bitcoind is reachable)",
                            block.getNumber());
                    return Optional.empty();
                }
                BtcMiningParentResolution resolution = parentResolution.get();
                if (resolution.getSource() == BtcMiningParentResolution.Source.CACHE_FALLBACK) {
                    logger.info(
                            "Fork balance proof: using cached BTC parent {} at height {} (RPC tip unavailable)",
                            resolution.getParent().getBlockHash(),
                            resolution.getParent().getHeight());
                }
                CachedBtcBlockForFac cachedParent = resolution.getParent();
                List<Keccak256> rskHashes = facBlockHashesCache != null
                        ? facBlockHashesCache.getMergedMiningHashesForProofType()
                        : Collections.emptyList();

                byte[] mergedMiningHash = block.getHashForMergedMining();
                BtcBlock childTemplate = buildBtcChildTemplateForMining(cachedParent, mergedMiningHash);
                byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                        rskHashes, childTemplate, cachedParent);
                block.getHeader().setForkBalanceProof(proof);

                byte[] mergedMiningHashAfterProof = block.getHashForMergedMining();
                if (!java.util.Arrays.equals(mergedMiningHash, mergedMiningHashAfterProof)) {
                    childTemplate = buildBtcChildTemplateForMining(cachedParent, mergedMiningHashAfterProof);
                    proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                            rskHashes, childTemplate, cachedParent);
                    block.getHeader().setForkBalanceProof(proof);
                }

                byte[] finalProof = block.getHeader().getForkBalanceProof();
                if (finalProof == null || ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(finalProof)) {
                    logger.error(
                            "Fork balance proof: placeholder proof remains after precompute for v3 work at height {}",
                            block.getNumber());
                    return Optional.empty();
                }
                return Optional.of(cachedParent.getBlockHash());
            }
        } catch (RuntimeException e) {
            logger.error(
                    "Fork balance proof: could not precompute for v3 work at height {}: {}",
                    block.getNumber(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BtcMiningParentResolution> resolveBtcMiningParentForWorkBuild() {
        if (bitcoinNetworkParameters instanceof RegTestParams) {
            BtcBlock parent = ensureRegtestBtcParent(bitcoinNetworkParameters);
            return btcBlockFacCache.resolveParent(parent.getHash())
                    .map(p -> new BtcMiningParentResolution(
                            p, BtcMiningParentResolution.Source.LOCAL_CACHE_ONLY, null));
        }
        return btcBlockFacCache.resolveMiningParentForNewWork();
    }

    private BtcBlock buildBtcChildTemplateForMining(CachedBtcBlockForFac miningParent, byte[] mergedMiningHash) {
        BtcTransaction coinbase = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(
                bitcoinNetworkParameters, mergedMiningHash);
        BtcBlock parentBlock = miningParent.toBtcBlock(bitcoinNetworkParameters);
        if (bitcoinNetworkParameters instanceof RegTestParams) {
            return RegtestBtcMergeMiningHelper.buildChildOnParent(
                    bitcoinNetworkParameters, parentBlock, coinbase);
        }
        return MinerUtils.buildChildTemplateOnParent(bitcoinNetworkParameters, parentBlock, coinbase);
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
                       currentWork.getFeesPaidToMiner(), false, currentWork.getParentBlockHash(),
                       currentWork.getBtcParentBlockHash());
            }
        }
        return work;
    }

    @VisibleForTesting
    public void setWork(MinerWork work) {
        this.currentWork = work;
    }

    @VisibleForTesting
    public Block getLatestBuiltBlockForTesting() {
        synchronized (lock) {
            return latestBlock;
        }
    }

    public MinerWork updateGetWork(
            @Nonnull final Block block,
            @Nonnull final boolean notify,
            @Nullable Sha256Hash btcParentHash) {
        Keccak256 blockMergedMiningHash = new Keccak256(block.getHashForMergedMining());

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());
        byte[] targetUnknownLengthArray = targetBI.toByteArray();
        byte[] targetArray = new byte[32];
        System.arraycopy(targetUnknownLengthArray, 0, targetArray, 32 - targetUnknownLengthArray.length, targetUnknownLengthArray.length);

        String btcParentJson = btcParentHash != null ? btcParentHash.toString() : "";
        logger.debug("Sending work for merged mining. Hash: {}", block.getPrintableHashForMergedMining());
        return new MinerWork(
                blockMergedMiningHash.toJsonString(),
                HexUtils.toJsonHex(targetArray),
                String.valueOf(block.getFeesPaidToMiner()),
                notify,
                block.getParentHashJsonString(),
                btcParentJson);
    }

    private void scheduleWorkBuildRetry() {
        if (!started) {
            return;
        }
        synchronized (lock) {
            if (pendingWorkBuildRetry != null || refreshWorkTimer == null) {
                return;
            }
            long delayMs = forkBalanceBtcCacheConfig.getWorkBuildRetryIntervalMs();
            pendingWorkBuildRetry = new TimerTask() {
                @Override
                public void run() {
                    synchronized (lock) {
                        pendingWorkBuildRetry = null;
                    }
                    if (started) {
                        logger.debug("Retrying work build after fork-balance parent resolution stall");
                        buildBlockToMine(false);
                    }
                }
            };
            refreshWorkTimer.schedule(pendingWorkBuildRetry, delayMs);
        }
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
        Optional<Sha256Hash> btcParentForWork = Optional.empty();
        if (newBlock.getHeader().getVersion() == (byte) 0x03) {
            Optional<Sha256Hash> btcParent = prepareForkBalanceProofAtWorkBuild(newBlock);
            if (btcParent.isEmpty()) {
                logger.error(
                        "Skipping work update for block at height {}: fork-balance proof could not be precomputed",
                        newBlock.getNumber());
                scheduleWorkBuildRetry();
                return;
            }
            btcParentForWork = btcParent;
        }
        cancelPendingWorkBuildRetry();
        clock.clearIncreaseTime();

        synchronized (lock) {
            Keccak256 parentHash = newBlockParentHeader.getHash();
            boolean notify = this.getNotify(newBlock, parentHash);

            if (notify) {
                latestPaidFeesWithNotify = newBlock.getFeesPaidToMiner();
            }

            latestParentHash = parentHash;
            latestBlock = newBlock;

            currentWork = updateGetWork(newBlock, notify, btcParentForWork.orElse(null));
            Keccak256 latestBlockHashWaitingForPoW = new Keccak256(newBlock.getHashForMergedMining());

            blocksWaitingForPoW.put(latestBlockHashWaitingForPoW, latestBlock);
            btcParentForWork.ifPresent(btcParent ->
                    btcMiningParentByWorkHash.put(latestBlockHashWaitingForPoW, btcParent));
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

    @VisibleForTesting
    static class NewBlockTxListener extends EthereumListenerAdapter {

        private final MiningMainchainView mainchainView;
        private final Consumer<Boolean> buildBlock;
        private final BlockProcessor nodeBlockProcessor;
        
        private final boolean updateWorkOnNewTransaction;
        
        public NewBlockTxListener(MiningMainchainView mainchainView, Consumer<Boolean> buildBlock, BlockProcessor nodeBlockProcessor, boolean updateWorkOnNewTransaction) {
            this.mainchainView = mainchainView;
            this.buildBlock = buildBlock;
            this.nodeBlockProcessor = nodeBlockProcessor;
            this.updateWorkOnNewTransaction = updateWorkOnNewTransaction;
        }
        
        
        // This event executes in the thread context of the caller.
        // In case of private miner, it's the "Private Mining timer" task
        @Override
        public void onBestBlock(Block newBestBlock, List<TransactionReceipt> receipts) {
            if (isSyncing()) {
                return;
            }

            logger.trace("Start onBestBlock");

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "There is a new best block: {}, number: {}",
                        newBestBlock.getPrintableHashForMergedMining(),
                        newBestBlock.getNumber());
            }

            mainchainView.addBest(newBestBlock.getHeader());

            buildBlock.accept(false);

            logger.trace("End onBestBlock");
        }

        @Override
        public void onPendingTransactionsReceived(List<Transaction> transactions) {

            if (!updateWorkOnNewTransaction || isSyncing()) {
                return;
            }

            logger.trace("Pending Transactions Received");

            buildBlock.accept(false);

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
