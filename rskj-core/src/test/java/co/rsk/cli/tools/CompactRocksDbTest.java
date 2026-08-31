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
import co.rsk.util.NodeStopper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CompactRocksDbTest {

    @TempDir
    private Path tempDir;

    @Test
    void compactsEveryDataSourceInDatabaseDir() {
        String databaseDir = tempDir.resolve("db").toString();

        Path blocksPath = Paths.get(databaseDir, "blocks");
        Path stateRootsPath = Paths.get(databaseDir, "stateRoots");

        putEntry(
                blocksPath,
                "key-blocks".getBytes(StandardCharsets.UTF_8),
                "value-blocks".getBytes(StandardCharsets.UTF_8)
        );
        putEntry(
                stateRootsPath,
                "key-state".getBytes(StandardCharsets.UTF_8),
                "value-state".getBytes(StandardCharsets.UTF_8)
        );

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        NodeStopper stopper = mock(NodeStopper.class);

        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(databaseDir).when(rskSystemProperties).databaseDir();
        doReturn("no_compression").when(rskSystemProperties).databaseRocksDbCompressionType();
        doReturn(DbKind.ROCKS_DB).when(rskSystemProperties).databaseKind();
        doReturn(DbKind.ROCKS_DB).when(rskContext).getDbKind(databaseDir);

        CompactRocksDb compactRocksDb = new CompactRocksDb();
        compactRocksDb.execute(new String[]{"--compressionType", "lz4"}, () -> rskContext, stopper);

        verify(stopper).stop(0);

        assertArrayEquals(
                "value-blocks".getBytes(StandardCharsets.UTF_8),
                getEntry(blocksPath, "key-blocks".getBytes(StandardCharsets.UTF_8))
        );
        assertArrayEquals(
                "value-state".getBytes(StandardCharsets.UTF_8),
                getEntry(stateRootsPath, "key-state".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void failsWhenCurrentDatabaseKindIsNotRocksDb() {
        String databaseDir = tempDir.resolve("db").toString();

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        NodeStopper stopper = mock(NodeStopper.class);

        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(databaseDir).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        doReturn(DbKind.LEVEL_DB).when(rskContext).getDbKind(databaseDir);

        CompactRocksDb compactRocksDb = new CompactRocksDb();
        compactRocksDb.execute(new String[]{}, () -> rskContext, stopper);

        verify(stopper).stop(1);
    }

    private static void putEntry(Path dataSourcePath, byte[] key, byte[] value) {
        KeyValueDataSource dataSource = KeyValueDataSourceUtils.makeDataSource(dataSourcePath, DbKind.ROCKS_DB);
        dataSource.put(key, value);
        dataSource.close();
    }

    private static byte[] getEntry(Path dataSourcePath, byte[] key) {
        KeyValueDataSource dataSource = KeyValueDataSourceUtils.makeDataSource(dataSourcePath, DbKind.ROCKS_DB);
        byte[] value = dataSource.get(key);
        dataSource.close();
        return value;
    }
}


