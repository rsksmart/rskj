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
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * FAC cache / tracker integration with {@link BlockChainImpl} best-chain and reorg rules.
 */
class FacBlockChainFacTest {

    @Test
    void sideChainImport_doesNotGrowMergedMiningCache() {
        BlockFacTracker tracker = new BlockFacTracker();
        FacBlockHashesCache cache = new FacBlockHashesCache();
        BlockChainBuilder builder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(tracker)
                .setFacBlockHashesCache(cache);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        connectChild(builder, chain, genesis, 10);
        int sizeAfterBest = cache.getMergedMiningHashesForProofType().size();

        Block block1b = prepareChild(builder, genesis, 1);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, chain.tryToConnect(block1b));
        Assertions.assertEquals(sizeAfterBest, cache.getMergedMiningHashesForProofType().size());
        Assertions.assertNull(tracker.get(block1b.getHash()));
    }

    @Test
    void reorg_rebuildsCacheAndFacFields_onNewCanonicalTip() {
        BlockFacTracker linearTracker = new BlockFacTracker();
        FacBlockHashesCache linearCache = new FacBlockHashesCache();
        BlockChainBuilder linearBuilder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(linearTracker)
                .setFacBlockHashesCache(linearCache);
        BlockChainImpl linearChain = linearBuilder.build();
        Block genesis = linearChain.getBestBlock();

        Block block1b = connectChild(linearBuilder, linearChain, genesis, 1);
        Block block2b = connectChild(linearBuilder, linearChain, block1b, 2);
        int linearCacheSize = linearCache.getMergedMiningHashesForProofType().size();
        BlockFacFields linearFac = linearChain.getBlockFacFields(block2b.getHash());

        BlockFacTracker reorgTracker = new BlockFacTracker();
        FacBlockHashesCache reorgCache = new FacBlockHashesCache();
        BlockChainBuilder reorgBuilder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(reorgTracker)
                .setFacBlockHashesCache(reorgCache);
        BlockChainImpl reorgChain = reorgBuilder.build();

        Block block1a = connectChild(reorgBuilder, reorgChain, genesis, 2);
        Block block1bFork = prepareChild(reorgBuilder, genesis, 1);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, reorgChain.tryToConnect(block1bFork));
        connectChild(reorgBuilder, reorgChain, block1bFork, 2);

        BlockFacFields reorgFac = reorgChain.getBlockFacFields(reorgChain.getBestBlock().getHash());
        Assertions.assertNotNull(linearFac);
        Assertions.assertNotNull(reorgFac);
        Assertions.assertEquals(linearFac.getFacEvidenceValue(), reorgFac.getFacEvidenceValue());
        Assertions.assertEquals(linearFac.getFacSafetyLevel(), reorgFac.getFacSafetyLevel());
        Assertions.assertEquals(linearCacheSize, reorgCache.getMergedMiningHashesForProofType().size());
        Assertions.assertNull(reorgTracker.get(block1a.getHash()));
    }

    private static Block prepareChild(BlockChainBuilder builder, Block parent, long difficulty) {
        BlockGenerator gen = new BlockGenerator();
        Block child = gen.createChildBlock(parent, 0, difficulty);
        builder.getBlockExecutor().executeAndFillAll(child, parent.getHeader());
        child.seal();
        return child;
    }

    private static Block connectChild(BlockChainBuilder builder, BlockChainImpl chain, Block parent, long difficulty) {
        Block child = prepareChild(builder, parent, difficulty);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(child));
        return child;
    }
}
