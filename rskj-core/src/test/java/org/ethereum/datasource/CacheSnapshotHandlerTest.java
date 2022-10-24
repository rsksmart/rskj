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
import org.ethereum.util.ByteUtil;
import org.ethereum.util.MapSnapshot;
import org.ethereum.util.TempFileCreator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CacheSnapshotHandlerTest {

    @TempDir
    public Path folder;

    private Path cacheSnapshotPath;
    private MapSnapshot.Factory mapSnapshotFactory;
    private TempFileCreator tempFileCreator;

    private CacheSnapshotHandler cacheSnapshotHandler;

    @BeforeEach
    void setUp() throws IOException {
        File testFolder = folder.resolve("test").toFile();
        testFolder.mkdir();

        Path tempFilePath = testFolder.toPath().resolve("tmp");

        cacheSnapshotPath = testFolder.toPath().resolve("cache");

        mapSnapshotFactory = spy(new MapSnapshot.Factory() {});

        tempFileCreator = mock(TempFileCreator.class);
        doAnswer(in -> {
            File file = tempFilePath.toFile();
            Assertions.assertTrue(file.createNewFile());
            return file;
        }).when(tempFileCreator).createTempFile(anyString(), anyString());

        cacheSnapshotHandler = new CacheSnapshotHandler(mapSnapshotFactory, tempFileCreator, cacheSnapshotPath);
    }

    @Test
    void load_WhenNoCache_MapShouldBeEmpty() {
        Map<ByteArrayWrapper, byte[]> cache = new HashMap<>();

        cacheSnapshotHandler.load(cache);

        Assertions.assertTrue(cache.isEmpty());
        verify(mapSnapshotFactory, never()).makeInputSnapshot(any());
    }

    @Test
    void load_WhenCacheCorrupted_CacheShouldBeRenamed() throws IOException {
        Assertions.assertTrue(cacheSnapshotPath.toFile().createNewFile());

        //noinspection unchecked
        Map<ByteArrayWrapper, byte[]> cache = (Map<ByteArrayWrapper, byte[]>) mock(Map.class);

        cacheSnapshotHandler.load(cache);

        Assertions.assertTrue(cacheSnapshotPath.resolveSibling(cacheSnapshotPath.getFileName() + ".err").toFile().exists());
        verify(cache, atLeastOnce()).clear();
        verify(cache, never()).put(any(), any());
    }

    @Test
    void save_WhenEmptyMap_NothingShouldBeSavedAndExistingCacheShouldBeRemoved() throws IOException {
        Assertions.assertTrue(cacheSnapshotPath.toFile().createNewFile());

        Map<ByteArrayWrapper, byte[]> cache = new HashMap<>();

        cacheSnapshotHandler.save(cache);

        Assertions.assertFalse(cacheSnapshotPath.toFile().exists());
        verify(tempFileCreator, never()).createTempFile(anyString(), anyString());
        verify(mapSnapshotFactory, never()).makeOutputSnapshot(any());
    }

    @Test
    void save_WhenNonEmptyMap_MapShouldBeSavedAndExistingCacheShouldBeReplaced() throws IOException {
        Assertions.assertTrue(cacheSnapshotPath.toFile().createNewFile());
        Assertions.assertEquals(0, cacheSnapshotPath.toFile().length());

        Map<ByteArrayWrapper, byte[]> cache = new HashMap<>();
        cache.put(ByteUtil.wrap(new byte[] {1, 2, 3}), new byte[] {4, 5, 6});

        cacheSnapshotHandler.save(cache);

        Assertions.assertTrue(cacheSnapshotPath.toFile().exists());
        Assertions.assertTrue(cacheSnapshotPath.toFile().length() > 0);
        verify(tempFileCreator, atLeastOnce()).createTempFile(anyString(), anyString());
        verify(mapSnapshotFactory, atLeastOnce()).makeOutputSnapshot(any());
    }

    @Test
    void save_MapCannotBeSaved_CacheFileShouldNotBeModified() throws IOException {
        Assertions.assertTrue(cacheSnapshotPath.toFile().createNewFile());
        Assertions.assertEquals(0, cacheSnapshotPath.toFile().length());

        MapSnapshot.Out outSnapshot = mock(MapSnapshot.Out.class);
        doThrow(new IOException()).when(outSnapshot).write(anyMap());
        doReturn(outSnapshot).when(mapSnapshotFactory).makeOutputSnapshot(any());

        File tempFile = spy(tempFileCreator.createTempFile("cache", ".tmp"));
        doReturn(tempFile).when(tempFileCreator).createTempFile(anyString(), anyString());

        Map<ByteArrayWrapper, byte[]> cache = new HashMap<>();
        cache.put(ByteUtil.wrap(new byte[] {1, 2, 3}), new byte[] {4, 5, 6});

        cacheSnapshotHandler.save(cache);

        Assertions.assertTrue(cacheSnapshotPath.toFile().exists());
        Assertions.assertEquals(0, cacheSnapshotPath.toFile().length());
        verify(tempFile, atLeastOnce()).deleteOnExit();
    }
}
