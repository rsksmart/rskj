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
 * Time-retained cache of recent <strong>best-chain</strong> blocks and their uncle headers with merged-mining hashes
 * and FAC metadata (including locally derived {@code proofType}).
 * Updated when a block becomes the new best chain head in {@link BlockChainImpl#tryToConnect(org.ethereum.core.Block)}
 * (and on startup / reorg replay via {@link #warmFromCanonicalTip}).
 * <p>
 * Uncles are taken from {@link Block#getUncleList()} (full headers already on the connecting block), not from optional
 * bodies in {@code blockStore}, so every node that imported the same best block shares the same MM-hash window.
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
     * Seeds {@link #getMergedMiningHashesForProofType()} for unit tests of local proof-type classification.
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
                        (byte) 2,
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
        List<Keccak256> snapshot = snapshotMergedMiningHashes();
        return snapshot.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(snapshot);
    }

    /** Caller must already hold {@code this}'s monitor. */
    private List<Keccak256> snapshotMergedMiningHashes() {
        if (facBlockHashes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Keccak256> out = new ArrayList<>(facBlockHashes.size());
        for (FacBlockHashEntry e : facBlockHashes) {
            out.add(e.getBlockMergedMiningHash());
        }
        return out;
    }

    /**
     * Rebuilds the MM-hash deque after node restart / reorg by replaying recent canonical history in connect order.
     * <p>
     * Walks the best chain backward from {@code bestBlock} through the retention window, then replays each main-chain
     * block (and its uncle <em>headers</em> from {@link Block#getUncleList()}) via {@link #appendAfterSuccessfulValidation}.
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

        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * Appends the new best {@code connectingBlock} and each uncle header from its uncle list after recording FAC
     * metadata (proof type derived against the cache <em>before</em> this append), then applies timestamp eviction.
     */
    public synchronized void appendAfterSuccessfulValidation(
            BlockFacTracker tracker,
            BlockStore blockStore,
            Block connectingBlock,
            @Nullable Block parent) {
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(blockStore, "blockStore");
        Objects.requireNonNull(connectingBlock, "connectingBlock");

        List<Keccak256> hashesBeforeAppend = snapshotMergedMiningHashes();
        tracker.recordAfterSuccessfulValidation(blockStore, connectingBlock, hashesBeforeAppend);
        appendMainChainBlock(tracker, connectingBlock, parent);

        for (BlockHeader uh : connectingBlock.getUncleList()) {
            appendUncleHeader(uh, hashesBeforeAppend);
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

    private void appendMainChainBlock(BlockFacTracker tracker, Block block, @Nullable Block parent) {
        Keccak256 blockHash = block.getHash();
        for (FacBlockHashEntry existing : facBlockHashes) {
            if (existing.getBlockHash().equals(blockHash)) {
                return;
            }
        }
        BlockFacFields fields = tracker.get(blockHash);
        if (fields == null) {
            return;
        }
        Keccak256 blockMm = new Keccak256(block.getHeader().getHashForMergedMining());
        Keccak256 parentMm = parent != null
                ? new Keccak256(parent.getHeader().getHashForMergedMining())
                : resolveParentMergedMiningHash(block.getParentHash());
        FacBlockHashEntry entry = new FacBlockHashEntry(
                block.getNumber(),
                blockHash,
                blockMm,
                parentMm,
                fields.getProofType(),
                fields.getFacEvidenceValue(),
                fields.getFacSafetyLevel(),
                fields.getRskTimestampSeconds(),
                fields.getBtcTimestampSeconds());
        facBlockHashes.addLast(entry);
    }

    /**
     * Adds an uncle from the connecting block's uncle list. Uses the uncle {@link BlockHeader} directly
     * ({@link BlockHeader#getHashForMergedMining()} matches the RSK-tag payload).
     */
    private void appendUncleHeader(BlockHeader uncleHeader, List<Keccak256> hashesBeforeConnectingBlock) {
        Keccak256 blockHash = uncleHeader.getHash();
        for (FacBlockHashEntry existing : facBlockHashes) {
            if (existing.getBlockHash().equals(blockHash)) {
                return;
            }
        }
        // Classify uncle against the cache as it stood when the nephew became best (same for all nodes).
        byte proofType = FacEvidenceCalculator.proofTypeFromHeader(uncleHeader, hashesBeforeConnectingBlock);
        int evidence = FacEvidenceCalculator.facEvidenceValueFromProofType(proofType);
        Keccak256 blockMm = new Keccak256(uncleHeader.getHashForMergedMining());
        Keccak256 parentMm = resolveParentMergedMiningHash(uncleHeader.getParentHash());
        long rskTs = uncleHeader.getTimestamp();
        long btcTs = FacBlockTimestamps.btcTimestampFromHeader80(uncleHeader.getBitcoinMergedMiningHeader());
        FacBlockHashEntry entry = new FacBlockHashEntry(
                uncleHeader.getNumber(),
                blockHash,
                blockMm,
                parentMm,
                proofType,
                evidence,
                0,
                rskTs,
                btcTs);
        facBlockHashes.addLast(entry);
    }

    private Keccak256 resolveParentMergedMiningHash(Keccak256 parentHash) {
        for (FacBlockHashEntry e : facBlockHashes) {
            if (e.getBlockHash().equals(parentHash)) {
                return e.getBlockMergedMiningHash();
            }
        }
        return Keccak256.ZERO_HASH;
    }
}
