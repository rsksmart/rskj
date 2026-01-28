/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NetBlockStore;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 19/08/2016.
 */
class BlockUtilsTest {
    @Test
    void blockInSomeBlockChain() {
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        BlockChainImpl blockChain = blockChainBuilder.build();
        org.ethereum.db.BlockStore blockStore = blockChainBuilder.getBlockStore();

        Block genesis = blockChain.getBestBlock();

        Block block1 = new BlockBuilder(null, null, null).parent(genesis).build();
        Block block1b = new BlockBuilder(null, null, null).parent(genesis).build();
        Block block2 = new BlockBuilder(null, null, null).parent(block1).build();
        Block block3 = new BlockBuilder(null, null, null).parent(block2).build();
        blockStore.saveBlock(block3, new BlockDifficulty(BigInteger.ONE), false);

        blockChain.tryToConnect(block1);
        blockChain.tryToConnect(block1b);

        assertTrue(BlockUtils.blockInSomeBlockChain(genesis, blockChain));
        assertTrue(BlockUtils.blockInSomeBlockChain(block1, blockChain));
        assertTrue(BlockUtils.blockInSomeBlockChain(block1b, blockChain));
        assertFalse(BlockUtils.blockInSomeBlockChain(block2, blockChain));
        assertTrue(BlockUtils.blockInSomeBlockChain(block3, blockChain));
    }

    @Test
    void unknowAncestorsHashes() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        NetBlockStore store = new NetBlockStore();

        Block genesis = blockChain.getBestBlock();

        Block block1 = new BlockBuilder(null, null, null).difficulty(2l).parent(genesis).build();
        Block block1b = new BlockBuilder(null, null, null).difficulty(1l).parent(genesis).build();
        Block block2 = new BlockBuilder(null, null, null).parent(block1).build();
        Block block3 = new BlockBuilder(null, null, null).parent(block2).build();

        store.saveBlock(block3);

        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        Set<Keccak256> hashes = BlockUtils.unknownAncestorsHashes(genesis.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1b.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block2.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertFalse(hashes.isEmpty());
        assertEquals(1, hashes.size());
        assertTrue(hashes.contains(block2.getHash()));

        hashes = BlockUtils.unknownAncestorsHashes(block3.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertFalse(hashes.isEmpty());
        assertEquals(1, hashes.size());
        assertTrue(hashes.contains(block2.getHash()));
    }

    @Test
    void unknowAncestorsHashesUsingUncles() {
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        BlockChainImpl blockChain = blockChainBuilder.build();
        Genesis genesis = (Genesis) blockChain.getBestBlock();
        NetBlockStore store = new NetBlockStore();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, null,
                                                     blockChainBuilder.getBlockStore()
        ).trieStore(blockChainBuilder.getTrieStore());
        blockBuilder.parent(blockChain.getBestBlock());
        Block block1 = blockBuilder.parent(genesis).build();
        Block block1b = blockBuilder.parent(genesis).build();
        Block block2 = blockBuilder.parent(block1).build();
        Block uncle1 = blockBuilder.parent(block1).build();
        Block uncle2 = blockBuilder.parent(block1).build();
        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());
        Block block3 = blockBuilder.parent(block2).uncles(uncles).build();

        store.saveBlock(block3);

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(block1);
        blockChain.tryToConnect(block1b);

        Set<Keccak256> hashes = BlockUtils.unknownAncestorsHashes(genesis.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1b.getHash(), blockChain, store);

        assertNotNull(hashes);

        assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block2.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertFalse(hashes.isEmpty());
        assertEquals(1, hashes.size());
        assertTrue(hashes.contains(block2.getHash()));

        hashes = BlockUtils.unknownAncestorsHashes(block3.getHash(), blockChain, store);

