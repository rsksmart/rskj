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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.crypto.Keccak256;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Startup replay of {@link FacBlockHashesCache} from the canonical tip.
 */
class FacBlockHashesCacheWarmupTest {

    @Test
    void warmFromCanonicalTip_afterSimulatedRestart_restoresMergedMiningHashes() {
        BlockFacTracker tracker = new BlockFacTracker();
        FacBlockHashesCache liveCache = new FacBlockHashesCache();
        BlockChainBuilder builder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(tracker)
                .setFacBlockHashesCache(liveCache);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator();
        Block block1 = gen.createChildBlock(genesis);
        builder.getBlockExecutor().executeAndFillAll(block1, genesis.getHeader());
        block1.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block1));

        Block block2 = gen.createChildBlock(block1);
        builder.getBlockExecutor().executeAndFillAll(block2, block1.getHeader());
        block2.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block2));

        List<Keccak256> liveHashes = liveCache.getMergedMiningHashesForProofType();
        Assertions.assertFalse(liveHashes.isEmpty());

        BlockStore blockStore = builder.getBlockStore();
        Block best = chain.getBestBlock();
        tracker.ensureChainRecorded(blockStore, best);

        FacBlockHashesCache restartedCache = new FacBlockHashesCache();
        restartedCache.warmFromCanonicalTip(tracker, blockStore, best);

        Assertions.assertEquals(liveHashes, restartedCache.getMergedMiningHashesForProofType());
        Assertions.assertEquals(
                liveCache.getLastBtcTailTimestampSeconds(),
                restartedCache.getLastBtcTailTimestampSeconds());
    }

    @Test
    void collectRecentCanonicalMainChain_stopsBeforeRetentionWindow() {
        BlockFacTracker tracker = new BlockFacTracker();
        FacBlockHashesCache cache = new FacBlockHashesCache();
        BlockChainBuilder builder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(tracker)
                .setFacBlockHashesCache(cache);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator();
        Block prev = genesis;
        for (int i = 0; i < 5; i++) {
            Block child = gen.createChildBlock(prev);
            builder.getBlockExecutor().executeAndFillAll(child, prev.getHeader());
            child.seal();
            Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(child));
            prev = child;
        }

        Block best = chain.getBestBlock();
        List<Block> collected = cache.collectRecentCanonicalMainChain(builder.getBlockStore(), best);
        Assertions.assertFalse(collected.isEmpty());
        Assertions.assertEquals(genesis.getHash(), collected.get(0).getHash());
        Assertions.assertEquals(best.getHash(), collected.get(collected.size() - 1).getHash());
    }
}
