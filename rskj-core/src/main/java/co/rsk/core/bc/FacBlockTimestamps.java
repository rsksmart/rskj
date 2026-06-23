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

import co.rsk.bitcoinj.core.Utils;
import org.ethereum.core.Block;

/**
 * RSK and merged-mining BTC timestamps used for FAC cache retention.
 */
public final class FacBlockTimestamps {

    /** BTC block header time field offset (uint32 LE) in the 80-byte wire header. */
    private static final int BTC_HEADER_TIME_OFFSET = 68;

    private FacBlockTimestamps() {
    }

    public static long rskTimestampSeconds(Block block) {
        return block.getHeader().getTimestamp();
    }

    /**
     * @return BTC merged-mining header time in seconds, or {@code 0} when absent or invalid
     */
    public static long btcTimestampSeconds(Block block) {
        byte[] header80 = block.getHeader().getBitcoinMergedMiningHeader();
        return btcTimestampFromHeader80(header80);
    }

    public static long btcTimestampFromHeader80(byte[] header80) {
        if (header80 == null || header80.length < BTC_HEADER_TIME_OFFSET + 4) {
            return 0;
        }
        return Utils.readUint32(header80, BTC_HEADER_TIME_OFFSET);
    }
}
