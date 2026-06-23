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

import java.util.Objects;

/**
 * One row of the fork-balance FAC block hash cache: merged-mining header hashes (comparable to the 32-byte
 * value after the RSK tag in the BTC coinbase), plus FAC metrics and timestamps for retention.
 */
public final class FacBlockHashEntry {

    private final long blockHeight;
    private final Keccak256 blockHash;
    private final Keccak256 blockMergedMiningHash;
    private final Keccak256 parentMergedMiningHash;
    private final int facEvidenceValue;
    private final int facSafetyLevel;
    private final long rskTimestampSeconds;
    private final long btcTimestampSeconds;

    public FacBlockHashEntry(
            long blockHeight,
            Keccak256 blockHash,
            Keccak256 blockMergedMiningHash,
            Keccak256 parentMergedMiningHash,
            int facEvidenceValue,
            int facSafetyLevel,
            long rskTimestampSeconds,
            long btcTimestampSeconds) {
        this.blockHeight = blockHeight;
        this.blockHash = Objects.requireNonNull(blockHash, "blockHash");
        this.blockMergedMiningHash = Objects.requireNonNull(blockMergedMiningHash);
        this.parentMergedMiningHash = Objects.requireNonNull(parentMergedMiningHash);
        this.facEvidenceValue = facEvidenceValue;
        this.facSafetyLevel = facSafetyLevel;
        this.rskTimestampSeconds = rskTimestampSeconds;
        this.btcTimestampSeconds = btcTimestampSeconds;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public Keccak256 getBlockHash() {
        return blockHash;
    }

    public Keccak256 getBlockMergedMiningHash() {
        return blockMergedMiningHash;
    }

    public Keccak256 getParentMergedMiningHash() {
        return parentMergedMiningHash;
    }

    public int getFacEvidenceValue() {
        return facEvidenceValue;
    }

    public int getFacSafetyLevel() {
        return facSafetyLevel;
    }

    public long getRskTimestampSeconds() {
        return rskTimestampSeconds;
    }

    public long getBtcTimestampSeconds() {
        return btcTimestampSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FacBlockHashEntry that = (FacBlockHashEntry) o;
        return blockHeight == that.blockHeight
                && facEvidenceValue == that.facEvidenceValue
                && facSafetyLevel == that.facSafetyLevel
                && rskTimestampSeconds == that.rskTimestampSeconds
                && btcTimestampSeconds == that.btcTimestampSeconds
                && blockHash.equals(that.blockHash)
                && blockMergedMiningHash.equals(that.blockMergedMiningHash)
                && parentMergedMiningHash.equals(that.parentMergedMiningHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                blockHeight,
                blockHash,
                blockMergedMiningHash,
                parentMergedMiningHash,
                facEvidenceValue,
                facSafetyLevel,
                rskTimestampSeconds,
                btcTimestampSeconds);
    }
}
