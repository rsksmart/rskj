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

import javax.annotation.Nullable;

/**
 * Post-connect metadata derived from fork-balance proof type (not part of the canonical block wire format).
 */
public final class BlockFacFields {

    private final int facEvidenceValue;
    private final int facSafetyLevel;
    @Nullable
    private final Keccak256 lastSafeBlock;
    private final long rskTimestampSeconds;
    private final long btcTimestampSeconds;

    public BlockFacFields(
            int facEvidenceValue,
            int facSafetyLevel,
            @Nullable Keccak256 lastSafeBlock,
            long rskTimestampSeconds,
            long btcTimestampSeconds) {
        this.facEvidenceValue = facEvidenceValue;
        this.facSafetyLevel = facSafetyLevel;
        this.lastSafeBlock = lastSafeBlock;
        this.rskTimestampSeconds = rskTimestampSeconds;
        this.btcTimestampSeconds = btcTimestampSeconds;
    }

    public int getFacEvidenceValue() {
        return facEvidenceValue;
    }

    public int getFacSafetyLevel() {
        return facSafetyLevel;
    }

    @Nullable
    public Keccak256 getLastSafeBlock() {
        return lastSafeBlock;
    }

    public long getRskTimestampSeconds() {
        return rskTimestampSeconds;
    }

    public long getBtcTimestampSeconds() {
        return btcTimestampSeconds;
    }
}
