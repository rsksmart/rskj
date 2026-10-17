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
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.db.StateRootsStore;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.trie.segbuild.SegbuildChunkIndex;
import co.rsk.trie.segbuild.SegbuildTrieStore;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.IndexedBlockStore;
import co.rsk.db.MapDBBlocksIndex;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImplV2;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An {@link RskContext} for replaying blocks that have already been produced, which opens every
 * database read-only and can read state from an alternative trie backend.
 *
 * <p>Replay is a read activity: it re-executes history to check that the code still agrees with it.
 * Writing while doing so would alter the very thing being checked, and the databases involved are
 * often archival artifacts or shared snapshots. So unless writes are explicitly allowed, every
 * store here is opened through a path that cannot modify it, and any write attempt fails loudly
 * rather than silently succeeding.
 *
 * <p>The trie backend is selectable so the same replay can be run against different stores and
 * compared.
 */
public class ReplayRskContext extends RskContext {

    /** Which store the replay reads trie nodes from. */
    public enum TrieBackend {
        /** The node's own unitrie, under {@code <database.dir>/unitrie}. */
        UNITRIE,
        /** A segbuild segment store: one self-contained trie store per block range. */
        SEGBUILD
    }

    private final TrieBackend trieBackend;

    @Nullable
    private final Path segbuildRoot;

    private final boolean allowWrites;

    /** Serve segbuild misses from the node's own unitrie, counting them, instead of failing. */
    private final boolean segbuildFallback;

    @Nullable
    private SegbuildTrieStore segbuildTrieStore;

    public ReplayRskContext(String[] args, TrieBackend trieBackend, @Nullable Path segbuildRoot, boolean allowWrites) {
        this(args, trieBackend, segbuildRoot, allowWrites, false);
    }

    public ReplayRskContext(String[] args, TrieBackend trieBackend, @Nullable Path segbuildRoot,
                            boolean allowWrites, boolean segbuildFallback) {
        super(args);
        this.trieBackend = trieBackend;
        this.segbuildRoot = segbuildRoot;
        this.allowWrites = allowWrites;
        this.segbuildFallback = segbuildFallback;
    }

    public boolean isAllowWrites() {
        return allowWrites;
    }

    public TrieBackend getTrieBackend() {
        return trieBackend;
    }

    /**
     * The segbuild store backing this context, or null when a different backend is in use. Exposed
     * so a caller can move it across chunk boundaries as it walks a block range.
     */
    @Nullable
    public SegbuildTrieStore getSegbuildTrieStore() {
        return segbuildTrieStore;
    }

    private boolean readOnly() {
        return !allowWrites;
    }

    @Override
    protected synchronized TrieStore buildTrieStore(Path trieStorePath) {
        if (trieBackend == TrieBackend.SEGBUILD) {
            if (segbuildRoot == null) {
                throw new IllegalStateException("A segbuild root directory is required for the segbuild backend");
            }
            TrieStore fallback = segbuildFallback ? buildUnitrieStore(trieStorePath) : null;
            segbuildTrieStore = new SegbuildTrieStore(SegbuildChunkIndex.scan(segbuildRoot), fallback);
            return segbuildTrieStore;
        }

        return buildUnitrieStore(trieStorePath);
    }

    private TrieStore buildUnitrieStore(Path trieStorePath) {

        RskSystemProperties properties = getRskSystemProperties();
        DbKind dbKind = getDbKind(properties.databaseDir());
        KeyValueDataSource ds = KeyValueDataSourceUtils.makeDataSource(trieStorePath, dbKind, readOnly());

        int statesCacheSize = properties.getStatesCacheSize();
        if (statesCacheSize != 0) {
            // No snapshot handler: persisting the cache would write beside the database being read.
            ds = new DataSourceWithCache(ds, statesCacheSize);
        }

        return new TrieStoreImpl(ds);
    }

    @Override
    protected synchronized ReceiptStore buildReceiptStore() {
        RskSystemProperties properties = getRskSystemProperties();
        Path receiptsPath = Paths.get(properties.databaseDir(), "receipts");
        DbKind dbKind = getDbKind(properties.databaseDir());
        KeyValueDataSource ds = KeyValueDataSourceUtils.makeDataSource(receiptsPath, dbKind, readOnly());

        int receiptsCacheSize = properties.getReceiptsCacheSize();
        if (receiptsCacheSize != 0) {
            ds = new DataSourceWithCache(ds, receiptsCacheSize);
        }

        return new ReceiptStoreImplV2(ds);
    }

    @Override
    protected StateRootsStore buildStateRootsStore() {
        RskSystemProperties properties = getRskSystemProperties();
        Path stateRootsPath = Paths.get(properties.databaseDir(), "stateRoots");
        DbKind dbKind = getDbKind(properties.databaseDir());
        KeyValueDataSource ds = KeyValueDataSourceUtils.makeDataSource(stateRootsPath, dbKind, readOnly());

        int stateRootsCacheSize = properties.getStateRootsCacheSize();
        if (stateRootsCacheSize > 0) {
            ds = new DataSourceWithCache(ds, stateRootsCacheSize);
        }

        return new StateRootsStoreImpl(ds);
    }

    @Override
    public synchronized org.ethereum.db.BlockStore buildBlockStore(String databaseDir) {
        File blockIndexDirectory = new File(databaseDir + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");

        if (!blockIndexDirectory.exists()) {
            if (readOnly()) {
                throw new IllegalArgumentException(String.format(
                        "No blocks directory to read at %s", blockIndexDirectory));
            }
            if (!blockIndexDirectory.mkdirs()) {
                throw new IllegalArgumentException(String.format(
                        "Unable to create blocks directory: %s", blockIndexDirectory));
            }
        }

        if (readOnly() && !Files.isRegularFile(dbFile.toPath())) {
            throw new IllegalArgumentException(String.format(
                    "No block index to read at %s", dbFile));
        }

        DBMaker.Maker indexMaker = DBMaker.fileDB(dbFile);
        if (readOnly()) {
            // Without this MapDB opens the index for writing and rewrites its header, which would
            // modify a snapshot merely by reading its blocks.
            indexMaker = indexMaker.readOnly();
        }
        DB indexDB = indexMaker.make();

        Path blocksDbPath = Paths.get(databaseDir, "blocks");
        DbKind dbKind = getDbKind(databaseDir);
        KeyValueDataSource blocksDB = KeyValueDataSourceUtils.makeDataSource(blocksDbPath, dbKind, readOnly());

        MapDBBlocksIndex index = new MapDBBlocksIndex(indexDB);

        return new IndexedBlockStore(getBlockFactory(), blocksDB,
                readOnly() ? new ReadOnlyBlocksIndex(index) : index);
    }
}
