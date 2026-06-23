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
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FacBlockHashesCacheEvictionTest {

    @Test
    void append_evictsRowsOlderThanBtcTailMinusRskip179AndDelay() {
        FacBlockHashesCache cache = new FacBlockHashesCache();
        BlockFacTracker tracker = new BlockFacTracker();
        BlockStore blockStore = Mockito.mock(BlockStore.class);

        long btcTail = 10_000L;
        long threshold = FacBlockCacheEviction.rskTimestampEvictionThreshold(btcTail);

        Keccak256 oldHash = hashWithFirstByte((byte) 0x01);
        Keccak256 keepHash = hashWithFirstByte((byte) 0x02);
        Keccak256 connectingHash = hashWithFirstByte((byte) 0x03);

        cache.addEntryForTests(entry(oldHash, 120, threshold - 10, btcTail));
        cache.addEntryForTests(entry(keepHash, 121, threshold + 10, btcTail));
        tracker.putForTests(oldHash, new BlockFacFields(0, 0, null, threshold - 10, btcTail));
        tracker.putForTests(keepHash, new BlockFacFields(0, 0, null, threshold + 10, btcTail));
        tracker.putForTests(connectingHash, new BlockFacFields(0, 0, null, threshold + 100, btcTail));

        Block connecting = mockBlock(connectingHash, 150, threshold + 100, btcTail);
        when(blockStore.getBlockByHash(any())).thenReturn(null);
        when(blockStore.getBlockByHash(connectingHash.getBytes())).thenReturn(connecting);

        cache.appendAfterSuccessfulValidation(tracker, blockStore, connecting, null);

        Assertions.assertEquals(btcTail, cache.getLastBtcTailTimestampSeconds());
        Assertions.assertFalse(cache.getMergedMiningHashesForProofType().contains(oldHash));
        Assertions.assertTrue(cache.getMergedMiningHashesForProofType().contains(keepHash));
        Assertions.assertNull(tracker.get(oldHash));
        Assertions.assertNotNull(tracker.get(keepHash));
    }

    @Test
    void append_doesNotDuplicateSameBlockHash() {
        FacBlockHashesCache cache = new FacBlockHashesCache();
        BlockFacTracker tracker = new BlockFacTracker();
        BlockStore blockStore = Mockito.mock(BlockStore.class);

        Keccak256 hash = hashWithFirstByte((byte) 0x0a);
        Block block = mockBlock(hash, 1, 5_000, 5_000);
        tracker.putForTests(hash, new BlockFacFields(0, 0, null, 5_000, 5_000));
        when(blockStore.getBlockByHash(any())).thenReturn(null);
        when(blockStore.getBlockByHash(hash.getBytes())).thenReturn(block);

        cache.appendAfterSuccessfulValidation(tracker, blockStore, block, null);
        int sizeAfterFirst = cache.getMergedMiningHashesForProofType().size();
        cache.appendAfterSuccessfulValidation(tracker, blockStore, block, null);
        Assertions.assertEquals(sizeAfterFirst, cache.getMergedMiningHashesForProofType().size());
    }

    private static FacBlockHashEntry entry(Keccak256 hash, long height, long rskTs, long btcTs) {
        return new FacBlockHashEntry(
                height, hash, hash, Keccak256.ZERO_HASH, 0, 0, rskTs, btcTs);
    }

    private static Keccak256 hashWithFirstByte(byte b) {
        byte[] bytes = new byte[32];
        bytes[0] = b;
        return new Keccak256(bytes);
    }

    private static Block mockBlock(Keccak256 hash, long number, long rskTs, long btcTs) {
        Block block = Mockito.mock(Block.class);
        BlockHeader header = Mockito.mock(BlockHeader.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getHeader()).thenReturn(header);
        when(header.getHashForMergedMining()).thenReturn(hash.getBytes());
        when(header.getTimestamp()).thenReturn(rskTs);
        byte[] btcHeader = new byte[80];
        co.rsk.bitcoinj.core.Utils.uint32ToByteArrayLE((int) btcTs, btcHeader, 68);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(btcHeader);
        when(block.getUncleList()).thenReturn(java.util.Collections.emptyList());
        return block;
    }
}
