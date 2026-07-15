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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Best-block append must include uncle headers from {@link Block#getUncleList()} even when the uncle body
 * is absent from {@code blockStore} (deterministic MM-hash window).
 */
class FacBlockHashesCacheUncleListTest {

    @Test
    void append_includesUncleHeaderMmHash_withoutUncleBodyInStore() {
        FacBlockHashesCache cache = new FacBlockHashesCache();
        BlockFacTracker tracker = new BlockFacTracker();
        BlockStore blockStore = Mockito.mock(BlockStore.class);

        Keccak256 bestHash = hashWithFirstByte((byte) 0x10);
        Keccak256 uncleHash = hashWithFirstByte((byte) 0x20);
        Keccak256 uncleMm = hashWithFirstByte((byte) 0xaa);

        BlockHeader uncleHeader = Mockito.mock(BlockHeader.class);
        when(uncleHeader.getHash()).thenReturn(uncleHash);
        when(uncleHeader.getHashForMergedMining()).thenReturn(uncleMm.getBytes());
        when(uncleHeader.getParentHash()).thenReturn(Keccak256.ZERO_HASH);
        when(uncleHeader.getNumber()).thenReturn(1L);
        when(uncleHeader.getTimestamp()).thenReturn(5_000L);
        when(uncleHeader.getBitcoinMergedMiningHeader()).thenReturn(new byte[80]);
        when(uncleHeader.getVersion()).thenReturn((byte) 0x02);
        when(uncleHeader.getForkBalanceProof()).thenReturn(null);

        Block best = mockBestBlock(bestHash, 2, 5_100, List.of(uncleHeader));
        tracker.putForTests(bestHash, new BlockFacFields((byte) 2, 0, 0, null, 5_100, 5_000));
        when(blockStore.getBlockByHash(any())).thenReturn(null);
        when(blockStore.getBlockByHash(bestHash.getBytes())).thenReturn(best);

        // Uncle body deliberately missing from store
        when(blockStore.getBlockByHash(uncleHash.getBytes())).thenReturn(null);

        cache.appendAfterSuccessfulValidation(tracker, blockStore, best, null);

        List<Keccak256> hashes = cache.getMergedMiningHashesForProofType();
        Assertions.assertTrue(hashes.contains(new Keccak256(best.getHeader().getHashForMergedMining())));
        Assertions.assertTrue(hashes.contains(uncleMm), "uncle MM hash must come from uncle list header");
    }

    private static Keccak256 hashWithFirstByte(byte b) {
        byte[] bytes = new byte[32];
        bytes[0] = b;
        return new Keccak256(bytes);
    }

    private static Block mockBestBlock(Keccak256 hash, long number, long rskTs, List<BlockHeader> uncles) {
        Block block = Mockito.mock(Block.class);
        BlockHeader header = Mockito.mock(BlockHeader.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getHeader()).thenReturn(header);
        when(block.getParentHash()).thenReturn(Keccak256.ZERO_HASH);
        when(header.getHashForMergedMining()).thenReturn(hash.getBytes());
        when(header.getTimestamp()).thenReturn(rskTs);
        when(header.getVersion()).thenReturn((byte) 0x02);
        when(header.getForkBalanceProof()).thenReturn(null);
        byte[] btcHeader = new byte[80];
        co.rsk.bitcoinj.core.Utils.uint32ToByteArrayLE(5_000, btcHeader, 68);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(btcHeader);
        when(block.getUncleList()).thenReturn(uncles);
        return block;
    }
}
