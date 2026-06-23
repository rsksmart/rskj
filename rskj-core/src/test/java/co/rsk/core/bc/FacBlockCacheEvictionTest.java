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
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

class FacBlockCacheEvictionTest {

    @Test
    void rskTimestampEvictionThreshold_subtractsRskip179WindowAndDelay() {
        long btcTail = 1_000_000L;
        long expected = btcTail - 300L - 60L;
        Assertions.assertEquals(expected, FacBlockCacheEviction.rskTimestampEvictionThreshold(btcTail));
    }

    @Test
    void rskTimestampEvictionThreshold_returnsMinValueWhenBtcTailUnknown() {
        Assertions.assertEquals(Long.MIN_VALUE, FacBlockCacheEviction.rskTimestampEvictionThreshold(0));
    }

    @Test
    void btcTailTimestampSeconds_usesMinimumBtcTimeInConnectingEpoch() {
        Block connecting = mockBlock(250, 9_000, 8_000);
        List<FacBlockHashEntry> entries = Arrays.asList(
                entry(200, 7_000, 6_000),
                entry(220, 7_500, 6_500),
                entry(299, 8_500, 5_000),
                entry(350, 9_500, 8_500));
        Assertions.assertEquals(5_000L, FacBlockCacheEviction.btcTailTimestampSeconds(entries, connecting));
    }

    @Test
    void btcTailTimestampSeconds_fallsBackToConnectingBlockBtcTime() {
        Block connecting = mockBlock(10, 4_000, 3_500);
        List<FacBlockHashEntry> entries = List.of(entry(5, 3_000, 0));
        Assertions.assertEquals(3_500L, FacBlockCacheEviction.btcTailTimestampSeconds(entries, connecting));
    }

    private static FacBlockHashEntry entry(long height, long rskTs, long btcTs) {
        return new FacBlockHashEntry(
                height,
                Keccak256.ZERO_HASH,
                Keccak256.ZERO_HASH,
                Keccak256.ZERO_HASH,
                0,
                0,
                rskTs,
                btcTs);
    }

    private static Block mockBlock(long number, long rskTs, long btcTs) {
        Block block = Mockito.mock(Block.class);
        BlockHeader header = Mockito.mock(BlockHeader.class);
        Mockito.when(block.getNumber()).thenReturn(number);
        Mockito.when(block.getHeader()).thenReturn(header);
        Mockito.when(header.getTimestamp()).thenReturn(rskTs);
        byte[] btcHeader = new byte[80];
        co.rsk.bitcoinj.core.Utils.uint32ToByteArrayLE((int) btcTs, btcHeader, 68);
        Mockito.when(header.getBitcoinMergedMiningHeader()).thenReturn(btcHeader);
        return block;
    }
}
