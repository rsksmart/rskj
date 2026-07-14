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
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
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

import static co.rsk.db.importer.BootstrapV2Format.TAG_BLOCKS;
import static co.rsk.db.importer.BootstrapV2Format.TAG_END;
import static co.rsk.db.importer.BootstrapV2Format.TAG_NODES;
import static co.rsk.db.importer.BootstrapV2Format.TAG_VALUES;
import static co.rsk.db.importer.BootstrapV2Format.VERSION;

public class BootstrapImporter {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapImporter.class);

    private static final int READ_BUFFER_SIZE = 64 * 1024;
    // A chunk is read into a single byte[], so per-chunk memory must stay bounded. The exporter flushes
    // once its buffer crosses CHUNK_MAX on an element boundary, so a well-formed chunk is at most
    // CHUNK_MAX plus one element; 2x CHUNK_MAX gives ample headroom while rejecting a corrupt length that
    // would otherwise allocate up to a ~2 GiB byte[].
    private static final long MAX_CHUNK_BYTES = 2 * BootstrapV2Format.CHUNK_MAX;

    private final BootstrapDataProvider bootstrapDataProvider;
    private final BlockStore blockStore;
    private final TrieStore trieStore;
    private final BlockFactory blockFactory;

    public BootstrapImporter(
            BlockStore blockStore,
            TrieStore trieStore,
            BlockFactory blockFactory,
            BootstrapDataProvider bootstrapDataProvider) {
        this.blockStore = blockStore;
        this.trieStore = trieStore;
        this.blockFactory = blockFactory;
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
        // Long values are co-located ahead of the nodes that reference them: the exporter writes the
        // values section before the nodes section, so a single streaming pass suffices. Each value is
        // written straight to the destination store; every node then resolves its long value from that
        // same store at save time (including embedded long-value children reached via parent recursion).
        // No separate value-staging store and no second file scan are needed.
        long start = System.currentTimeMillis();
        logger.debug("Inserting blocks and state...");
        // v1 implicitly required both the blocks and the nodes section to be present (it polled them off a
        // queue); v2 dispatches by tag, so we tally each section and assert non-empty blocks/nodes below to
        // fail fast on a file missing either section rather than "succeeding" with no state and only
        // crashing later at first state access.
        SectionCounts counts = new SectionCounts();
        scanSections(dataPath, Set.of(TAG_BLOCKS, TAG_VALUES, TAG_NODES), (tag, chunk) -> {
            if (tag == TAG_BLOCKS) {
                counts.blocks += importBlocks(chunk);
            } else if (tag == TAG_VALUES) {
                counts.values += importValues(chunk);
            } else if (tag == TAG_NODES) {
                counts.nodes += importNodes(chunk);
            }
        });
        if (counts.blocks == 0) {
            throw new BootstrapImportException("Bootstrap-data v2 has no blocks section (or it is empty); refusing to import incomplete data");
        }
        if (counts.nodes == 0) {
            throw new BootstrapImportException("Bootstrap-data v2 has no state-nodes section (or it is empty); refusing to import a stateless snapshot");
        }
        blockStore.flush();
        trieStore.flush();
        logger.info("Bootstrap-data v2 imported {} blocks, {} long values and {} state nodes in {} ms",
                counts.blocks, counts.values, counts.nodes, System.currentTimeMillis() - start);
    }

    /** Decodes and saves every block tuple in a blocks-section chunk; returns how many were saved. */
    private long importBlocks(byte[] chunk) {
        long imported = 0;
        for (RLPElement element : RLP.decode2(chunk)) {
            saveBlockFromTuple(RLP.decodeList(element.getRLPData()));
            imported++;
        }
        return imported;
    }

    /** Saves every long value in a values-section chunk into the destination store; returns how many. */
    private long importValues(byte[] chunk) {
        long imported = 0;
        for (RLPElement element : RLP.decode2(chunk)) {
            trieStore.saveValue(requireNonEmptyElement(element, "values"));
            imported++;
        }
        return imported;
    }

    /** Reconstructs and saves every state node in a nodes-section chunk; returns how many were saved. */
    private long importNodes(byte[] chunk) {
        long imported = 0;
        for (RLPElement element : RLP.decode2(chunk)) {
            saveNode(Trie.fromMessage(requireNonEmptyElement(element, "nodes"), trieStore));
            imported++;
        }
        return imported;
    }

    /**
     * Returns the element's RLP data, rejecting an empty element. {@link RLPElement#getRLPData()} is
     * {@code null} for an empty element; a value or node cannot be empty, so a {@code null} here means a
     * corrupt section — surfaced as an actionable import error instead of a downstream NPE.
     */
    private static byte[] requireNonEmptyElement(RLPElement element, String section) {
        byte[] data = element.getRLPData();
        if (data == null) {
            throw new BootstrapImportException("Bootstrap-data v2 " + section
                    + " section contains an empty element; the file is corrupt");
        }
        return data;
    }

    /**
     * Saves a single state node. A node's long value is resolved lazily from the destination store (where
     * the preceding values section already wrote it), so a value missing (or length-inconsistent) there
     * surfaces deep inside {@code save} as a generic {@link IllegalArgumentException}; we translate it into
     * an actionable import error that points at the real cause (incomplete/corrupt bootstrap data) instead
     * of letting the opaque exception escape.
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
     * Streams the v2 file, invoking {@code processor} once per chunk that belongs to a section in
     * {@code tagsOfInterest}, in file order. Sections are dispatched by tag; the v2 import reads every
     * section in a single pass (the exporter co-locates values before the nodes that reference them, so
     * value/block/node chunks are processed as they stream past). That ordering is enforced structurally:
     * a nodes section appearing before the values section is rejected up front. Chunks for tags outside
     * {@code tagsOfInterest} are skipped without being read into memory or decoded — including tags this
     * reader does not recognize, so a newer exporter can add optional sections (e.g. a metadata manifest)
     * without breaking an older reader. Each wanted chunk is read into a bounded {@code byte[]}, handed
     * off, then discarded. The full structural scan (header, chunk-length and end-of-section sentinels,
     * end-of-sections marker) is validated regardless of which tags are of interest, and the file must end
     * exactly at the end-of-sections marker — trailing bytes are rejected as non-canonical.
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

            boolean valuesSectionSeen = false;
            int tag = in.read();
            while (tag != -1 && tag != TAG_END) {
                valuesSectionSeen = checkSectionOrder(tag, valuesSectionSeen);
                // Unknown/unwanted section tags are skipped (their chunks are still length-validated), so
                // an older reader tolerates optional sections a newer exporter may add.
                boolean wanted = tagsOfInterest.contains(tag);
                readSectionChunks(in, counter, fileSize, tag, wanted, processor);
                tag = in.read();
            }
            if (tag == -1) {
                throw new BootstrapImportException("Truncated bootstrap-data v2: missing end-of-sections marker");
            }
            // tag == TAG_END here. The v2 format is canonical: nothing may follow the end-of-sections
            // marker. Reject trailing bytes (a tampered, truncated, or concatenated file) rather than
            // silently ignoring them, matching the v1 path's whole-payload decode.
            if (in.read() != -1) {
                throw new BootstrapImportException("Bootstrap-data v2 has trailing bytes after the "
                        + "end-of-sections marker; the file is corrupt or not canonical");
            }
        } catch (IOException e) {
            throw new BootstrapImportException("Error reading bootstrap-data v2 from " + dataPath, e);
        }
    }

    /**
     * Enforces v2 section ordering: the values section must be co-located ahead of the nodes section, since
     * each node resolves its long values from the destination store at save time. Rejecting a mis-ordered
     * file here — at the section-tag level, before any node is processed — makes the failure deterministic
     * rather than surfacing later only on the first node that references a long value. The check sees empty
     * sections too (the tag byte is read even when a section carries no chunks), so an all-short-values
     * snapshot still passes. Returns the updated "values section seen" flag.
     */
    private static boolean checkSectionOrder(int tag, boolean valuesSectionSeen) {
        if (tag == TAG_VALUES) {
            return true;
        }
        if (tag == TAG_NODES && !valuesSectionSeen) {
            throw new BootstrapImportException("Bootstrap-data v2 sections are out of order: the nodes "
                    + "section appears before the values section, but long values must be co-located "
                    + "before the nodes that reference them");
        }
        return valuesSectionSeen;
    }

    /**
     * Reads one section's chunks up to its end-of-section sentinel, handing each wanted chunk to
     * {@code processor} and skipping the rest without allocating. Each declared length is bounded before
     * any {@code byte[]} is allocated.
     */
    private void readSectionChunks(DataInputStream in, CountingInputStream counter, long fileSize,
                                   int tag, boolean wanted, ChunkProcessor processor) throws IOException {
        for (long len = in.readLong(); len != 0L; len = in.readLong()) {
            validateChunkLength(len, fileSize - counter.getCount());
            if (wanted) {
                byte[] chunk = new byte[(int) len];
                in.readFully(chunk);
                processor.process(tag, chunk);
            } else {
                skipFully(in, len);
            }
        }
    }

    /**
     * Bounds a declared chunk length against the per-chunk ceiling ({@code MAX_CHUNK_BYTES}) and the bytes
     * actually remaining in the file, so a corrupt or oversized length is rejected before allocation.
     */
    private static void validateChunkLength(long len, long remaining) {
        if (len < 0 || len > MAX_CHUNK_BYTES) {
            throw new BootstrapImportException("Bootstrap-data v2 chunk length out of range: " + len);
        }
        if (len > remaining) {
            throw new BootstrapImportException("Bootstrap-data v2 chunk length " + len
                    + " exceeds the " + remaining + " bytes remaining in the file; "
                    + "the file is truncated or its length field is corrupt");
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
        byte[] expectedMagic = BootstrapV2Format.magic();
        byte[] magic = new byte[expectedMagic.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, expectedMagic)) {
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
            if (BootstrapV2Format.isV2(firstByte)) {
                return true;
            }
            if (BootstrapV2Format.isV1(firstByte)) {
                return false;
            }
            throw new BootstrapImportException(String.format(
                    "Unrecognized bootstrap-data format in %s: first byte 0x%02X is neither the v2 magic "
                            + "('R') nor a v1 RLP list prefix (0xc0+)", dataPath, firstByte));
        } catch (IOException e) {
            throw new BootstrapImportException("Error reading bootstrap data from " + dataPath, e);
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

    /** Per-section element tallies gathered during a v2 import (mutated from the streaming callback). */
    private static final class SectionCounts {
        private long blocks;
        private long values;
        private long nodes;
    }
}
