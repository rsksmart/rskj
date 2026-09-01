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
package co.rsk.db;

import co.rsk.config.BlocksIndexConfig;
import co.rsk.core.BlockDifficulty;
import org.ethereum.db.IndexedBlockStore.BlockInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compact encoding replaces Java serialization in the block index, so it has to reproduce the
 * stored values exactly, and it must be impossible to read an index with the encoding that did not
 * write it - a mismatch there returns plausible-looking garbage rather than failing, which is the
 * worst way for a storage bug to behave.
 */
class BlocksIndexEncodingTest {

    private static final BlocksIndexConfig COMPACT =
            new BlocksIndexConfig(false, false, false, 1000, true);
    private static final BlocksIndexConfig JAVA =
            new BlocksIndexConfig(false, false, false, 1000, false);

    private static BlockInfo info(byte hashByte, BigInteger difficulty, boolean mainChain) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, hashByte);

        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setHash(hash);
        blockInfo.setCummDifficulty(new BlockDifficulty(difficulty));
        blockInfo.setMainChain(mainChain);
        return blockInfo;
    }

    private static DB open(Path dir) {
        return DBMaker.fileDB(new File(dir.toFile(), "index")).make();
    }

    /** Values must survive the round trip byte for byte, including a difficulty far beyond a long. */
    @Test
    void compactEncodingRoundTripsEveryField(@TempDir Path dir) {
        BigInteger huge = BigInteger.TWO.pow(200).add(BigInteger.ONE);

        List<BlockInfo> written = new ArrayList<>();
        written.add(info((byte) 0x11, BigInteger.ZERO, true));
        written.add(info((byte) 0x22, BigInteger.valueOf(123456789L), false));
        written.add(info((byte) 0x33, huge, true));

        DB db = open(dir);
        MapDBBlocksIndex index = new MapDBBlocksIndex(db, COMPACT);
        index.putBlocks(7L, written);
        index.flush();
        index.close();

        db = open(dir);
        List<BlockInfo> read = new MapDBBlocksIndex(db, COMPACT).getBlocksByNumber(7L);
        db.close();

        assertEquals(written.size(), read.size());
        for (int i = 0; i < written.size(); i++) {
            assertEquals(written.get(i).getHash(), read.get(i).getHash(), "hash " + i);
            assertEquals(written.get(i).getCummDifficulty(), read.get(i).getCummDifficulty(), "difficulty " + i);
            assertEquals(written.get(i).isMainChain(), read.get(i).isMainChain(), "mainChain " + i);
        }
        assertEquals(huge, read.get(2).getCummDifficulty().asBigInteger());
    }

    /** An index written compact must refuse to open as Java-serialized rather than return garbage. */
    @Test
    void compactIndexRefusesToOpenAsJava(@TempDir Path dir) {
        DB db = open(dir);
        MapDBBlocksIndex index = new MapDBBlocksIndex(db, COMPACT);
        index.putBlocks(1L, new ArrayList<>(List.of(info((byte) 1, BigInteger.ONE, true))));
        index.flush();
        index.close();

        DB reopened = open(dir);
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> new MapDBBlocksIndex(reopened, JAVA));
        assertTrue(e.getMessage().contains("compact-v1"), e.getMessage());
        reopened.close();
    }

    /** And the reverse: an index written the old way must refuse to open compact. */
    @Test
    void javaIndexRefusesToOpenAsCompact(@TempDir Path dir) {
        DB db = open(dir);
        MapDBBlocksIndex index = new MapDBBlocksIndex(db, JAVA);
        index.putBlocks(1L, new ArrayList<>(List.of(info((byte) 1, BigInteger.ONE, true))));
        index.flush();
        index.close();

        DB reopened = open(dir);
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> new MapDBBlocksIndex(reopened, COMPACT));
        assertTrue(e.getMessage().contains("java-serialization"), e.getMessage());
        reopened.close();
    }

    /** Reopening with the same encoding is the normal path and must not throw. */
    @Test
    void reopeningWithTheSameEncodingIsFine(@TempDir Path dir) {
        DB db = open(dir);
        MapDBBlocksIndex index = new MapDBBlocksIndex(db, COMPACT);
        index.putBlocks(3L, new ArrayList<>(List.of(info((byte) 9, BigInteger.TEN, false))));
        index.flush();
        index.close();

        DB reopened = open(dir);
        MapDBBlocksIndex again = new MapDBBlocksIndex(reopened, COMPACT);
        assertEquals(1, again.getBlocksByNumber(3L).size());
        assertEquals(BigInteger.TEN, again.getBlocksByNumber(3L).get(0).getCummDifficulty().asBigInteger());
        reopened.close();
    }

    /** The default config must still be the legacy encoding, so an upgrade changes nothing on disk. */
    @Test
    void defaultsAreTheLegacyEncoding(@TempDir Path dir) {
        DB db = open(dir);
        new MapDBBlocksIndex(db, BlocksIndexConfig.defaults()).close();

        DB reopened = open(dir);
        // Opening the same index with an explicitly Java-serialized config must be accepted.
        MapDBBlocksIndex index = new MapDBBlocksIndex(reopened, JAVA);
        assertTrue(index.isEmpty());
        reopened.close();
    }
}
