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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks FAC fields per block hash. Computed after validation on connect; not serialized in the block.
 * <p>
 * {@code proofType} is derived locally from the fork-balance proof's coinbase suffix against recent MM hashes.
 * {@code facSafetyLevel} uses the incremental update (same as summing the last {@link ForkBalanceFacProtocolConstants#EPOCH_LENGTH}
 * evidence values on this chain): {@code parentSafety + evidence(this) - evidence(ancestor at distance EPOCH_LENGTH)}.
 */
public class BlockFacTracker {

    private final Map<Keccak256, BlockFacFields> byHash = new ConcurrentHashMap<>();

    /**
     * Records metadata for a block that has passed validation and is about to be / has been stored.
     * Fills in any missing ancestor rows back to an already-recorded block or genesis (lazy backfill).
     * Uses an empty MM-hash list for proof-type classification (neutral/negative when a tag is present).
     */
    public void recordAfterSuccessfulValidation(BlockStore blockStore, Block block) {
        ensureChainRecorded(blockStore, block, Collections.emptyList());
    }

    /**
     * Same as {@link #recordAfterSuccessfulValidation(BlockStore, Block)} but classifies {@code proofType}
     * against {@code recentMergedMiningHashes} (the FAC MM-hash cache <em>before</em> this block is appended).
     */
    public void recordAfterSuccessfulValidation(
            BlockStore blockStore,
            Block block,
            List<Keccak256> recentMergedMiningHashes) {
        ensureChainRecorded(blockStore, block, recentMergedMiningHashes);
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
     * Ancestors without an explicit MM-hash list use an empty list for proof-type classification.
     */
    public void ensureChainRecorded(BlockStore blockStore, Block block) {
        ensureChainRecorded(blockStore, block, Collections.emptyList());
    }

    /**
     * Ensures the chain is recorded. The tip block {@code block} is classified against
     * {@code recentMergedMiningHashesForTip}; ancestors use an empty list (or previously stored rows).
     */
    public void ensureChainRecorded(
            BlockStore blockStore,
            Block block,
            List<Keccak256> recentMergedMiningHashesForTip) {
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
        for (int i = 0; i < pending.size(); i++) {
            Block b = pending.get(i);
            Block p = b.getNumber() == 0 ? null : blockStore.getBlockByHash(b.getParentHash().getBytes());
            boolean isTip = i == pending.size() - 1 && b.getHash().equals(block.getHash());
            List<Keccak256> hashes = isTip
                    ? (recentMergedMiningHashesForTip != null
                    ? recentMergedMiningHashesForTip
                    : Collections.emptyList())
                    : Collections.emptyList();
            computeAndPut(blockStore, b, p, hashes);
        }
    }

    private void computeAndPut(
            BlockStore blockStore,
            Block block,
            @Nullable Block parent,
            List<Keccak256> recentMergedMiningHashes) {
        byte proofType = FacEvidenceCalculator.proofTypeFromBlock(block, recentMergedMiningHashes);
        int evidence = FacEvidenceCalculator.facEvidenceValueFromProofType(proofType);
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
        int dropEvidence = 0;
        if (ancestorEpoch != null) {
            BlockFacFields ancestorFields = byHash.get(ancestorEpoch.getHash());
            if (ancestorFields != null) {
                dropEvidence = ancestorFields.getFacEvidenceValue();
            } else {
                dropEvidence = FacEvidenceCalculator.facEvidenceValueFromProofType(
                        FacEvidenceCalculator.proofTypeFromBlock(ancestorEpoch, Collections.emptyList()));
            }
        }
        int safety = parentSafety + evidence - dropEvidence;
        int gate = ForkBalanceFacProtocolConstants.safeLevelGateProduct();
        Keccak256 lastSafe = safety > gate ? block.getHash() : parentLastSafe;
        long rskTs = FacBlockTimestamps.rskTimestampSeconds(block);
        long btcTs = FacBlockTimestamps.btcTimestampSeconds(block);
        byHash.put(block.getHash(), new BlockFacFields(proofType, evidence, safety, lastSafe, rskTs, btcTs));
    }

    /**
     * Drops FAC rows for blocks that leave the best chain after a reorg. New-chain rows are rebuilt by
     * {@link FacBlockHashesCache#warmFromCanonicalTip} (oldest-first, with MM-hash list in sync).
     */
    public void onReorganization(BlockStore blockStore, BlockFork fork) {
        Objects.requireNonNull(blockStore, "blockStore");
        Objects.requireNonNull(fork, "fork");
        for (Block old : fork.getOldBlocks()) {
            byHash.remove(old.getHash());
        }
        for (Block neu : fork.getNewBlocks()) {
            byHash.remove(neu.getHash());
        }
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
