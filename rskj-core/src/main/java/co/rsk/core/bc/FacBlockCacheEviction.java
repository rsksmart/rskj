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

import org.ethereum.core.Block;

import java.util.Collection;

/**
 * Timestamp-based FAC cache retention: evict rows whose RSK timestamp falls below
 * {@code BTC_TAIL(connectingBlock) - RSKIP179_WINDOW - DELAY_PARAMETER}.
 */
public final class FacBlockCacheEviction {

    private FacBlockCacheEviction() {
    }

    public static long epochStartInclusive(long blockNumber) {
        return (blockNumber / ForkBalanceFacProtocolConstants.EPOCH_LENGTH) * ForkBalanceFacProtocolConstants.EPOCH_LENGTH;
    }

    public static long epochEndInclusive(long blockNumber) {
        return epochStartInclusive(blockNumber) + ForkBalanceFacProtocolConstants.EPOCH_LENGTH - 1;
    }

    /**
     * Lowest BTC merged-mining timestamp among cached rows whose RSK height lies in the connecting block's epoch.
     * Falls back to the connecting block's own BTC timestamp when the epoch slice has no usable BTC times.
     */
    public static long btcTailTimestampSeconds(
            Collection<FacBlockHashEntry> entries,
            Block connectingBlock) {
        long connectingBtc = FacBlockTimestamps.btcTimestampSeconds(connectingBlock);
        long epochStart = epochStartInclusive(connectingBlock.getNumber());
        long epochEnd = epochEndInclusive(connectingBlock.getNumber());
        long min = Long.MAX_VALUE;
        for (FacBlockHashEntry entry : entries) {
            long height = entry.getBlockHeight();
            if (height < epochStart || height > epochEnd) {
                continue;
            }
            long btcTs = entry.getBtcTimestampSeconds();
            if (btcTs > 0) {
                min = Math.min(min, btcTs);
            }
        }
        if (min != Long.MAX_VALUE) {
            return min;
        }
        return connectingBtc;
    }

    /**
     * RSK blocks with {@code rskTimestamp < threshold} are removed from the FAC caches.
     *
     * @return {@link Long#MIN_VALUE} when eviction must not run ({@code btcTail <= 0})
     */
    public static long rskTimestampEvictionThreshold(long btcTailSeconds, long delayParameterSeconds) {
        if (btcTailSeconds <= 0) {
            return Long.MIN_VALUE;
        }
        return btcTailSeconds
                - ForkBalanceFacProtocolConstants.BTC_RSK_TIMESTAMP_MAX_DIFF_SECONDS
                - delayParameterSeconds;
    }

    /**
     * @see #rskTimestampEvictionThreshold(long, long)
     */
    public static long rskTimestampEvictionThreshold(long btcTailSeconds) {
        return rskTimestampEvictionThreshold(
                btcTailSeconds, ForkBalanceFacProtocolConstants.DEFAULT_DELAY_PARAMETER_SECONDS);
    }
}
