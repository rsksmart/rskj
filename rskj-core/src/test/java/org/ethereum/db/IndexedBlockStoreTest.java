/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.MapDBBlocksIndex;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FileUtil;
import org.ethereum.util.RskTestFactory;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;
import org.mapdb.DB;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

import static co.rsk.core.BlockDifficulty.ZERO;
import static org.ethereum.TestUtils.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class IndexedBlockStoreTest {

    private List<Block> blocks = new ArrayList<>();
    private BlockDifficulty cumDifficulty = ZERO;
    private TestSystemProperties config;
    private BlockFactory blockFactory;
    private Function<TestSystemProperties, KeyValueDataSource> keyValueDataSourceFn;

    protected void setUp(Function<TestSystemProperties, KeyValueDataSource> keyValueDataSourceFn) {
        this.keyValueDataSourceFn = keyValueDataSourceFn;
    }

    @Test
    @Disabled("save some load, and check it exist")
    void test1() {
        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockDifficulty cummDiff = BlockDifficulty.ZERO;
        for (Block block : blocks) {
            cummDiff = cummDiff.add(block.getDifficultyWithUncles());
            indexedBlockStore.saveBlock(block, cummDiff, true);
        }

        //  testing:   getTotalDifficultyForHash(byte[])
        //  testing:   getMaxNumber()

        long bestIndex = blocks.get(blocks.size() - 1).getNumber();
        assertEquals(bestIndex, indexedBlockStore.getMaxNumber());
        assertEquals(cumDifficulty, indexedBlockStore.getTotalDifficultyForHash(blocks.get(blocks.size() - 1).getHash().getBytes()));

        //  testing:  getBlockByHash(byte[])

        Block block = blocks.get(50);
        Block block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getBlockByHash(Hex.decode("00112233"));
        assertNull(block_);

        //  testing:  getChainBlockByNumber(long)

        block = blocks.get(50);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getChainBlockByNumber(10000);
        assertNull(block_);

        //  testing: getBlocksInformationByNumber(long)

        block = blocks.get(50);
        BlockInformation blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        block = blocks.get(150);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        block = blocks.get(0);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        block = blocks.get(8003);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        int blocksNum = indexedBlockStore.getBlocksInformationByNumber(10000).size();
        assertEquals(0, blocksNum);

        //  testing: getListHashesEndWith(byte[], long)

        block = blocks.get(8003);
        List<byte[]> hashList = indexedBlockStore.getListHashesEndWith(block.getHash().getBytes(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(8003 - i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        //  testing: getListHashesStartWith(long, long)

        block = blocks.get(7003);
        hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(7003 + i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

    }

    @Test
    @Disabled("save some load, and check it exist")
    void test2() {
        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockDifficulty cummDiff = BlockDifficulty.ZERO;
        for (Block block : blocks) {
            cummDiff = cummDiff.add(block.getDifficultyWithUncles());
            indexedBlockStore.saveBlock(block, cummDiff, true);
        }

        //  testing:   getTotalDifficultyForHash(byte[])
        //  testing:   getMaxNumber()

        long bestIndex = blocks.get(blocks.size() - 1).getNumber();
        assertEquals(bestIndex, indexedBlockStore.getMaxNumber());
        assertEquals(cumDifficulty, indexedBlockStore.getTotalDifficultyForHash(blocks.get(blocks.size() - 1).getHash().getBytes()));

        //  testing:  getBlockByHash(byte[])

        Block block = blocks.get(50);
        Block block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getBlockByHash(Hex.decode("00112233"));
        assertNull(block_);

        //  testing:  getChainBlockByNumber(long)

        block = blocks.get(50);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getChainBlockByNumber(10000);
        assertNull(block_);

        //  testing: getBlocksInformationByNumber(long)

        block = blocks.get(50);
        BlockInformation blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(150);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(0);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(8003);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        int blocksNum = indexedBlockStore.getBlocksInformationByNumber(10000).size();
        assertEquals(0, blocksNum);

        //  testing: getListHashesEndWith(byte[], long)

        block = blocks.get(8003);
        List<byte[]> hashList = indexedBlockStore.getListHashesEndWith(block.getHash().getBytes(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(8003 - i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        //  testing: getListHashesStartWith(long, long)

        block = blocks.get(7003);
        hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(7003 + i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

    }

    @Test
    @Disabled("???")
    void test3() {
        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockDifficulty cummDiff = BlockDifficulty.ZERO;

        for (Block block : blocks) {
            cummDiff = cummDiff.add(block.getDifficultyWithUncles());
            indexedBlockStore.saveBlock(block, cummDiff, true);
        }

        indexedBlockStore.flush();

        //  testing:   getTotalDifficultyForHash(byte[])
        //  testing:   getMaxNumber()

        long bestIndex = blocks.get(blocks.size() - 1).getNumber();
        assertEquals(bestIndex, indexedBlockStore.getMaxNumber());
        assertEquals(cumDifficulty, indexedBlockStore.getTotalDifficultyForHash(blocks.get(blocks.size() - 1).getHash().getBytes()));

        //  testing:  getBlockByHash(byte[])

        Block block = blocks.get(50);
        Block block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getBlockByHash(Hex.decode("00112233"));
        assertNull(block_);

        //  testing:  getChainBlockByNumber(long)

        block = blocks.get(50);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getChainBlockByNumber(10000);
        assertNull(block_);

        //  testing: getBlocksInformationByNumber(long)

        block = blocks.get(50);
        BlockInformation blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(150);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(0);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        block = blocks.get(8003);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());

        int blocksNum = indexedBlockStore.getBlocksInformationByNumber(10000).size();
        assertEquals(0, blocksNum);

        //  testing: getListHashesEndWith(byte[], long)

        block = blocks.get(8003);
        List<byte[]> hashList = indexedBlockStore.getListHashesEndWith(block.getHash().getBytes(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(8003 - i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        //  testing: getListHashesStartWith(long, long)

        block = blocks.get(7003);
        hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(7003 + i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }
    }

    @Test
    @Disabled("leveldb + mapdb, save some load, flush to disk, and check it exist")
    void test4() {
        BigInteger bi = new BigInteger(32, new Random(IndexedBlockStoreTest.class.hashCode()));
        String testDir = "test_db_" + bi;
        config.setDataBaseDir(testDir);

        DB indexDB = createMapDB(testDir);

        KeyValueDataSource blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

        BlockDifficulty cummDiff = BlockDifficulty.ZERO;
        for (Block block : blocks) {
            cummDiff = cummDiff.add(block.getDifficultyWithUncles());
            indexedBlockStore.saveBlock(block, cummDiff, true);
        }

        //  testing:   getTotalDifficultyForHash(byte[])
        //  testing:   getMaxNumber()

        long bestIndex = blocks.get(blocks.size() - 1).getNumber();
        assertEquals(bestIndex, indexedBlockStore.getMaxNumber());
        assertEquals(cumDifficulty, indexedBlockStore.getTotalDifficultyForHash(blocks.get(blocks.size() - 1).getHash().getBytes()));

        //  testing:  getBlockByHash(byte[])

        Block block = blocks.get(50);
        Block block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getBlockByHash(Hex.decode("00112233"));
        assertNull(block_);

        //  testing:  getChainBlockByNumber(long)

        block = blocks.get(50);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(150);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(0);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block = blocks.get(8003);
        block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
        assertEquals(block.getNumber(), block_.getNumber());

        block_ = indexedBlockStore.getChainBlockByNumber(10000);
        assertNull(block_);

        //  testing: getBlocksInformationByNumber(long)

        block = blocks.get(50);
        BlockInformation blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(150);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(0);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        block = blocks.get(8003);
        blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
        Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

        int blocksNum = indexedBlockStore.getBlocksInformationByNumber(10000).size();
        assertEquals(0, blocksNum);

        //  testing: getListHashesEndWith(byte[], long)

        block = blocks.get(8003);
        List<byte[]> hashList = indexedBlockStore.getListHashesEndWith(block.getHash().getBytes(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(8003 - i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        //  testing: getListHashesStartWith(long, long)

        block = blocks.get(7003);
        hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(7003 + i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        blocksDB.close();
        indexDB.close();

        // testing after: REOPEN

        indexDB = createMapDB(testDir);

        blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

        //  testing: getListHashesStartWith(long, long)

        block = blocks.get(7003);
        hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
        for (int i = 0; i < 100; ++i) {
            block = blocks.get(7003 + i);
            String hash = ByteUtil.toHexString(hashList.get(i));
            String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
            assertEquals(hash_, hash);
        }

        blocksDB.close();
        indexDB.close();
        FileUtil.recursiveDelete(testDir);
    }

    @Test
    @Disabled("leveldb + mapdb, save part to disk part to cache, and check it exist")
    void test5() {
        BigInteger bi = new BigInteger(32, new Random(IndexedBlockStoreTest.class.hashCode()));
        String testDir = "test_db_" + bi;
        config.setDataBaseDir(testDir);

        DB indexDB = createMapDB(testDir);

        KeyValueDataSource blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        try {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

            BlockDifficulty cummDiff = BlockDifficulty.ZERO;
            int preloadSize = blocks.size() / 2;
            for (int i = 0; i < preloadSize; ++i) {
                Block block = blocks.get(i);
                cummDiff = cummDiff.add(block.getDifficultyWithUncles());
                indexedBlockStore.saveBlock(block, cummDiff, true);
            }

            indexedBlockStore.flush();

            for (int i = preloadSize; i < blocks.size(); ++i) {
                Block block = blocks.get(i);
                cummDiff = cummDiff.add(block.getDifficultyWithUncles());
                indexedBlockStore.saveBlock(block, cummDiff, true);
            }

            //  testing:   getTotalDifficultyForHash(byte[])
            //  testing:   getMaxNumber()

            long bestIndex = blocks.get(blocks.size() - 1).getNumber();
            assertEquals(bestIndex, indexedBlockStore.getMaxNumber());
            assertEquals(cumDifficulty, indexedBlockStore.getTotalDifficultyForHash(blocks.get(blocks.size() - 1).getHash().getBytes()));

            //  testing:  getBlockByHash(byte[])

            Block block = blocks.get(50);
            Block block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(150);
            block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(0);
            block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(8003);
            block_ = indexedBlockStore.getBlockByHash(block.getHash().getBytes());
            assertEquals(block.getNumber(), block_.getNumber());

            block_ = indexedBlockStore.getBlockByHash(Hex.decode("00112233"));
            assertNull(block_);

            //  testing:  getChainBlockByNumber(long)

            block = blocks.get(50);
            block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(150);
            block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(0);
            block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
            assertEquals(block.getNumber(), block_.getNumber());

            block = blocks.get(8003);
            block_ = indexedBlockStore.getChainBlockByNumber(block.getNumber());
            assertEquals(block.getNumber(), block_.getNumber());

            block_ = indexedBlockStore.getChainBlockByNumber(10000);
            assertNull(block_);

            //  testing: getBlocksInformationByNumber(long)

            block = blocks.get(50);
            BlockInformation blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
            Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
            Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

            block = blocks.get(150);
            blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
            Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
            Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

            block = blocks.get(0);
            blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
            Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
            Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

            block = blocks.get(8003);
            blockInformation = indexedBlockStore.getBlocksInformationByNumber(block.getNumber()).get(0);
            Assertions.assertArrayEquals(block.getHash().getBytes(), blockInformation.getHash());
            Assertions.assertTrue(blockInformation.getTotalDifficulty().compareTo(ZERO) > 0);

            int blocksNum = indexedBlockStore.getBlocksInformationByNumber(10000).size();
            assertEquals(0, blocksNum);

            //  testing: getListHashesEndWith(byte[], long)

            block = blocks.get(8003);
            List<byte[]> hashList = indexedBlockStore.getListHashesEndWith(block.getHash().getBytes(), 100);
            for (int i = 0; i < 100; ++i) {
                block = blocks.get(8003 - i);
                String hash = ByteUtil.toHexString(hashList.get(i));
                String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
                assertEquals(hash_, hash);
            }

            //  testing: getListHashesStartWith(long, long)

            block = blocks.get(7003);
            hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
            for (int i = 0; i < 100; ++i) {
                block = blocks.get(7003 + i);
                String hash = ByteUtil.toHexString(hashList.get(i));
                String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
                assertEquals(hash_, hash);
            }


            indexedBlockStore.flush();
            blocksDB.close();
            indexDB.close();

            // testing after: REOPEN

            indexDB = createMapDB(testDir);

            blocksDB = keyValueDataSourceFn.apply(config);
            blocksDB.init();

            indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

            //  testing: getListHashesStartWith(long, long)

            block = blocks.get(7003);
            hashList = indexedBlockStore.getListHashesStartWith(block.getNumber(), 100);
            for (int i = 0; i < 100; ++i) {
                block = blocks.get(7003 + i);
                String hash = ByteUtil.toHexString(hashList.get(i));
                String hash_ = ByteUtil.toHexString(block.getHash().getBytes());
                assertEquals(hash_, hash);
            }
        } finally {
            blocksDB.close();
            indexDB.close();
            FileUtil.recursiveDelete(testDir);
        }

    }

    @Test // leveldb + mapdb, multi branch, total difficulty test
    @Disabled("Ethereum block format")
    void test6() throws IOException {
        BigInteger bi = new BigInteger(32, new Random(IndexedBlockStoreTest.class.hashCode()));
        String testDir = "test_db_" + bi;
        config.setDataBaseDir(testDir);

        DB indexDB = createMapDB(testDir);

        KeyValueDataSource blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        try {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

            Block genesis = RskTestFactory.getGenesisInstance(config);
            List<Block> bestLine = getRandomChain(blockFactory, genesis.getHash().getBytes(), 1, 100);

            indexedBlockStore.saveBlock(genesis, genesis.getDifficultyWithUncles(), true);

            BlockDifficulty td = genesis.getDifficultyWithUncles();

            for (int i = 0; i < bestLine.size(); ++i) {
                Block newBlock = bestLine.get(i);
                td = td.add(newBlock.getDifficultyWithUncles());

                indexedBlockStore.saveBlock(newBlock, td, true);
            }

            byte[] forkParentHash = bestLine.get(60).getHash().getBytes();
            long forkParentNumber = bestLine.get(60).getNumber();
            List<Block> forkLine = getRandomChain(blockFactory, forkParentHash, forkParentNumber + 1, 50);

            for (int i = 0; i < forkLine.size(); ++i) {
                Block newBlock = forkLine.get(i);
                Block parentBlock = indexedBlockStore.getBlockByHash(newBlock.getParentHash().getBytes());
                td = indexedBlockStore.getTotalDifficultyForHash(parentBlock.getHash().getBytes());

                td = td.add(newBlock.getDifficultyWithUncles());
                indexedBlockStore.saveBlock(newBlock, td, false);
            }

            // calc all TDs
            Map<Keccak256, BlockDifficulty> tDiffs = new HashMap<>();
            td = RskTestFactory.getGenesisInstance(config).getDifficultyWithUncles();
            for (Block block : bestLine) {
                td = td.add(block.getDifficultyWithUncles());
                tDiffs.put(block.getHash(), td);
            }

            Map<Keccak256, BlockDifficulty> tForkDiffs = new HashMap<>();
            Block block = forkLine.get(0);
            td = tDiffs.get(block.getParentHash());
            for (Block currBlock : forkLine) {
                td = td.add(currBlock.getDifficultyWithUncles());
                tForkDiffs.put(currBlock.getHash(), td);
            }

            // Assert tds on bestLine
            for (Keccak256 hash : tDiffs.keySet()) {
                BlockDifficulty currTD = tDiffs.get(hash);
                BlockDifficulty checkTd = indexedBlockStore.getTotalDifficultyForHash(hash.getBytes());
                assertEquals(checkTd, currTD);
            }

            // Assert tds on forkLine
            for (Keccak256 hash : tForkDiffs.keySet()) {
                BlockDifficulty currTD = tForkDiffs.get(hash);
                BlockDifficulty checkTd = indexedBlockStore.getTotalDifficultyForHash(hash.getBytes());
                assertEquals(checkTd, currTD);
            }

            indexedBlockStore.flush();

            // Assert tds on bestLine
            for (Keccak256 hash : tDiffs.keySet()) {
                BlockDifficulty currTD = tDiffs.get(hash);
                BlockDifficulty checkTd = indexedBlockStore.getTotalDifficultyForHash(hash.getBytes());
                assertEquals(checkTd, currTD);
            }

            // check total difficulty
            Block bestBlock = bestLine.get(bestLine.size() - 1);
            BlockDifficulty totalDifficulty = indexedBlockStore.getTotalDifficultyForHash(bestBlock.getHash().getBytes());
            BlockDifficulty totalDifficulty_ = tDiffs.get(bestBlock.getHash());

            assertEquals(totalDifficulty_, totalDifficulty);

            // Assert tds on forkLine
            for (Keccak256 hash : tForkDiffs.keySet()) {
                BlockDifficulty currTD = tForkDiffs.get(hash);
                BlockDifficulty checkTd = indexedBlockStore.getTotalDifficultyForHash(hash.getBytes());
                assertEquals(checkTd, currTD);
            }


        } finally {
            blocksDB.close();
            indexDB.close();
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test // leveldb + mapdb, multi branch, total re-branch test
    @Disabled("Ethereum block format")
    void test7() throws IOException {
        BigInteger bi = new BigInteger(32, new Random(IndexedBlockStoreTest.class.hashCode()));
        String testDir = "test_db_" + bi;
        config.setDataBaseDir(testDir);

        DB indexDB = createMapDB(testDir);

        KeyValueDataSource blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        try {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

            Block genesis = RskTestFactory.getGenesisInstance(config);
            List<Block> bestLine = getRandomChain(blockFactory, genesis.getHash().getBytes(), 1, 100);

            indexedBlockStore.saveBlock(genesis, genesis.getDifficultyWithUncles(), true);

            BlockDifficulty td = genesis.getDifficultyWithUncles();

            for (int i = 0; i < bestLine.size(); ++i) {
                Block newBlock = bestLine.get(i);
                td = td.add(newBlock.getDifficultyWithUncles());

                indexedBlockStore.saveBlock(newBlock, td, true);
            }

            byte[] forkParentHash = bestLine.get(60).getHash().getBytes();
            long forkParentNumber = bestLine.get(60).getNumber();
            List<Block> forkLine = getRandomChain(blockFactory, forkParentHash, forkParentNumber + 1, 50);

            for (int i = 0; i < forkLine.size(); ++i) {
                Block newBlock = forkLine.get(i);
                Block parentBlock = indexedBlockStore.getBlockByHash(newBlock.getParentHash().getBytes());
                td = indexedBlockStore.getTotalDifficultyForHash(parentBlock.getHash().getBytes());

                td = td.add(newBlock.getDifficultyWithUncles());
                indexedBlockStore.saveBlock(newBlock, td, false);
            }

            Block forkBlock = forkLine.get(forkLine.size() - 1);

            Assertions.assertDoesNotThrow(() -> indexedBlockStore.reBranch(forkBlock));
        } finally {
            blocksDB.close();
            indexDB.close();
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test // leveldb + mapdb, multi branch, total re-branch test
    @Disabled("Ethereum block format")
    void test8() {
        BigInteger bi = new BigInteger(32, new Random(IndexedBlockStoreTest.class.hashCode()));
        String testDir = "test_db_" + bi;
        config.setDataBaseDir(testDir);

        DB indexDB = createMapDB(testDir);

        KeyValueDataSource blocksDB = keyValueDataSourceFn.apply(config);
        blocksDB.init();

        try {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, blocksDB, new MapDBBlocksIndex(indexDB));

            Block genesis = RskTestFactory.getGenesisInstance(config);
            List<Block> bestLine = getRandomChain(blockFactory, genesis.getHash().getBytes(), 1, 100);

            indexedBlockStore.saveBlock(genesis, genesis.getDifficultyWithUncles(), true);

            BlockDifficulty td = BlockDifficulty.ZERO;

            for (int i = 0; i < bestLine.size(); ++i) {
                Block newBlock = bestLine.get(i);
                td = td.add(newBlock.getDifficultyWithUncles());

                indexedBlockStore.saveBlock(newBlock, td, true);
            }

            byte[] forkParentHash = bestLine.get(60).getHash().getBytes();
            long forkParentNumber = bestLine.get(60).getNumber();
            List<Block> forkLine = getRandomChain(blockFactory, forkParentHash, forkParentNumber + 1, 10);

            for (int i = 0; i < forkLine.size(); ++i) {
                Block newBlock = forkLine.get(i);
                Block parentBlock = indexedBlockStore.getBlockByHash(newBlock.getParentHash().getBytes());
                td = indexedBlockStore.getTotalDifficultyForHash(parentBlock.getHash().getBytes());

                td = td.add(newBlock.getDifficultyWithUncles());
                indexedBlockStore.saveBlock(newBlock, td, false);
            }

            Block bestBlock = bestLine.get(bestLine.size() - 1);
            Block forkBlock = forkLine.get(forkLine.size() - 1);

            assertEquals(100, indexedBlockStore.getBestBlock().getNumber());

            indexedBlockStore.reBranch(forkBlock);

            assertEquals(71, indexedBlockStore.getBestBlock().getNumber());

            // Assert that all fork moved to the main line
            for (Block currBlock : forkLine) {
                Long number = currBlock.getNumber();
                Block chainBlock = indexedBlockStore.getChainBlockByNumber(number);
                assertEquals(currBlock.getPrintableHash(), chainBlock.getPrintableHash());
            }

            // Assert that all fork moved to the main line
            // re-branch back to previous line and assert that
            // all the block really moved
            bestBlock = bestLine.get(bestLine.size() - 1);
            indexedBlockStore.reBranch(bestBlock);
            for (Block currBlock : bestLine) {
                Long number = currBlock.getNumber();
                Block chainBlock = indexedBlockStore.getChainBlockByNumber(number);
                assertEquals(currBlock.getPrintableHash(), chainBlock.getPrintableHash());
            }
        } finally {
            blocksDB.close();
            indexDB.close();
            FileUtil.recursiveDelete(testDir);
        }
    }

    @Test // test index merging during the flush
    @Disabled("Ethereum block format")
    void test9() {
        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        // blocks with the same block number
        Block block1 = blockFactory.decodeBlock(Hex.decode(
                "f90202f901fda0ad0d51e8d64c364a7b77ef2fe252f3f4df0940c7cfa69cedc1" +
                        "fbd6ea66894936a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413" +
                        "f0a142fd40d493479414a3bc0f103706650a19c5d24e5c4cf1ea5af78ea0e058" +
                        "0f4fdd1e3ae8346efaa6b1018605361f6e2fb058580e31414c8cbf5b0d49a056" +
                        "e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0" +
                        "56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421" +
                        "b901000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000008605065cf2c43a8303e52e832fefd8808455fcbe1b80a017247341fd5d" +
                        "2f1d384682fea9302065a95dbd3e4f8260dde88a386f3cb95be3880f3fc8d5e0c87378c0c0"
        ));
        Block block2 = blockFactory.decodeBlock(Hex.decode(
                "f90218f90213a0c63fc3626abc6f6ba695064e973126cccc6fd513d4f53485e1" +
                        "1794a8855e8b2ba01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413" +
                        "f0a142fd40d49347941dcb8d1f0fcc8cbc8c2d76528e877f915e299fbea0ccb2" +
                        "ed2a8c585409fe5530d36320bc8c1406454b32a9e419e890ea49489e534aa056" +
                        "e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0" +
                        "56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421" +
                        "b901000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000008605079eb238d88303e52e832fefd8808455fcbe2596d5830101038447" +
                        "65746885676f312e35856c696e7578a0a673a429161eb32e6d0887b2bce2b12b" +
                        "1edd6e4b4cf55371853cba13d57118bd88d44d3609c7e203c7c0c0"
        ));

        indexedBlockStore.saveBlock(block1, block1.getDifficultyWithUncles(), true);
        indexedBlockStore.flush();

        indexedBlockStore.saveBlock(block2, block2.getDifficultyWithUncles(), true);
        indexedBlockStore.flush();

        assertEquals(block1.getDifficultyWithUncles(), indexedBlockStore.getTotalDifficultyForHash(block1.getHash().getBytes()));
        assertEquals(block2.getDifficultyWithUncles(), indexedBlockStore.getTotalDifficultyForHash(block2.getHash().getBytes()));
    }

    @Test
    void rewind() {
        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(
                mock(BlockFactory.class),
                mock(KeyValueDataSource.class),
                new HashMapBlocksIndex());

        Random random = new Random(123);
        long blocksToGenerate = 14;
        for (long i = 0; i < blocksToGenerate; i++) {
            Block block = mock(Block.class);
            Keccak256 blockHash = new Keccak256(generateBytesFromRandom(random,32));
            when(block.getHash()).thenReturn(blockHash);
            when(block.getNumber()).thenReturn(i);

            indexedBlockStore.saveBlock(block, ZERO, true);
        }

        Block bestBlock = indexedBlockStore.getBestBlock();
        MatcherAssert.assertThat(bestBlock.getNumber(), is(blocksToGenerate - 1));

        long blockToRewind = blocksToGenerate / 2;
        indexedBlockStore.rewind(blockToRewind);

        bestBlock = indexedBlockStore.getBestBlock();
        MatcherAssert.assertThat(bestBlock.getNumber(), is(blockToRewind));
    }
}
