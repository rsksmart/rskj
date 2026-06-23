/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.core.bc;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.mine.BitcoinBlockRpcClient;
import co.rsk.mine.MinerUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Height-bucketed cache of recent Bitcoin blocks for fork-balance proof construction.
 * <p>
 * Blocks are indexed by BTC hash for parent lookup and grouped by BTC height so competing
 * headers at the same height can coexist while older heights are evicted.
 */
public final class BtcBlockFacCache {

    private static final Logger logger = LoggerFactory.getLogger("minerserver");

    private final ForkBalanceBtcCacheConfig config;
    private final NetworkParameters btcParams;
    @Nullable
    private final BitcoinBlockRpcClient rpcClient;
    @Nullable
    private final ActivationConfig activationConfig;

    /** height -> (blockHash -> entry) */
    private final TreeMap<Integer, Map<Sha256Hash, CachedBtcBlockForFac>> byHeight = new TreeMap<>();
    private final Map<Sha256Hash, CachedBtcBlockForFac> byHash = new HashMap<>();
    @Nullable
    private Sha256Hash lastKnownGoodTipHash;
    private int lastKnownGoodTipHeight = -1;

    public BtcBlockFacCache(
            ForkBalanceBtcCacheConfig config,
            NetworkParameters btcParams,
            @Nullable BitcoinBlockRpcClient rpcClient,
            @Nullable ActivationConfig activationConfig) {
        this.config = Objects.requireNonNull(config, "config");
        this.btcParams = Objects.requireNonNull(btcParams, "btcParams");
        this.rpcClient = rpcClient;
        this.activationConfig = activationConfig;
    }

    /**
     * Resolves a parent BTC block by hash, using the local cache and optional JSON-RPC fallback.
     */
    public synchronized Optional<CachedBtcBlockForFac> resolveParent(Sha256Hash parentHash) {
        if (Sha256Hash.ZERO_HASH.equals(parentHash)) {
            return Optional.empty();
        }
        CachedBtcBlockForFac cached = byHash.get(parentHash);
        if (cached != null) {
            if (cached.isComplete()) {
                return Optional.of(cached);
            }
            // Incomplete stub (e.g. from import without coinbase); try RPC below if configured.
        }
        if (rpcClient == null || !rpcClient.isConfigured()) {
            return Optional.empty();
        }
        Optional<BitcoinBlockRpcClient.FetchedBtcBlock> fetched = rpcClient.fetchBlock(parentHash, btcParams);
        if (fetched.isEmpty()) {
            return Optional.empty();
        }
        BtcBlock block = fetched.get().block();
        if (!block.getHash().equals(parentHash)) {
            logger.warn(
                    "BTC RPC returned block {} but requested parent {}",
                    block.getHash(),
                    parentHash);
            return Optional.empty();
        }
        CachedBtcBlockForFac entry = buildEntryFromFullBlock(block, fetched.get().height());
        if (!entry.isComplete()) {
            logger.warn(
                    "BTC RPC block {} is not usable for fork-balance parent proof (incomplete coinbase merkle proof)",
                    parentHash);
            return Optional.empty();
        }
        put(entry);
        return Optional.of(entry);
    }

