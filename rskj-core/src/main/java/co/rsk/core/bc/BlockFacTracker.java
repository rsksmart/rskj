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
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks FAC fields per block hash. Computed after validation on connect; not serialized in the block.
 * <p>
 * {@code facSafetyLevel} uses the incremental update (same as summing the last {@link ForkBalanceFacProtocolConstants#EPOCH_LENGTH}
 * evidence values on this chain): {@code parentSafety + evidence(this) - evidence(ancestor at distance EPOCH_LENGTH)}.
 */
public class BlockFacTracker {

    private final Map<Keccak256, BlockFacFields> byHash = new ConcurrentHashMap<>();

    /**
     * Records metadata for a block that has passed validation and is about to be / has been stored.
     * Fills in any missing ancestor rows back to an already-recorded block or genesis (lazy backfill).
     */
    public void recordAfterSuccessfulValidation(BlockStore blockStore, Block block, @Nullable Block parent) {
        ensureChainRecorded(blockStore, block);
    }

    @Nullable
    public BlockFacFields get(Keccak256 blockHash) {
        return byHash.get(blockHash);
    }

    public Optional<BlockFacFields> getOptional(Keccak256 blockHash) {
        return Optional.ofNullable(byHash.get(blockHash));
    }

    /**
     * Ensures {@code block} and any missing ancestors up to genesis or an already-known block are recorded.
     */
    public void ensureChainRecorded(BlockStore blockStore, Block block) {
        if (byHash.containsKey(block.getHash())) {
            return;
        }
        List<Block> pending = new ArrayList<>();
        Block cur = block;
        while (cur != null && !byHash.containsKey(cur.getHash())) {
            pending.add(0, cur);
            if (cur.getNumber() == 0) {
                break;
            }
            cur = blockStore.getBlockByHash(cur.getParentHash().getBytes());
        }
        for (Block b : pending) {
            Block p = b.getNumber() == 0 ? null : blockStore.getBlockByHash(b.getParentHash().getBytes());
            computeAndPut(blockStore, b, p);
        }
    }

    private void computeAndPut(BlockStore blockStore, Block block, @Nullable Block parent) {
        int evidence = FacEvidenceCalculator.facEvidenceValueFromBlock(block);
        int parentSafety = 0;
        Keccak256 parentLastSafe = null;
        if (parent != null) {
            BlockFacFields pf = byHash.get(parent.getHash());
            if (pf != null) {
                parentSafety = pf.getFacSafetyLevel();
                parentLastSafe = pf.getLastSafeBlock();
            }
        }
        Block ancestorEpoch = ancestorNthParent(blockStore, block, ForkBalanceFacProtocolConstants.EPOCH_LENGTH);
        int dropEvidence = ancestorEpoch == null ? 0 : FacEvidenceCalculator.facEvidenceValueFromBlock(ancestorEpoch);
        int safety = parentSafety + evidence - dropEvidence;
        int gate = ForkBalanceFacProtocolConstants.safeLevelGateProduct();
        Keccak256 lastSafe = safety > gate ? block.getHash() : parentLastSafe;
        long rskTs = FacBlockTimestamps.rskTimestampSeconds(block);
        long btcTs = FacBlockTimestamps.btcTimestampSeconds(block);
        byHash.put(block.getHash(), new BlockFacFields(evidence, safety, lastSafe, rskTs, btcTs));
    }

    /**
     * Recomputes FAC metadata on the new canonical segment after a reorg.
     * <p>
     * Drops rows for blocks that leave the best chain and recomputes {@link BlockFork#getNewBlocks()} in order from the
     * common ancestor.
     */
    public void onReorganization(BlockStore blockStore, BlockFork fork) {
        Objects.requireNonNull(blockStore, "blockStore");
        Objects.requireNonNull(fork, "fork");
        for (Block old : fork.getOldBlocks()) {
            byHash.remove(old.getHash());
        }
        Block commonAncestor = fork.getCommonAncestor();
        for (Block block : fork.getNewBlocks()) {
            byHash.remove(block.getHash());
            Block parent = resolveParentForReorg(blockStore, block, commonAncestor);
            computeAndPut(blockStore, block, parent);
        }
    }

    @Nullable
    private static Block resolveParentForReorg(
            BlockStore blockStore,
            Block block,
            Block commonAncestor) {
        if (block.getNumber() == 0) {
            return null;
        }
        if (block.getParentHash().equals(commonAncestor.getHash())) {
            return commonAncestor;
        }
        return blockStore.getBlockByHash(block.getParentHash().getBytes());
    }

    /**
     * Drops in-memory FAC rows older than the timestamp retention window (see {@link FacBlockCacheEviction}).
     */
    public void evictEntriesWithRskTimestampBelow(long rskTimestampThreshold) {
        byHash.entrySet().removeIf(e -> e.getValue().getRskTimestampSeconds() < rskTimestampThreshold);
    }

    @com.google.common.annotations.VisibleForTesting
    void putForTests(Keccak256 blockHash, BlockFacFields fields) {
        byHash.put(blockHash, fields);
    }

    /**
     * {@code ancestorNthParent(b, 1)} is the direct parent of {@code b}.
     */
    @Nullable
    static Block ancestorNthParent(BlockStore blockStore, Block start, int n) {
        Block cur = start;
        for (int i = 0; i < n; i++) {
            byte[] ph = cur.getParentHash().getBytes();
            cur = blockStore.getBlockByHash(ph);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }
}
