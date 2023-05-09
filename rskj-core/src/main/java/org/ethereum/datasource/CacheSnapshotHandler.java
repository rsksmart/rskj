/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import org.ethereum.util.MapSnapshot;
import org.ethereum.util.TempFileCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CacheSnapshotHandler {

    private static final Logger logger = LoggerFactory.getLogger(CacheSnapshotHandler.class);

    private final MapSnapshot.Factory mapSnapshotFactory;
    private final TempFileCreator tempFileCreator;

    private final Path cacheSnapshotPath;

    public CacheSnapshotHandler(@Nonnull Path cacheSnapshotPath) {
        this(new MapSnapshot.Factory() {}, File::createTempFile, cacheSnapshotPath);
    }

    public CacheSnapshotHandler(@Nonnull MapSnapshot.Factory mapSnapshotFactory,
                                @Nonnull TempFileCreator tempFileCreator,
                                @Nonnull Path cacheSnapshotPath) {
        this.mapSnapshotFactory = Objects.requireNonNull(mapSnapshotFactory);
        this.tempFileCreator = Objects.requireNonNull(tempFileCreator);
        this.cacheSnapshotPath = Objects.requireNonNull(cacheSnapshotPath);
    }

    public void load(@Nonnull Map<ByteArrayWrapper, byte[]> cache) {
        File cacheSnapshotFile = cacheSnapshotPath.toFile();
        String relativePath = Optional.ofNullable(cacheSnapshotFile.getParentFile())
                .map(File::getName)
                .orElse("") + "/" + cacheSnapshotFile.getName();

        if (!cacheSnapshotFile.exists()) {
            logger.info("Cache snapshot file '{}' not found. Returning empty cache", relativePath);
            return;
        }

        try (MapSnapshot.In inSnapshot = mapSnapshotFactory.makeInputSnapshot(new BufferedInputStream(new FileInputStream(cacheSnapshotFile)))) {
            inSnapshot.read(cache);

            logger.info("Loaded {} cache entries from '{}'", cache.size(), relativePath);
        } catch (IOException e) {
            cache.clear();
            Path errFilePath = cacheSnapshotPath.resolveSibling(cacheSnapshotPath.getFileName() + ".err");
            File errFile = errFilePath.toFile();
            if (!errFile.exists()) {
                try {
                    Files.move(cacheSnapshotPath, errFilePath);
                } catch (IOException ex) {
                    logger.error("Cannot rename cache snapshot file", ex);
                }
            }
            logger.error("Cannot read from cache snapshot file", e);
        }
    }

    /**
     *  Saves {@code cache}'s entries into a file.
     *
     *  As this operation can take some time and be interrupted in the middle (which potentially could lead to having a corrupted file),
     *  the entries are being saved into the temporary file first, which then replaces existing one, if any.
     */
    public void save(@Nonnull Map<ByteArrayWrapper, byte[]> cache) {
        File tempFile = null;
        try {
            File cacheSnapshotFile = cacheSnapshotPath.toFile();
            String relativePath = Optional.ofNullable(cacheSnapshotFile.getParentFile())
                    .map(File::getName)
                    .orElse("") + "/" + cacheSnapshotFile.getName();

            int count = cache.size();
            if (count == 0) {
                logger.info("Cache is empty. Nothing to save to '{}'", relativePath);

                if (cacheSnapshotFile.exists() && !cacheSnapshotFile.delete()) {
                    throw new IOException("Cannot delete existing cache snapshot file '" + relativePath + "'");
                }
                return;
            }

            tempFile = tempFileCreator.createTempFile("cache", ".tmp");

            try (MapSnapshot.Out outSnapshot = mapSnapshotFactory.makeOutputSnapshot(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
                outSnapshot.write(cache);
            }

            if (cacheSnapshotFile.exists() && !cacheSnapshotFile.delete()) {
                throw new IOException("Cannot replace existing cache snapshot file '" + relativePath + "'");
            }

            Files.move(tempFile.toPath(), cacheSnapshotPath);

            logger.info("Saved {} cache entries in '{}'", count, relativePath);
        } catch (IOException e) {
            logger.error("Cannot save cache snapshot to file", e);

            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }
}
