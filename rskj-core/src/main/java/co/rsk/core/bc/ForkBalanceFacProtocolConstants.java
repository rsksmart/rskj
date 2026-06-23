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

/**
 * Protocol parameters for fork-balance FAC (fork-awareness) metadata computed after block validation.
 */
public final class ForkBalanceFacProtocolConstants {

    /** Sliding window length for {@code facSafetyLevel}. */
    public static final int EPOCH_LENGTH = 100;

    /** Capacity hint for {@link FacBlockHashesCache}; retention is time-based, not size-based. */
    public static final int BLOCK_HASHES_LIST_SIZE = 4 * EPOCH_LENGTH;

    /** Suffix of the parent BTC coinbase carried in fork-balance proofs (RSK tag fits in this window). */
    public static final int PARENT_COINBASE_SUFFIX_MAX_BYTES = 128;

    /**
     * RSKIP-179 bound on BTC vs RSK header timestamps (see {@link org.ethereum.config.Constants}).
     */
    public static final long BTC_RSK_TIMESTAMP_MAX_DIFF_SECONDS = 300L;

    /**
     * Default extra retention margin when evicting FAC cache rows ({@code BTC_TAIL - RSKIP179 - DELAY}).
     * Override via {@code miner.forkBalance.facCache.delayParameterSeconds}.
     */
    public static final long DEFAULT_DELAY_PARAMETER_SECONDS = 60L;

    /**
     * Gate for advancing {@code lastSafeBlock}: {@code facSafetyLevel > SAFE_BLOCK_THRESHOLD * EPOCH_LENGTH}.
     */
    public static final int SAFE_BLOCK_THRESHOLD = 0;

    private ForkBalanceFacProtocolConstants() {
    }

    public static int safeLevelGateProduct() {
        return SAFE_BLOCK_THRESHOLD * EPOCH_LENGTH;
    }
}
