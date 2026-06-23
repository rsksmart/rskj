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

import co.rsk.crypto.Keccak256;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Time-retained cache of recent validated main-chain and uncle blocks with merged-mining hashes and FAC fields.
 * Updated when a block becomes the new best chain head in {@link BlockChainImpl#tryToConnect(org.ethereum.core.Block)}
 * (and on startup / reorg replay via {@link #warmFromCanonicalTip}).
 * <p>
 * Rows are evicted when {@code rskTimestamp < BTC_TAIL(connecting) - 300s - DELAY_PARAMETER} (see
 * {@link FacBlockCacheEviction}).
 */
public final class FacBlockHashesCache {

    private final long delayParameterSeconds;
    private final Deque<FacBlockHashEntry> facBlockHashes = new ArrayDeque<>();

    /** {@code BTC_TAIL} from the latest cache update ({@link FacBlockCacheEviction#btcTailTimestampSeconds}). */
    private volatile long lastBtcTailTimestampSeconds;

    public FacBlockHashesCache() {
        this(ForkBalanceFacProtocolConstants.DEFAULT_DELAY_PARAMETER_SECONDS);
    }

    public FacBlockHashesCache(long delayParameterSeconds) {
        if (delayParameterSeconds < 0) {
            throw new IllegalArgumentException("delayParameterSeconds must be >= 0");
        }
        this.delayParameterSeconds = delayParameterSeconds;
    }

    /**
     * Seeds {@link #getMergedMiningHashesForProofType()} for unit tests (fork-balance proof type 0).
     */
    @VisibleForTesting
    public synchronized void seedMergedMiningHashesForTests(Keccak256... hashes) {
        facBlockHashes.clear();
        if (hashes == null) {
            return;
        }
        for (Keccak256 hash : hashes) {
            if (hash != null) {
                facBlockHashes.addLast(new FacBlockHashEntry(
                        0,
                        Keccak256.ZERO_HASH,
                        hash,
                        Keccak256.ZERO_HASH,
                        0,
                        0,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE));
            }
        }
    }

    @VisibleForTesting
    synchronized void addEntryForTests(FacBlockHashEntry entry) {
        facBlockHashes.addLast(entry);
    }

    public long getLastBtcTailTimestampSeconds() {
        return lastBtcTailTimestampSeconds;
    }

    public synchronized List<Keccak256> getMergedMiningHashesForProofType() {
        if (facBlockHashes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Keccak256> out = new ArrayList<>(facBlockHashes.size());
        for (FacBlockHashEntry e : facBlockHashes) {
            out.add(e.getBlockMergedMiningHash());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Rebuilds the MM-hash deque after node restart by replaying recent canonical history in connect order.
     * <p>
     * Walks the best chain backward from {@code bestBlock} through the retention window, then replays each main-chain
     * block (and its uncles loaded from {@code blockStore}) via {@link #appendAfterSuccessfulValidation}.
     * {@code tracker} must already cover the replayed chain (see {@link BlockFacTracker#ensureChainRecorded}).
     */
    public synchronized void warmFromCanonicalTip(
            BlockFacTracker tracker,
            BlockStore blockStore,
            Block bestBlock) {
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(blockStore, "blockStore");
        Objects.requireNonNull(bestBlock, "bestBlock");

        facBlockHashes.clear();
        lastBtcTailTimestampSeconds = 0;

        List<Block> mainChainOldestFirst = collectRecentCanonicalMainChain(blockStore, bestBlock);
        for (Block block : mainChainOldestFirst) {
            Block parent = block.getNumber() == 0
                    ? null
                    : blockStore.getBlockByHash(block.getParentHash().getBytes());
            appendAfterSuccessfulValidation(tracker, blockStore, block, parent);
        }
    }

    /**
     * Main-chain blocks from oldest to newest that should be replayed when warming at {@code bestBlock}.
     */
    @VisibleForTesting
    List<Block> collectRecentCanonicalMainChain(BlockStore blockStore, Block bestBlock) {
        long tipBtcTail = FacBlockTimestamps.btcTimestampSeconds(bestBlock);
        long rskEvictionThreshold = FacBlockCacheEviction.rskTimestampEvictionThreshold(
                tipBtcTail, delayParameterSeconds);

        List<Block> newestFirst = new ArrayList<>();
        Block cur = bestBlock;
        while (cur != null && newestFirst.size() < ForkBalanceFacProtocolConstants.BLOCK_HASHES_LIST_SIZE) {
            if (!newestFirst.isEmpty()
                    && rskEvictionThreshold != Long.MIN_VALUE
                    && FacBlockTimestamps.rskTimestampSeconds(cur) < rskEvictionThreshold) {
                break;
            }
            newestFirst.add(cur);
            if (cur.getNumber() == 0) {
                break;
            }
            cur = blockStore.getBlockByHash(cur.getParentHash().getBytes());
        }

        List<Block> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    /**
     * Appends the connected {@code block} and any known uncle blocks (loaded from {@code blockStore}) after FAC rows
     * exist in {@code tracker}, then applies timestamp-based eviction.
     */
    public synchronized void appendAfterSuccessfulValidation(
            BlockFacTracker tracker,
            BlockStore blockStore,
            Block connectingBlock,
            @Nullable Block parent) {
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(blockStore, "blockStore");
        Objects.requireNonNull(connectingBlock, "connectingBlock");
        appendOne(tracker, blockStore, connectingBlock, parent);
        for (BlockHeader uh : connectingBlock.getUncleList()) {
            Block uncleBlock = blockStore.getBlockByHash(uh.getHash().getBytes());
            if (uncleBlock == null) {
                continue;
            }
            Block uncleParent = uncleBlock.getNumber() == 0
                    ? null
                    : blockStore.getBlockByHash(uncleBlock.getParentHash().getBytes());
            tracker.ensureChainRecorded(blockStore, uncleBlock);
            appendOne(tracker, blockStore, uncleBlock, uncleParent);
        }
        evictStaleEntries(tracker, connectingBlock);
    }

    private void evictStaleEntries(BlockFacTracker tracker, Block connectingBlock) {
        long btcTail = FacBlockCacheEviction.btcTailTimestampSeconds(facBlockHashes, connectingBlock);
        lastBtcTailTimestampSeconds = btcTail;
        long threshold = FacBlockCacheEviction.rskTimestampEvictionThreshold(btcTail, delayParameterSeconds);
        if (threshold == Long.MIN_VALUE) {
            return;
        }
        Iterator<FacBlockHashEntry> it = facBlockHashes.iterator();
        while (it.hasNext()) {
            if (it.next().getRskTimestampSeconds() < threshold) {
                it.remove();
            }
        }
        tracker.evictEntriesWithRskTimestampBelow(threshold);
    }

    private void appendOne(BlockFacTracker tracker, BlockStore blockStore, Block block, @Nullable Block parent) {
        Keccak256 blockHash = block.getHash();
        for (FacBlockHashEntry existing : facBlockHashes) {
            if (existing.getBlockHash().equals(blockHash)) {
                return;
            }
        }
        tracker.ensureChainRecorded(blockStore, block);
        BlockFacFields fields = tracker.get(blockHash);
        if (fields == null) {
            return;
        }
        Keccak256 blockMm = new Keccak256(block.getHeader().getHashForMergedMining());
        Keccak256 parentMm = parent != null
                ? new Keccak256(parent.getHeader().getHashForMergedMining())
                : Keccak256.ZERO_HASH;
        FacBlockHashEntry entry = new FacBlockHashEntry(
                block.getNumber(),
                blockHash,
                blockMm,
                parentMm,
                fields.getFacEvidenceValue(),
                fields.getFacSafetyLevel(),
                fields.getRskTimestampSeconds(),
                fields.getBtcTimestampSeconds());
        facBlockHashes.addLast(entry);
    }
}
