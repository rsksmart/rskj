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

import co.rsk.trie.Trie;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A strictly read-only {@link TrieStore} over the segbuild segment store.
 *
 * <p>segbuild is 97 independent trie stores, one per block range, which together hold every node
 * the chain wrote. Each chunk is self-contained for its own range: during the build every node
 * <em>read</em> was copied into the window, not only every node written, so a chunk holds
 * everything its range touched. To execute or verify blocks in a range, one chunk is opened and
 * nothing else -- no cross-chunk probing, no fallback store, no full-history trie.
 *
 * <p>This store therefore holds one chunk open at a time and is moved across chunk boundaries with
 * {@link #positionAt(long)}. That is the intended access pattern, and it is also what makes the set
 * parallelisable: the chunks share nothing.
 *
 * <h2>Read-only</h2>
 *
 * Every mutating method throws. The store is an archival artifact: it is opened through RocksDB's
 * read-only path, which does not rewrite its files, and nothing here can write to it even if a
 * caller asks. This is deliberate belt-and-braces -- the on-disk copy is also expected to be
 * protected by the filesystem.
 *
 * <h2>A miss is an error</h2>
 *
 * Inside a chunk's own range, a node that is not found means the chunk is damaged or incomplete.
 * Returning "absent" would let a caller compute a wrong state root instead of failing, so lookups
 * throw {@link SegbuildMissingNodeException} rather than reporting emptiness. Outside a chunk's
 * range misses are expected and carry no information, which is why the store must be positioned on
 * the chunk matching the block being processed.
 */
public class SegbuildTrieStore implements TrieStore {

    private static final Logger logger = LoggerFactory.getLogger("segbuild");

    /** The only column family holding data. The default column family is unused. */
    private static final String TRIE_NODES_COLUMN_FAMILY = "trie_nodes";

    private final SegbuildChunkIndex index;

    /**
     * Optional store consulted when a chunk does not hold a node.
     *
     * <p>A chunk holds what the <em>producer</em> read while building it. A different
     * implementation replaying the same blocks may touch state the producer did not, in which case
     * a strictly self-contained chunk cannot serve the read. With no fallback that is an error, as
     * the format requires. With one, the read is served and counted, so how far a chunk falls short
     * of a given client's working set becomes a measurable number rather than a dead end.
     *
     * <p>A fallback makes timings unrepresentative of segbuild alone: read
     * {@link #getFallbackHitCount()} alongside any measurement taken with one configured.
     */
    @Nullable
    private final TrieStore fallback;

    private long fallbackHitCount;

    /** Every read asked of this store, so a cache hit rate can be read as a proportion. */
    private long readCount;

    private long cacheHitCount;

    /**
     * Bounded, least-recently-used cache of node messages, keyed by node hash.
     *
     * <p>Without one, every step of every trie walk is a RocksDB lookup: a JNI crossing, an index
     * binary search, a data block scan and a decompression -- for a node that may be the trie root
     * being read for the thousandth time. The upper levels of a trie are common to every path, so a
     * modest cache absorbs a disproportionate share of the reads.
     *
     * <p>Cleared when the store moves to another chunk. Node hashes are content-addressed so the
     * entries would stay valid, but a chunk switch means the working set has moved on.
     */
    @Nullable
    private final Map<ByteArrayWrapper, byte[]> nodeCache;

    /**
     * The last miss this store refused to serve.
     *
     * <p>Block execution catches exceptions thrown while running a transaction and treats them as
     * an invalid transaction, so a missing node surfaces to the caller as a state root that does
     * not match rather than as the storage failure it is. Recording it lets the caller say what
     * actually went wrong.
     */
    @Nullable
    private SegbuildMissingNodeException lastMiss;

    /** The state root the caller is executing from, reported alongside a miss. */
    @Nullable
    private byte[] currentStateRoot;

    /**
     * Records which state root is being walked, so a miss can be reported as the triple a repair
     * pass needs: the node hash, the state root, and the key being walked.
     */
    public void walkingStateRoot(byte[] stateRoot) {
        this.currentStateRoot = stateRoot;
    }

    @Nullable
    private SegbuildChunk openChunk;
    @Nullable
    private RocksDB db;
    @Nullable
    private ColumnFamilyHandle trieNodes;
    private final List<ColumnFamilyHandle> openHandles = new ArrayList<>();
    @Nullable
    private DBOptions openOptions;

    private long chunkOpenCount;

    public SegbuildTrieStore(@Nonnull SegbuildChunkIndex index) {
        this(index, null);
    }

    public SegbuildTrieStore(@Nonnull SegbuildChunkIndex index, @Nullable TrieStore fallback) {
        this(index, fallback, 0);
    }

    /**
     * @param cacheSize how many node messages to keep in memory, or 0 for no cache
     */
    public SegbuildTrieStore(@Nonnull SegbuildChunkIndex index, @Nullable TrieStore fallback, int cacheSize) {
        this.index = index;
        this.fallback = fallback;
        this.nodeCache = cacheSize > 0 ? newLruCache(cacheSize) : null;
    }

    private static Map<ByteArrayWrapper, byte[]> newLruCache(int maxEntries) {
        return new LinkedHashMap<ByteArrayWrapper, byte[]>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ByteArrayWrapper, byte[]> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Node reads served from memory rather than from the chunk. */
    public long getCacheHitCount() {
        return cacheHitCount;
    }

    private SegbuildMissingNodeException recordMiss(SegbuildMissingNodeException e) {
        lastMiss = e;
        return e;
    }

    /** The last read a chunk could not serve, or null if every read has been served. */
    @Nullable
    public SegbuildMissingNodeException getLastMiss() {
        return lastMiss;
    }

    /** How many reads a chunk could not serve and the fallback did. Zero when strict. */
    public long getFallbackHitCount() {
        return fallbackHitCount;
    }

    public boolean hasFallback() {
        return fallback != null;
    }

    /**
     * Opens the chunk covering {@code blockNumber}, closing whichever chunk was open before.
     * Does nothing if that chunk is already open.
     *
     * @throws IllegalArgumentException if no chunk covers the block
     */
    public void positionAt(long blockNumber) {
        SegbuildChunk chunk = index.requireChunkFor(blockNumber);

        if (openChunk != null && openChunk.getIndex() == chunk.getIndex()) {
            return;
        }

        closeCurrentChunk();
        if (nodeCache != null) {
            nodeCache.clear();
        }
        openChunkStore(chunk);
    }

    private void openChunkStore(SegbuildChunk chunk) {
        java.nio.file.Path sealed = chunk.getSealedDirectory();

        if (!Files.isDirectory(sealed)) {
            throw new IllegalArgumentException(String.format(
                    "Segbuild %s has no '%s' directory at %s",
                    chunk, SegbuildChunk.SEALED_DIRECTORY, sealed));
        }

        RocksDB.loadLibrary();

        // RocksDB requires every column family in the store to be named when opening, including
        // the unused default one.
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
        descriptors.add(new ColumnFamilyDescriptor(
                TRIE_NODES_COLUMN_FAMILY.getBytes(StandardCharsets.UTF_8)));

        List<ColumnFamilyHandle> handles = new ArrayList<>();
        DBOptions options = new DBOptions();

        try {
            // Read-only: an ordinary read-write open would replay the write-ahead log and rewrite
            // files, which must never happen to an archival artifact. The read-only path still
            // makes log contents visible, which matters because a chunk's data may live entirely
            // in its log with no .sst files yet.
            RocksDB opened = RocksDB.openReadOnly(options, sealed.toString(), descriptors, handles);

            this.db = opened;
            this.openOptions = options;
            this.openHandles.addAll(handles);
            this.trieNodes = handles.get(1);
            this.openChunk = chunk;
            this.chunkOpenCount++;

            logger.debug("Opened segbuild {} read-only at {}", chunk, sealed);
        } catch (RocksDBException e) {
            options.close();
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
            String detail = String.valueOf(e.getMessage()).contains("format_version")
                    ? " The chunk was written by a newer RocksDB than this build links against, so its"
                      + " table files cannot be read. A newer org.rocksdb:rocksdbjni is required."
                    : "";
            throw new IllegalArgumentException(
                    String.format("Cannot open segbuild %s read-only at %s.%s", chunk, sealed, detail), e);
        }
    }

    private void closeCurrentChunk() {
        if (db != null) {
            db.close();
            db = null;
        }
        for (ColumnFamilyHandle handle : openHandles) {
            handle.close();
        }
        openHandles.clear();
        if (openOptions != null) {
            openOptions.close();
            openOptions = null;
        }
        trieNodes = null;
        openChunk = null;
    }

    /** Every read asked of this store, whether the chunk served it or the cache did. */
    public long getReadCount() {
        return readCount;
    }

    @Nullable
    private byte[] get(byte[] key) {
        readCount++;
        if (db == null || trieNodes == null) {
            throw new IllegalStateException(
                    "Segbuild store is not positioned on a chunk; call positionAt(blockNumber) first");
        }

        ByteArrayWrapper cacheKey = nodeCache == null ? null : new ByteArrayWrapper(key);
        if (cacheKey != null) {
            byte[] cached = nodeCache.get(cacheKey);
            if (cached != null) {
                cacheHitCount++;
                return cached;
            }
        }

        try {
            byte[] value = db.get(trieNodes, key);
            if (value != null && cacheKey != null) {
                nodeCache.put(cacheKey, value);
            }
            return value;
        } catch (RocksDBException e) {
            throw new IllegalStateException(String.format(
                    "Segbuild lookup failed in %s for key %s", openChunk, ByteUtil.toHexString(key)), e);
        }
    }

    @Override
    public Optional<Trie> retrieve(byte[] hash) {
        byte[] message = get(hash);

        if (message == null) {
            if (fallback == null) {
                throw recordMiss(new SegbuildMissingNodeException(openChunk, hash, "node", currentStateRoot));
            }
            fallbackHitCount++;
            return fallback.retrieve(hash);
        }

        return Optional.of(Trie.fromMessage(message, this).markAsSaved());
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        byte[] value = get(hash);

        if (value == null) {
            if (fallback == null) {
                throw recordMiss(new SegbuildMissingNodeException(openChunk, hash, "long value", currentStateRoot));
            }
            fallbackHitCount++;
            return fallback.retrieveValue(hash);
        }

        return value;
    }

    @Override
    public Optional<TrieDTO> retrieveDTO(byte[] hash) {
        byte[] message = get(hash);

        if (message == null) {
            if (fallback == null) {
                throw recordMiss(new SegbuildMissingNodeException(openChunk, hash, "node", currentStateRoot));
            }
            fallbackHitCount++;
            return fallback.retrieveDTO(hash);
        }

        return Optional.of(TrieDTO.decodeFromMessage(message, this));
    }

    /**
     * Reports whether a key is present without treating absence as an error. Only for callers that
     * are genuinely probing, such as diagnostics; block execution must use {@link #retrieve}.
     */
    public boolean contains(byte[] hash) {
        return get(hash) != null;
    }

    @Override
    public void flush() {
        // Nothing is ever buffered for writing.
    }

    @Override
    public void save(Trie trie) {
        throw readOnly("save a trie");
    }

    @Override
    public void saveValue(byte[] value) {
        throw readOnly("save a value");
    }

    @Override
    public void saveDTO(TrieDTO trieDTO) {
        throw readOnly("save a trie DTO");
    }

    @Override
    public void dispose() {
        closeCurrentChunk();
    }

    private static UnsupportedOperationException readOnly(String what) {
        return new UnsupportedOperationException(
                "The segbuild store is read-only and cannot " + what);
    }

    @Nullable
    public SegbuildChunk getOpenChunk() {
        return openChunk;
    }

    /** How many times a chunk has been opened, for diagnosing boundary crossings. */
    public long getChunkOpenCount() {
        return chunkOpenCount;
    }

    public SegbuildChunkIndex getIndex() {
        return index;
    }

    /**
     * Raised when a chunk does not hold a node it was asked for. Inside a chunk's own range this
     * means the chunk is damaged or incomplete, and must not be mistaken for an absent key.
     */
    public static class SegbuildMissingNodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SegbuildMissingNodeException(@Nullable SegbuildChunk chunk, byte[] hash, String kind) {
            this(chunk, hash, kind, null);
        }

        public SegbuildMissingNodeException(@Nullable SegbuildChunk chunk, byte[] hash, String kind,
                                            @Nullable byte[] stateRoot) {
            super(String.format(
                    "Segbuild %s does not contain %s %s. Inside a chunk's range this means the chunk "
                            + "is damaged or incomplete; it must not be read as an absent key. "
                            + "To have it repaired, report: node=%s stateRoot=%s key=%s",
                    chunk == null ? "(no chunk open)" : chunk.toString(),
                    kind,
                    ByteUtil.toHexString(Arrays.copyOf(hash, hash.length)),
                    ByteUtil.toHexString(Arrays.copyOf(hash, hash.length)),
                    stateRoot == null ? "(unknown)" : ByteUtil.toHexString(stateRoot),
                    describeKey()));
        }

        private static String describeKey() {
            byte[] key = co.rsk.trie.TrieReadContext.currentKey();
            return key == null ? "(none recorded)" : ByteUtil.toHexString(key);
        }
    }
}