        assertNotNull(hashes);
        assertFalse(hashes.isEmpty());
        assertEquals(3, hashes.size());
        assertTrue(hashes.contains(block2.getHash()));
        assertTrue(hashes.contains(uncle1.getHash()));
        assertTrue(hashes.contains(uncle2.getHash()));
    }

    @Test
    void tooMuchProcessTime() {
        assertFalse(BlockUtils.tooMuchProcessTime(0));
        assertFalse(BlockUtils.tooMuchProcessTime(1000));
        assertFalse(BlockUtils.tooMuchProcessTime(1_000_000L));
        assertFalse(BlockUtils.tooMuchProcessTime(1_000_000_000L));
        assertFalse(BlockUtils.tooMuchProcessTime(60_000_000_000L));

        assertTrue(BlockUtils.tooMuchProcessTime(60_000_000_001L));
        assertTrue(BlockUtils.tooMuchProcessTime(1_000_000_000_000L));
    }

    @Test
    void sublistGasLimit_ShouldDivideGasLimitEquallyAmongAllSets() {
        long minSequentialSetGasLimit = Constants.regtest().getMinSequentialSetGasLimit();
        long mockedBlockGasLimit =  9_000_000L;
        Block block = mock(Block.class);
        when(block.getGasLimit()).thenReturn(BigInteger.valueOf(mockedBlockGasLimit).toByteArray());

        long expectedLimit = mockedBlockGasLimit / (Constants.getTransactionExecutionThreads() + 1);
        assertEquals(expectedLimit, BlockUtils.getSublistGasLimit(block, false, minSequentialSetGasLimit));
        assertEquals(expectedLimit, BlockUtils.getSublistGasLimit(block, true, minSequentialSetGasLimit));
    }

    @Test
    void sublistGasLimit_ShouldAssignLessGasLimitToParallelSets() {
        long minSequentialSetGasLimit = 6_800_000L;
        long mockedBlockGasLimit =  10_000_000L;
        Block block = mock(Block.class);
        when(block.getGasLimit()).thenReturn(BigInteger.valueOf(mockedBlockGasLimit).toByteArray());

        assertEquals(minSequentialSetGasLimit, BlockUtils.getSublistGasLimit(block, true, minSequentialSetGasLimit));

        long expectedParallelLimit = (mockedBlockGasLimit - minSequentialSetGasLimit) / (Constants.getTransactionExecutionThreads());
        assertEquals(expectedParallelLimit, BlockUtils.getSublistGasLimit(block, false, minSequentialSetGasLimit));
    }

    @Test
    void sublistGasLimit_ShouldAssignExtraGasLimitToSequentialSet() {
        long minSequentialSetGasLimit = Constants.regtest().getMinSequentialSetGasLimit();
        long mockedBlockGasLimit =  10_000_010L;
        Block block = mock(Block.class);
        when(block.getGasLimit()).thenReturn(BigInteger.valueOf(mockedBlockGasLimit).toByteArray());

        long expectedSequentialLimit = 3_333_338L;
        assertEquals(expectedSequentialLimit, BlockUtils.getSublistGasLimit(block, true, minSequentialSetGasLimit));

        long expectedParallelLimit = 3_333_336L;
        assertEquals(expectedParallelLimit, BlockUtils.getSublistGasLimit(block, false, minSequentialSetGasLimit));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithExactMultipleOfCheckpointNumber() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber = 10000 (exact multiple of 5000)
        long bestBlockNumber = 10000;
        long expected = 10000 - (192 * 20); // 10000 - 3840 = 6160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithNonExactMultipleOfCheckpointNumber() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber = 12500 (not exact multiple of 5000)
        // Should round down to 10000, then subtract checkpoint distance
        long bestBlockNumber = 12500;
        long expected = 10000 - (192 * 20); // 10000 - 3840 = 6160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithBestBlockNumberLessThanCheckpointDistance() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber = 3000 (less than checkpoint distance of 3840)
        long bestBlockNumber = 3000;
        long expected = 0; // Math.max(0, 0 - 3840) = 0
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithBestBlockNumberEqualToCheckpointDistance() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber = 3840 (equal to checkpoint distance)
        long bestBlockNumber = 3840;
        long expected = 0; // Math.max(0, 0 - 3840) = 0
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithZeroBestBlockNumber() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber = 0
        long bestBlockNumber = 0;
        long expected = 0; // Math.max(0, 0 - 3840) = 0
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithLargeBestBlockNumber() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with a very large bestBlockNumber
        long bestBlockNumber = 1000000;
        long expected = 1000000 - (192 * 20); // 1000000 - 3840 = 996160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithDifferentSyncConfigurations() {
        // Test with different chunk sizes and max skeleton chunks
        SyncConfiguration syncConfig1 = mock(SyncConfiguration.class);
        when(syncConfig1.getChunkSize()).thenReturn(100);
        when(syncConfig1.getMaxSkeletonChunks()).thenReturn(10);

        SyncConfiguration syncConfig2 = mock(SyncConfiguration.class);
        when(syncConfig2.getChunkSize()).thenReturn(500);
        when(syncConfig2.getMaxSkeletonChunks()).thenReturn(5);

        long bestBlockNumber = 15000;

        // With syncConfig1: checkpoint distance = 100 * 10 = 1000
        long expected1 = 15000 - 1000; // 14000
        assertEquals(expected1, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig1));

        // With syncConfig2: checkpoint distance = 500 * 5 = 2500
        long expected2 = 15000 - 2500; // 12500
        assertEquals(expected2, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig2));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithBoundaryValues() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with bestBlockNumber just below a checkpoint boundary
        long bestBlockNumber = 4999;
        long expected = 0 - (192 * 20); // 0 - 3840 = -3840, but Math.max(0, -3840) = 0
        assertEquals(0, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));

        // Test with bestBlockNumber exactly at a checkpoint boundary
        bestBlockNumber = 5000;
        expected = 5000 - (192 * 20); // 5000 - 3840 = 1160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));

        // Test with bestBlockNumber just above a checkpoint boundary
        bestBlockNumber = 5001;
        expected = 5000 - (192 * 20); // 5000 - 3840 = 1160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithNegativeBestBlockNumber() {
        SyncConfiguration syncConfig = mock(SyncConfiguration.class);
        when(syncConfig.getChunkSize()).thenReturn(192);
        when(syncConfig.getMaxSkeletonChunks()).thenReturn(20);

        // Test with negative bestBlockNumber (edge case)
        long bestBlockNumber = -1000;
        long expected = 0; // Math.max(0, -1000 - 3840) = 0
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithDefaultSyncConfiguration() {
        // Test using the default sync configuration
        SyncConfiguration defaultConfig = SyncConfiguration.DEFAULT;
        long bestBlockNumber = 10000;
        
        // Default config has chunkSize=192 and maxSkeletonChunks=20
        long expected = 10000 - (192 * 20); // 10000 - 3840 = 6160
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, defaultConfig));
    }

    @Test
    void getSnapCheckpointBlockNumber_WithImmediateSyncConfiguration() {
        // Test using the immediate sync configuration for testing
        SyncConfiguration immediateConfig = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        long bestBlockNumber = 10000;
        
        // Immediate config has chunkSize=192 and maxSkeletonChunks=5
        long expected = 10000 - (192 * 5); // 10000 - 960 = 9040
        assertEquals(expected, BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, immediateConfig));
    }
}
