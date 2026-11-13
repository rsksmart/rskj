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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table format this node writes decides which other builds can read its databases: RocksDB
 * reads older formats but not newer ones. Left to the library's default it would move whenever the
 * library is upgraded, quietly making every database written afterwards unreadable by the previous
 * build. It is pinned instead, and this checks the pin holds.
 */
class RocksDbWriteFormatTest {

    @TempDir
    Path databaseDir;

    @Test
    void writesThePinnedTableFormat() throws IOException {
        RocksDbDataSource ds = new RocksDbDataSource("store", databaseDir.toString());
        ds.init();
        try {
            ds.put(new byte[]{1}, new byte[]{2});
        } finally {
            ds.close();
        }

        // RocksDB records the options it ran with beside the data.
        List<String> formatLines;
        try (Stream<Path> files = Files.list(databaseDir.resolve("store"))) {
            Path options = files.filter(p -> p.getFileName().toString().startsWith("OPTIONS-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("RocksDB wrote no OPTIONS file"));
            formatLines = Files.readAllLines(options).stream()
                    .map(String::trim)
                    .filter(l -> l.startsWith("format_version="))
                    .collect(Collectors.toList());
        }

        assertFalse(formatLines.isEmpty(), "no format_version recorded");
        for (String line : formatLines) {
            assertEquals("format_version=5", line,
                    "the written table format changed; databases written by this build can no longer "
                            + "be read by builds that write format 5");
        }
    }

    @Test
    void whatIsWrittenCanBeReadBack() {
        RocksDbDataSource writer = new RocksDbDataSource("store", databaseDir.toString());
        writer.init();
        try {
            writer.put(new byte[]{1}, new byte[]{2});
        } finally {
            writer.close();
        }

        RocksDbDataSource reader = new RocksDbDataSource("store", databaseDir.toString(), true);
        reader.init();
        try {
            assertTrue(reader.get(new byte[]{1})[0] == 2);
        } finally {
            reader.close();
        }
    }
}
