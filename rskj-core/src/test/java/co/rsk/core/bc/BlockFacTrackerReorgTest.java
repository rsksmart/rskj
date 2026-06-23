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
import co.rsk.core.BlockDifficulty;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

class BlockFacTrackerReorgTest {

    private static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);
    private static final BlockFactory BLOCK_FACTORY = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    void onReorganization_recomputesNewBranchAndDropsOldTip() {
        BlockStore store = createBlockStore();
        BlockGenerator gen = new BlockGenerator();
        Block genesis = gen.getGenesisBlock();
        Block parent = gen.createChildBlock(genesis);
        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(parent, TEST_DIFFICULTY, true);

        Block oldTip = gen.createChildBlock(parent, 0, 1);
        Block newForkChild = gen.createChildBlock(parent, 0, 2);
        Block newTip = gen.createChildBlock(newForkChild, 0, 2);
        store.saveBlock(oldTip, TEST_DIFFICULTY, true);
        store.saveBlock(newForkChild, TEST_DIFFICULTY, true);
        store.saveBlock(newTip, TEST_DIFFICULTY, true);

        BlockFacTracker tracker = new BlockFacTracker();
        tracker.ensureChainRecorded(store, oldTip);
        Assertions.assertNotNull(tracker.get(oldTip.getHash()));
        Assertions.assertNull(tracker.get(newTip.getHash()));

        BlockFork fork = new BlockchainBranchComparator(store).calculateFork(oldTip, newTip);
        tracker.onReorganization(store, fork);

        Assertions.assertNull(tracker.get(oldTip.getHash()));
        Assertions.assertNotNull(tracker.get(newForkChild.getHash()));
        Assertions.assertNotNull(tracker.get(newTip.getHash()));
    }

    private static BlockStore createBlockStore() {
        return new IndexedBlockStore(BLOCK_FACTORY, new HashMapDB(), new HashMapBlocksIndex());
    }
}
