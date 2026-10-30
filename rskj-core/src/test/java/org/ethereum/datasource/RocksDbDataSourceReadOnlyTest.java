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
package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database opened read-only must serve reads and refuse every write, so that a tool which is
 * only meant to read an archival database cannot modify it by mistake.
 */
class RocksDbDataSourceReadOnlyTest {

    @TempDir
    Path databaseDir;

    private static final byte[] KEY = new byte[]{1, 2, 3};
    private static final byte[] VALUE = new byte[]{4, 5, 6};

    private RocksDbDataSource writable() {
        RocksDbDataSource ds = new RocksDbDataSource("store", databaseDir.toString());
        ds.init();
        return ds;
    }

    private RocksDbDataSource readOnly() {
        RocksDbDataSource ds = new RocksDbDataSource("store", databaseDir.toString(), true);
        ds.init();
        return ds;
    }

    private void seed() {
        RocksDbDataSource ds = writable();
        try {
            ds.put(KEY, VALUE);
        } finally {
            ds.close();
        }
    }

    @Test
    void readsWorkThroughAReadOnlyHandle() {
        seed();

        RocksDbDataSource ds = readOnly();
        try {
            assertTrue(ds.isReadOnly());
            assertArrayEquals(VALUE, ds.get(KEY));
        } finally {
            ds.close();
        }
    }

    @Test
    void writesAreRefused() {
        seed();

        RocksDbDataSource ds = readOnly();
        try {
            assertThrows(UnsupportedOperationException.class, () -> ds.put(KEY, new byte[]{9}));
            assertThrows(UnsupportedOperationException.class, () -> ds.delete(KEY));

            Map<ByteArrayWrapper, byte[]> rows = new HashMap<>();
            rows.put(new ByteArrayWrapper(KEY), new byte[]{9});
            assertThrows(UnsupportedOperationException.class,
                    () -> ds.updateBatch(rows, Collections.emptySet()));

            // The value is still what it was.
            assertArrayEquals(VALUE, ds.get(KEY));
        } finally {
            ds.close();
        }
    }

    /**
     * Write-buffering caches flush unconditionally when closed. A flush with nothing in it writes
     * nothing, so it must not turn an ordinary shutdown into an error.
     */
    @Test
    void anEmptyBatchIsNotAWrite() {
        seed();

        RocksDbDataSource ds = readOnly();
        try {
            assertDoesNotThrow(() -> ds.updateBatch(Collections.emptyMap(), Collections.emptySet()));
        } finally {
            ds.close();
        }
    }

    @Test
    void aReadOnlyOpenDoesNotCreateAMissingDatabase() {
        RocksDbDataSource ds = new RocksDbDataSource("never-existed", databaseDir.toString(), true);

        assertThrows(IllegalArgumentException.class, ds::init);
    }

    @Test
    void theDefaultConstructorIsStillWritable() {
        RocksDbDataSource ds = writable();
        try {
            assertTrue(!ds.isReadOnly());
            ds.put(KEY, VALUE);
            assertArrayEquals(VALUE, ds.get(KEY));
        } finally {
            ds.close();
        }
    }

    @Test
    void askingForAReadOnlyLevelDbIsRefusedRatherThanSilentlyWritable() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyValueDataSourceUtils.makeDataSource(databaseDir.resolve("lvl"), DbKind.LEVEL_DB, true));
    }
}
