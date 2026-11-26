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

import co.rsk.cli.PicoCliToolRskContextAware;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.datasource.RocksDbDataSource;
import org.rocksdb.CompressionType;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@CommandLine.Command(name = "compact-rocksdb", mixinStandardHelpOptions = true, version = "compact-rocksdb 1.0",
        description = "Compacts all RocksDB data sources under database.dir.")
public class CompactRocksDb extends PicoCliToolRskContextAware {

    @CommandLine.Option(
            names = {"-c", "--compressionType"},
            description = "RocksDB compression type to use during compaction (for example: lz4, lz4hc, no_compression). Defaults to database.rocksdb.compressionType."
    )
    private String compressionTypeValue;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        String databaseDir = ctx.getRskSystemProperties().databaseDir();
        DbKind dbKind = ctx.getDbKind(databaseDir);

        if (dbKind != DbKind.ROCKS_DB) {
            throw new IllegalStateException("compact-rocksdb only supports RocksDB-backed nodes");
        }

        Path databasePath = Paths.get(databaseDir);
        String effectiveCompressionTypeValue = compressionTypeValue != null
                ? compressionTypeValue
                : ctx.getRskSystemProperties().databaseRocksDbCompressionType();
        CompressionType compressionType = RocksDbDataSource.parseCompressionType(effectiveCompressionTypeValue);
        printInfo("Using RocksDB compression type {}", compressionType.name());

        List<Path> dataSourcePaths;
        try (Stream<Path> databaseEntries = Files.list(databasePath)) {
            dataSourcePaths = databaseEntries
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }

        if (dataSourcePaths.isEmpty()) {
            printInfo("No RocksDB data sources found in {}", databasePath);
            return 0;
        }

        for (Path dataSourcePath : dataSourcePaths) {
            String dataSourceName = dataSourcePath.getFileName().toString();
            printInfo("Compacting RocksDB data source {}", dataSourceName);

            KeyValueDataSource dataSource = KeyValueDataSourceUtils.makeDataSource(dataSourcePath, dbKind, compressionType);
            try {
                ((RocksDbDataSource) dataSource).compactRange();
            } finally {
                dataSource.close();
            }
        }

        printInfo("Compaction completed for {} data source(s)", dataSourcePaths.size());
        return 0;
    }
}


