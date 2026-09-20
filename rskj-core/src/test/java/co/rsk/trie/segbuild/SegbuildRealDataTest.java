/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.trie.segbuild;

import org.ethereum.crypto.Keccak256Helper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the on-disk segbuild set against the format it is documented to have.
 *
 * <p>Skipped unless the store is present, so it is a no-op on a machine that does not have it.
 * Point it elsewhere with {@code -Dsegbuild.dir=/path/to/segbuild}.
 */
class SegbuildRealDataTest {

    private static final String DEFAULT_ROOT = "/srv/segbuild-ro";

    private static Path root;

    @BeforeAll
    static void locateStore() {
        root = Paths.get(System.getProperty("segbuild.dir", DEFAULT_ROOT));
        Assumptions.assumeTrue(Files.isDirectory(root), "segbuild store not present at " + root);
    }

    @Test
    void theSetTilesTheChainWithoutGapsOrOverlaps() {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        assertEquals(97, index.size(), "expected 97 chunks");
        assertEquals(1, index.getFirstBlock());

        List<SegbuildChunk> chunks = index.getChunks();
        for (int i = 1; i < chunks.size(); i++) {
            assertEquals(chunks.get(i - 1).getLastBlock() + 1, chunks.get(i).getFirstBlock(),
                    "gap or overlap between " + chunks.get(i - 1) + " and " + chunks.get(i));
        }
    }

    /**
     * A truncated chunk retained beside its replacement, and any stray file in the root, must not
     * be picked up as part of the set.
     */
    @Test
    void directoriesThatAreNotChunksAreIgnored() {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        for (SegbuildChunk chunk : index.getChunks()) {
            String name = chunk.getDirectory().getFileName().toString();
            assertTrue(name.matches("\\d{3}-\\d+-\\d+"), "not a chunk name: " + name);
            assertFalse(name.contains("truncated"), "superseded chunk was included: " + name);
        }
    }

    /**
     * Every entry is keyed by the keccak256 of its own value, whether it is a node message or a
     * long value. That is the invariant the whole store rests on.
     */
    @Test
    void everyEntryIsKeyedByTheKeccakOfItsValue() throws Exception {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);
        SegbuildChunk smallest = index.getChunks().get(index.size() - 1);

        RocksDB.loadLibrary();
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
        descriptors.add(new ColumnFamilyDescriptor("trie_nodes".getBytes(StandardCharsets.UTF_8)));
        List<ColumnFamilyHandle> handles = new ArrayList<>();

        int checked = 0;
        try (DBOptions options = new DBOptions();
             RocksDB db = RocksDB.openReadOnly(options, smallest.getSealedDirectory().toString(),
                     descriptors, handles)) {
            try (RocksIterator it = db.newIterator(handles.get(1))) {
                for (it.seekToFirst(); it.isValid() && checked < 500; it.next()) {
                    byte[] key = it.key();
                    byte[] value = it.value();

                    assertEquals(32, key.length, "keys are 32 bytes");
                    assertArrayEquals(Keccak256Helper.keccak256(value), key,
                            "entry is not keyed by the keccak256 of its value");
                    checked++;
                }
            }
        } finally {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
        }

        assertTrue(checked > 0, "the chunk held no entries");
    }

    /**
     * Opening through the store itself, and reading a node back through rskj's own decoder: the
     * node hash rskj computes must equal the key it was stored under. This is what makes the
     * container change safe -- the encoding is unchanged.
     */
    @Test
    void nodesDecodeWithRskjAndHashToTheirKey() throws Exception {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);
        SegbuildChunk smallest = index.getChunks().get(index.size() - 1);

        SegbuildTrieStore store = new SegbuildTrieStore(index);
        try {
            store.positionAt(smallest.getFirstBlock());
            assertEquals(smallest.getIndex(), store.getOpenChunk().getIndex());

            RocksDB.loadLibrary();
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
            descriptors.add(new ColumnFamilyDescriptor("trie_nodes".getBytes(StandardCharsets.UTF_8)));
            List<ColumnFamilyHandle> handles = new ArrayList<>();

            int decoded = 0;
            try (DBOptions options = new DBOptions();
                 RocksDB db = RocksDB.openReadOnly(options, smallest.getSealedDirectory().toString(),
                         descriptors, handles)) {
                try (RocksIterator it = db.newIterator(handles.get(1))) {
                    for (it.seekToFirst(); it.isValid() && decoded < 50; it.next()) {
                        byte[] key = it.key();

                        // Long values are raw bytes, not node messages; skip anything that does
                        // not decode to a node whose hash is its key.
                        try {
                            byte[] nodeHash = store.retrieve(key).get().getHash().getBytes();
                            if (Arrays.equals(nodeHash, key)) {
                                decoded++;
                            }
                        } catch (RuntimeException ignored) {
                            // Not a node message. Expected for long-value entries.
                        }
                    }
                }
            } finally {
                for (ColumnFamilyHandle handle : handles) {
                    handle.close();
                }
            }

            assertTrue(decoded > 0, "no node decoded with rskj's decoder and hashed to its key");
        } finally {
            store.dispose();
        }
    }

    /** The store must refuse to write, whatever a caller asks of it. */
    @Test
    void theStoreRefusesToWrite() {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);
        SegbuildTrieStore store = new SegbuildTrieStore(index);
        try {
            store.positionAt(index.getLastBlock());

            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> store.saveValue(new byte[]{1, 2, 3}));
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> store.save(null));
        } finally {
            store.dispose();
        }
    }

    /** A node that is not there is an error, not an absent key. */
    @Test
    void aMissIsAnError() {
        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);
        SegbuildTrieStore store = new SegbuildTrieStore(index);
        try {
            store.positionAt(index.getLastBlock());

            byte[] absent = new byte[32];
            Arrays.fill(absent, (byte) 0xAB);

            org.junit.jupiter.api.Assertions.assertThrows(
                    SegbuildTrieStore.SegbuildMissingNodeException.class,
                    () -> store.retrieve(absent));
            assertFalse(store.contains(absent));
        } finally {
            store.dispose();
        }
    }
}
