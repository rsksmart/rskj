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
package co.rsk.config;

/**
 * Tuning for the MapDB-backed block index, resolved from {@code database.blocksIndex.*}.
 *
 * <p>Profiling a mainnet sync put roughly 60% of the block-processing thread's wall time inside
 * this one store: about half in {@code RandomAccessFile} seek/read syscalls and about half in the
 * {@code ObjectInputStream} round trip used to (de)serialise the per-height entries. Neither cost
 * is inherent - both come from MapDB being opened with no options at all - so each of the knobs
 * below exists to remove one of them.
 *
 * <p>They are ordinary configuration properties: they live in {@code rsk.conf}, and because the
 * config loader layers {@link com.typesafe.config.ConfigFactory#systemProperties()} over the files,
 * any of them can be overridden with {@code -Ddatabase.blocksIndex.<key>=<value>}. Defaults are
 * deliberately conservative so an existing node upgrading to this build behaves exactly as before;
 * a host with memory to spare opts in.
 */
public class BlocksIndexConfig {

    private final boolean mmap;
    private final boolean asyncWrite;
    private final boolean transactionDisable;
    private final int cacheEntries;
    private final boolean compactSerializer;

    public BlocksIndexConfig(boolean mmap, boolean asyncWrite, boolean transactionDisable,
                             int cacheEntries, boolean compactSerializer) {
        this.mmap = mmap;
        this.asyncWrite = asyncWrite;
        this.transactionDisable = transactionDisable;
        this.cacheEntries = cacheEntries;
        this.compactSerializer = compactSerializer;
    }

    /**
     * What a node gets when nothing is configured: MapDB's own behaviour, plus the read cache that
     * is safe everywhere. Mirrors the values in reference.conf.
     */
    public static BlocksIndexConfig defaults() {
        return new BlocksIndexConfig(false, false, false, 200_000, false);
    }

    /**
     * Memory-map the index file instead of reaching it through RandomAccessFile, which turns a
     * seek+read syscall pair per access into a page-cache read. Worth the memory only where the
     * index file comfortably fits alongside everything else; it is address space, so a 32-bit or
     * very memory-tight host should leave it off.
     */
    public boolean isMmap() {
        return mmap;
    }

    /** Move index writes onto MapDB's background writer so they leave the block-processing thread. */
    public boolean isAsyncWrite() {
        return asyncWrite;
    }

    /**
     * Drop MapDB's write-ahead log. This is the fastest single option and also the only dangerous
     * one: without the log, a crash or a hard kill can leave the index structurally broken, and the
     * only repair is a resync. Enable it for throughput experiments and disposable nodes, not for a
     * node whose database you would mind losing.
     */
    public boolean isTransactionDisable() {
        return transactionDisable;
    }

    /** Entries held in the read cache in front of MapDB. */
    public int getCacheEntries() {
        return cacheEntries;
    }

    /**
     * Write index entries with a compact hand-rolled encoding instead of Java serialization, which
     * spends most of its time in reflection and classloader lookups ({@code Class.forName},
     * {@code VM.latestUserDefinedLoader}) rather than on the ~40 bytes actually being stored.
     *
     * <p>This changes the on-disk format. It cannot be turned on or off under an existing index -
     * the store records which encoding wrote it and refuses to open with the other one rather than
     * silently returning garbage - so changing it requires a resync.
     */
    public boolean isCompactSerializer() {
        return compactSerializer;
    }
}
