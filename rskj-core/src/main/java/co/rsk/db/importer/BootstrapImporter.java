/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db.importer;

import co.rsk.core.BlockDifficulty;
import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import com.google.common.io.CountingInputStream;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.BlockStore;
import org.ethereum.util.FileUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import static co.rsk.db.importer.BootstrapV2Format.CHUNK_MAX;
import static co.rsk.db.importer.BootstrapV2Format.MAGIC;
import static co.rsk.db.importer.BootstrapV2Format.TAG_BLOCKS;
import static co.rsk.db.importer.BootstrapV2Format.TAG_END;
import static co.rsk.db.importer.BootstrapV2Format.TAG_NODES;
import static co.rsk.db.importer.BootstrapV2Format.TAG_VALUES;
import static co.rsk.db.importer.BootstrapV2Format.VERSION;

public class BootstrapImporter {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapImporter.class);

    private static final int READ_BUFFER_SIZE = 64 * 1024;
    // A chunk is read into a single byte[], so it must fit a Java array. CHUNK_MAX is the exporter's
    // soft cap, but a single oversized element may exceed it; this is the hard ceiling either way.
    private static final long MAX_CHUNK_BYTES = (long) Integer.MAX_VALUE - 8;

    private final BootstrapDataProvider bootstrapDataProvider;
    private final BlockStore blockStore;
    private final TrieStore trieStore;
    private final BlockFactory blockFactory;
    private final DbKind dbKind;

    public BootstrapImporter(
            BlockStore blockStore,
            TrieStore trieStore,
            BlockFactory blockFactory,
            DbKind dbKind,
            BootstrapDataProvider bootstrapDataProvider) {
        this.blockStore = blockStore;
        this.trieStore = trieStore;
        this.blockFactory = blockFactory;
        this.dbKind = dbKind;
        this.bootstrapDataProvider = bootstrapDataProvider;
    }

    public void importData() {
        long start = System.currentTimeMillis();

        bootstrapDataProvider.retrieveData();
        updateDatabase();

        long durationInMills = System.currentTimeMillis() - start;
        logger.info("Bootstrap data has successfully been imported in {} mills", durationInMills);
    }

    private void updateDatabase() {
        Path dataPath = bootstrapDataProvider.getBootstrapDataPath();
        if (isV2(dataPath)) {
            logger.info("Detected bootstrap-data v2 (chunked) format");
            updateDatabaseV2(dataPath);
        } else {
            logger.info("Detected bootstrap-data v1 (legacy) format");
            updateDatabaseV1(bootstrapDataProvider.getBootstrapData());
        }
    }

    // --- v1 (legacy) path: whole-payload in-memory decode. Kept for already-published snapshots. ---

    private void updateDatabaseV1(byte[] bootstrapData) {
        Queue<RLPElement> rlpElementQueue = decodeQueue(bootstrapData);

        long start = System.currentTimeMillis();
        logger.debug("Inserting blocks...");
        insertBlocks(Objects.requireNonNull(rlpElementQueue.poll()));
        logger.debug("Blocks have been inserted in {} mills", System.currentTimeMillis() - start);

        HashMapDB hashMapDB = new HashMapDB();
        Queue<byte[]> nodeDataQueue = new LinkedList<>();
        Queue<byte[]> nodeValueQueue = new LinkedList<>();
        Queue<Trie> trieQueue = new LinkedList<>();

        start = System.currentTimeMillis();
        logger.debug("Preparing state for insertion...");
        fillUpRlpDataQueues(nodeDataQueue, nodeValueQueue, Objects.requireNonNull(rlpElementQueue.poll()));
        fillUpTrieQueue(trieQueue, nodeDataQueue, nodeValueQueue, hashMapDB);
        logger.debug("State has been prepared in {} mills", System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        logger.debug("Inserting state...");
        insertState(trieStore, trieQueue);
        logger.debug("State has been inserted in {} mills", System.currentTimeMillis() - start);
    }

    private void insertBlocks(RLPElement encodedTuples) {
        RLPList blocksData = RLP.decodeList(encodedTuples.getRLPData());

        for (int k = 0; k < blocksData.size(); k++) {
            RLPElement element = blocksData.get(k);
            RLPList blockData = RLP.decodeList(element.getRLPData());
            RLPList tuple = RLP.decodeList(blockData.getRLPData());
            saveBlockFromTuple(tuple);
        }

        blockStore.flush();
    }

    private static void fillUpRlpDataQueues(Queue<byte[]> nodeDataQueue, Queue<byte[]> nodeValueQueue, RLPElement rlpElement) {
        Queue<RLPElement> nodeListQueue = decodeQueue(rlpElement.getRLPData());

        fillUpRlpDataQueue(nodeDataQueue, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
        fillUpRlpDataQueue(nodeValueQueue, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
    }

    private static void fillUpRlpDataQueue(Queue<byte[]> rlpDataQueue, RLPList nodesData) {
        int size = nodesData.size();
        for (int k = 0; k < size; k++) {
            RLPElement element = nodesData.get(k);
            byte[] rlpData = Objects.requireNonNull(element.getRLPData());

            rlpDataQueue.add(rlpData);
        }
    }

    private static void fillUpTrieQueue(Queue<Trie> trieQueue,
                                        Queue<byte[]> nodeDataQueue, Queue<byte[]> nodeValueQueue,
                                        HashMapDB hashMapDB) {
        TrieStoreImpl fakeStore = new TrieStoreImpl(hashMapDB);

        for (byte[] nodeData = nodeDataQueue.poll(); nodeData != null; nodeData = nodeDataQueue.poll()) {
            Trie trie = Trie.fromMessage(nodeData, fakeStore);
            hashMapDB.put(trie.getHash().getBytes(), nodeData);
            trieQueue.add(trie);
        }

        for (byte[] nodeValue = nodeValueQueue.poll(); nodeValue != null; nodeValue = nodeValueQueue.poll()) {
            hashMapDB.put(Keccak256Helper.keccak256(nodeValue), nodeValue);
        }
    }

    private static void insertState(TrieStore destinationTrieStore, Queue<Trie> trieQueue) {
        for (Trie trie = trieQueue.poll(); trie != null; trie = trieQueue.poll()) {
            destinationTrieStore.save(trie);
        }
    }

    private static Queue<RLPElement> decodeQueue(byte[] data) {
        RLPList rlpList = RLP.decodeList(data);
        int size = rlpList.size();

        Queue<RLPElement> result = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            result.add(rlpList.get(i));
        }

        return result;
    }

    // --- v2 (chunked, streaming) path: bounded memory, size-uncapped. See BootstrapV2Format. ---

    private void updateDatabaseV2(Path dataPath) {
        // A node's long value is resolved lazily from its store at save time (including embedded
        // long-value children reached via parent recursion), so every long value must be available
        // before any node is saved. Pass 1 stages all values into a bounded, disk-backed store; the
        // nodes section is then saved against it without holding the whole state in heap.
        try (ValueStore valueStore = openValueStore()) {
            KeyValueDataSource valueDataSource = valueStore.dataSource();
            TrieStore valueTrieStore = new TrieStoreImpl(valueDataSource);

            long start = System.currentTimeMillis();
            logger.debug("Staging long values...");
            scanSections(dataPath, Set.of(TAG_VALUES), (tag, chunk) -> {
                for (RLPElement element : RLP.decode2(chunk)) {
                    byte[] value = element.getRLPData();
                    valueDataSource.put(Keccak256Helper.keccak256(value), value);
                }
            });
            valueDataSource.flush();
            logger.debug("Long values staged in {} mills", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            logger.debug("Inserting blocks and state...");
            // counts[0] = blocks saved, counts[1] = nodes saved. v1 implicitly required both the blocks
            // and the nodes section to be present (it polled them off a queue); v2 dispatches by tag, so
            // we assert non-empty results here to fail fast on a file missing either section rather than
            // "succeeding" with no state and only crashing later at first state access.
            long[] counts = new long[2];
            scanSections(dataPath, Set.of(TAG_BLOCKS, TAG_NODES), (tag, chunk) -> {
                if (tag == TAG_BLOCKS) {
                    for (RLPElement element : RLP.decode2(chunk)) {
                        saveBlockFromTuple(RLP.decodeList(element.getRLPData()));
                        counts[0]++;
                    }
                } else if (tag == TAG_NODES) {
                    for (RLPElement element : RLP.decode2(chunk)) {
                        Trie trie = Trie.fromMessage(element.getRLPData(), valueTrieStore);
                        saveNode(trie);
                        counts[1]++;
                    }
                }
            });
            if (counts[0] == 0) {
                throw new BootstrapImportException("Bootstrap-data v2 has no blocks section (or it is empty); refusing to import incomplete data");
            }
            if (counts[1] == 0) {
                throw new BootstrapImportException("Bootstrap-data v2 has no state-nodes section (or it is empty); refusing to import a stateless snapshot");
            }
            blockStore.flush();
            trieStore.flush();
            logger.debug("Blocks and state inserted in {} mills", System.currentTimeMillis() - start);
        }
    }

    /**
     * Saves a single state node. A node's long value is resolved lazily from the staged value store, so a
     * value missing (or length-inconsistent) there surfaces deep inside {@code save} as a generic
     * {@link IllegalArgumentException}; we translate it into an actionable import error that points at the
     * real cause (incomplete/corrupt bootstrap data) instead of letting the opaque exception escape.
     */
    private void saveNode(Trie trie) {
        try {
            trieStore.save(trie);
        } catch (IllegalArgumentException e) {
            throw new BootstrapImportException(
                    "Failed to save a state node during bootstrap import: a referenced long value is missing "
                            + "or inconsistent in the values section (incomplete or corrupt bootstrap data)", e);
        }
    }

    /**
     * Opens the bounded, disk-backed store used to stage long values during a v2 import. Overridable so
     * tests can substitute an in-memory store.
     */
    protected ValueStore openValueStore() {
        Path dir = createValueStoreDir();
        return new ValueStore(KeyValueDataSourceUtils.makeDataSource(dir, dbKind), dir);
    }

    /** Lifecycle holder for the temporary value-staging data source (disposes the store and its dir). */
    protected static class ValueStore implements AutoCloseable {
        private final KeyValueDataSource dataSource;
        private final Path dir;

        public ValueStore(KeyValueDataSource dataSource, Path dir) {
            this.dataSource = dataSource;
            this.dir = dir;
        }

        KeyValueDataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() {
            dataSource.close();
            if (dir != null) {
                FileUtil.recursiveDelete(dir.toString());
            }
        }
    }

    /**
     * Streams the v2 file, invoking {@code processor} once per chunk that belongs to a section in
     * {@code tagsOfInterest}. Sections are dispatched by tag, so this is independent of the order in which
     * the exporter wrote them. Chunks for tags outside {@code tagsOfInterest} are skipped without being
     * read into memory or decoded: the file layout puts nodes (the bulk) before values, so the value pass
     * seeks past blocks+nodes and the blocks+nodes pass seeks past values, keeping the total cost close to
     * a single full read rather than two. Each wanted chunk is read into a bounded {@code byte[]}, handed
     * off, then discarded. The full structural scan (header, tags, chunk-length and end-of-section
     * sentinels, end-of-sections marker) is validated regardless of which tags are of interest.
     */
    private void scanSections(Path dataPath, Set<Integer> tagsOfInterest, ChunkProcessor processor) {
        long fileSize;
        try {
            fileSize = Files.size(dataPath);
        } catch (IOException e) {
            throw new BootstrapImportException("Error reading bootstrap-data v2 from " + dataPath, e);
        }
        // counts every byte handed to the DataInputStream (reads and skips alike), so a declared chunk
        // length can be bounded against the bytes actually left in the file before any byte[] is allocated.
        try (CountingInputStream counter = new CountingInputStream(
                new BufferedInputStream(Files.newInputStream(dataPath), READ_BUFFER_SIZE));
             DataInputStream in = new DataInputStream(counter)) {
            readAndVerifyHeader(in);

            int tag = in.read();
            while (tag != -1 && tag != TAG_END) {
                if (tag != TAG_BLOCKS && tag != TAG_NODES && tag != TAG_VALUES) {
                    throw new BootstrapImportException("Unknown bootstrap-data v2 section tag: " + tag);
                }
                boolean wanted = tagsOfInterest.contains(tag);
                for (long len = in.readLong(); len != 0L; len = in.readLong()) {
                    if (len < 0 || len > MAX_CHUNK_BYTES) {
                        throw new BootstrapImportException("Bootstrap-data v2 chunk length out of range: " + len);
                    }
                    long remaining = fileSize - counter.getCount();
                    if (len > remaining) {
                        throw new BootstrapImportException("Bootstrap-data v2 chunk length " + len
                                + " exceeds the " + remaining + " bytes remaining in the file; "
                                + "the file is truncated or its length field is corrupt");
                    }
                    if (wanted) {
                        byte[] chunk = new byte[(int) len];
                        in.readFully(chunk);
                        processor.process(tag, chunk);
                    } else {
                        skipFully(in, len);
                    }
                }
                tag = in.read();
            }
            if (tag == -1) {
                throw new BootstrapImportException("Truncated bootstrap-data v2: missing end-of-sections marker");
            }
        } catch (IOException e) {
            throw new BootstrapImportException("Error reading bootstrap-data v2 from " + dataPath, e);
        }
    }

    /**
     * Skips exactly {@code n} bytes, falling back to reading-and-discarding when the underlying stream's
     * {@code skip} cannot make progress, and detecting a chunk truncated below its declared length.
     */
    private static void skipFully(DataInputStream in, long n) throws IOException {
        long remaining = n;
        byte[] scratch = null;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (scratch == null) {
                scratch = new byte[(int) Math.min(remaining, READ_BUFFER_SIZE)];
            }
            int read = in.read(scratch, 0, (int) Math.min(remaining, scratch.length));
            if (read < 0) {
                throw new BootstrapImportException(
                        "Truncated bootstrap-data v2: section chunk shorter than its declared length");
            }
            remaining -= read;
        }
    }

    private static void readAndVerifyHeader(DataInputStream in) throws IOException {
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new BootstrapImportException("Invalid bootstrap-data v2 magic");
        }
        int version = in.read();
        if (version != (VERSION & 0xFF)) {
            throw new BootstrapImportException("Unsupported bootstrap-data v2 version: " + version);
        }
    }

    private boolean isV2(Path dataPath) {
        try (InputStream in = Files.newInputStream(dataPath)) {
            int firstByte = in.read();
            if (firstByte == -1) {
                throw new BootstrapImportException("Empty bootstrap data file: " + dataPath);
            }
            return BootstrapV2Format.isV2(firstByte);
        } catch (IOException e) {
            throw new BootstrapImportException("Error reading bootstrap data from " + dataPath, e);
        }
    }

    private static Path createValueStoreDir() {
        try {
            return Files.createTempDirectory("bootstrap-import-values");
        } catch (IOException e) {
            throw new BootstrapImportException("Failed to create a temporary directory for value staging", e);
        }
    }

    // --- shared leaf decoding (identical between v1 and v2; only the container framing differs) ---

    private void saveBlockFromTuple(RLPList tuple) {
        Block block = blockFactory.decodeBlock(
                Objects.requireNonNull(tuple.get(0).getRLPData(), "block data is missing"));
        BlockDifficulty blockDifficulty = new BlockDifficulty(
                new BigInteger(Objects.requireNonNull(tuple.get(1).getRLPData(), "block difficulty data is missing")));
        blockStore.saveBlock(block, blockDifficulty, true);
    }

    @FunctionalInterface
    private interface ChunkProcessor {
        void process(int tag, byte[] chunk) throws IOException;
    }
}
