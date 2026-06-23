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

import co.rsk.bitcoinj.core.Sha256Hash;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Bitcoin parent block (BTCB) chosen for a new mining work unit, and how it was resolved.
 * <p>
 * {@link Source#RPC_TIP}: live chain tip from bitcoind.
 * {@link Source#CACHE_FALLBACK}: RPC unavailable; highest complete cached block used instead.
 * {@link Source#LOCAL_CACHE_ONLY}: no RPC configured; highest complete cached block.
 */
public final class BtcMiningParentResolution {

    public enum Source {
        RPC_TIP,
        CACHE_FALLBACK,
        LOCAL_CACHE_ONLY
    }

    private final CachedBtcBlockForFac parent;
    private final Source source;
    @Nullable
    private final Sha256Hash attemptedTipHash;

    public BtcMiningParentResolution(
            CachedBtcBlockForFac parent,
            Source source,
            @Nullable Sha256Hash attemptedTipHash) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.source = Objects.requireNonNull(source, "source");
        this.attemptedTipHash = attemptedTipHash;
        if (!parent.isComplete()) {
            throw new IllegalArgumentException("parent must be complete");
        }
    }

    public CachedBtcBlockForFac getParent() {
        return parent;
    }

    public Source getSource() {
        return source;
    }

    @Nullable
    public Sha256Hash getAttemptedTipHash() {
        return attemptedTipHash;
    }
}