    /**
     * Resolves the Bitcoin block merge-mining should build on for a new work unit.
     * <p>
     * When JSON-RPC is configured, uses {@code getbestblockhash} (with retries) and completes the entry via
     * {@link #resolveParent(Sha256Hash)}. On RPC failure, falls back to the highest complete cached block.
     */
    public synchronized Optional<BtcMiningParentResolution> resolveMiningParentForNewWork() {
        if (rpcClient != null && rpcClient.isConfigured()) {
            Optional<Sha256Hash> bestHash = rpcClient.fetchBestBlockHash();
            if (bestHash.isPresent()) {
                Optional<CachedBtcBlockForFac> fromRpc = resolveParent(bestHash.get());
                if (fromRpc.isPresent()) {
                    rememberGoodTip(fromRpc.get());
                    return Optional.of(new BtcMiningParentResolution(
                            fromRpc.get(),
                            BtcMiningParentResolution.Source.RPC_TIP,
                            bestHash.get()));
                }
            }
            logger.warn(
                    "BTC RPC best block unavailable after retries; falling back to local fork-balance cache "
                            + "(last known tip {} at height {})",
                    lastKnownGoodTipHash,
                    lastKnownGoodTipHeight);
            return fallbackMiningParentFromCache(BtcMiningParentResolution.Source.CACHE_FALLBACK);
        }
        return fallbackMiningParentFromCache(BtcMiningParentResolution.Source.LOCAL_CACHE_ONLY);
    }

    private Optional<BtcMiningParentResolution> fallbackMiningParentFromCache(BtcMiningParentResolution.Source source) {
        Optional<CachedBtcBlockForFac> cached = highestCompleteCachedBlock();
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        CachedBtcBlockForFac parent = cached.get();
        if (source == BtcMiningParentResolution.Source.CACHE_FALLBACK
                && lastKnownGoodTipHash != null
                && !lastKnownGoodTipHash.equals(parent.getBlockHash())) {
            logger.info(
                    "Using cached BTC parent {} at height {} instead of last known tip {} at height {}",
                    parent.getBlockHash(),
                    parent.getHeight(),
                    lastKnownGoodTipHash,
                    lastKnownGoodTipHeight);
        }
        return Optional.of(new BtcMiningParentResolution(parent, source, lastKnownGoodTipHash));
    }

    private void rememberGoodTip(CachedBtcBlockForFac tip) {
        lastKnownGoodTipHash = tip.getBlockHash();
        lastKnownGoodTipHeight = tip.getHeight();
    }

    private Optional<CachedBtcBlockForFac> highestCompleteCachedBlock() {
        if (byHeight.isEmpty()) {
            return Optional.empty();
        }
        for (int height = byHeight.lastKey(); height >= byHeight.firstKey(); height--) {
            Map<Sha256Hash, CachedBtcBlockForFac> bucket = byHeight.get(height);
            if (bucket == null) {
                continue;
            }
            for (CachedBtcBlockForFac entry : bucket.values()) {
                if (entry.isComplete()) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Records merge-mined Bitcoin data from a local mining submission.
     */
    public synchronized void recordFromMiningSubmit(
            BtcBlock mergedMinedBtcBlock,
            BtcTransaction coinbase,
            byte[] coinbaseMerkleProof,
            long rskBlockNumber) {
        Objects.requireNonNull(mergedMinedBtcBlock, "mergedMinedBtcBlock");
        Objects.requireNonNull(coinbase, "coinbase");
        int height = resolveHeight(mergedMinedBtcBlock.getPrevBlockHash());
        byte[] header80 = mergedMinedBtcBlock.cloneAsHeader().bitcoinSerialize();
        CachedBtcBlockForFac entry = new CachedBtcBlockForFac(
                mergedMinedBtcBlock.getHash(),
                mergedMinedBtcBlock.getPrevBlockHash(),
                height,
                header80,
                coinbase.bitcoinSerialize(),
                coinbaseMerkleProof);
        put(entry, true);
        logger.debug("Cached BTC block {} at height {} from mining submit (RSK #{})", entry.getBlockHash(), height, rskBlockNumber);
    }

    /**
     * Records merge-mined Bitcoin header data from an imported RSK block.
     * Coinbase bytes may be absent; entries are completed on demand via RPC when configured.
     */
    public synchronized void recordFromImportedRskBlock(Block block) {
        Objects.requireNonNull(block, "block");
        BlockHeader header = block.getHeader();
        byte[] mmHeader = header.getBitcoinMergedMiningHeader();
        if (mmHeader == null || mmHeader.length != 80) {
            return;
        }
        byte[] mmMerkleProof = header.getBitcoinMergedMiningMerkleProof();
        if (mmMerkleProof == null) {
            mmMerkleProof = new byte[0];
        }
        try {
            BtcBlock btcHeader = btcParams.getDefaultSerializer().makeBlock(mmHeader);
            Sha256Hash hash = btcHeader.getHash();
            if (byHash.containsKey(hash)) {
                CachedBtcBlockForFac existing = byHash.get(hash);
                if (existing.isComplete()) {
                    return;
                }
            }
            int height = resolveHeight(btcHeader.getPrevBlockHash());
            CachedBtcBlockForFac entry = new CachedBtcBlockForFac(
                    hash,
                    btcHeader.getPrevBlockHash(),
                    height,
                    mmHeader,
                    null,
                    mmMerkleProof);
            put(entry, false);
            logger.trace("Cached partial BTC block {} at height {} from imported RSK #{}", hash, height, block.getNumber());
        } catch (RuntimeException e) {
            logger.debug("Skipping BTC FAC cache for RSK block {}: {}", block.getNumber(), e.getMessage());
        }
    }

    /**
     * Stores a fully known Bitcoin block (e.g. regtest parent seed).
     */
    public synchronized void recordFromFullBtcBlock(BtcBlock block, byte[] coinbaseMerkleProof) {
        CachedBtcBlockForFac entry = buildEntryFromFullBlock(block, resolveHeight(block.getPrevBlockHash()));
        if (coinbaseMerkleProof != null && coinbaseMerkleProof.length > 0) {
            entry = entry.withCoinbaseMerkleProof(coinbaseMerkleProof);
        }
        put(entry);
    }

    private CachedBtcBlockForFac buildEntryFromFullBlock(BtcBlock block, int height) {
        byte[] merkleProof = new byte[0];
        if (activationConfig != null && block.getTransactions().size() > 1) {
            merkleProof = MinerUtils.buildMerkleProof(
                    activationConfig,
                    pb -> pb.buildFromBlock(block),
                    1L);
        } else if (activationConfig != null && block.getTransactions().size() == 1) {
            merkleProof = new byte[0];
        }
        return CachedBtcBlockForFac.fromBtcBlock(block, height, merkleProof);
    }

    private int resolveHeight(Sha256Hash prevBlockHash) {
        if (Sha256Hash.ZERO_HASH.equals(prevBlockHash)) {
            return 0;
        }
        CachedBtcBlockForFac parent = byHash.get(prevBlockHash);
        if (parent != null) {
            return parent.getHeight() + 1;
        }
        return byHeight.isEmpty() ? 0 : byHeight.lastKey() + 1;
    }

    private void put(CachedBtcBlockForFac entry) {
        put(entry, true);
    }

    private void put(CachedBtcBlockForFac entry, boolean mayEvictByHeight) {
        CachedBtcBlockForFac existing = byHash.get(entry.getBlockHash());
        if (existing != null && existing.isComplete() && !entry.isComplete()) {
            return;
        }
        int height = entry.getHeight();
        if (mayEvictByHeight) {
            evictOldestHeightsIfNeeded(height);
        }
        byHeight.computeIfAbsent(height, h -> new HashMap<>()).put(entry.getBlockHash(), entry);
        byHash.put(entry.getBlockHash(), entry);
    }

    private void evictOldestHeightsIfNeeded(int newHeight) {
        if (byHeight.isEmpty()) {
            return;
        }
        int currentMax = byHeight.lastKey();
        if (newHeight <= currentMax) {
            return;
        }
        while (byHeight.size() >= config.getMaxHeights()) {
            Integer oldest = byHeight.firstKey();
            Map<Sha256Hash, CachedBtcBlockForFac> bucket = byHeight.remove(oldest);
            if (bucket != null) {
                for (Sha256Hash hash : bucket.keySet()) {
                    byHash.remove(hash);
                }
            }
        }
    }
}
