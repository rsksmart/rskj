/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import org.ethereum.config.SystemProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.ethereum.TestUtils.randomBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class LevelDbDataSourceTest {

    @Rule
    public TemporaryFolder dbsTempFolder = new TemporaryFolder();

    private LevelDbDataSource underTest;

    private static byte[] A_KEY = "lala".getBytes();
    private static byte[] A_VALUE = "lele".getBytes();

    @Before
    public void before() {
        SystemProperties.CONFIG.setDataBaseDir(dbsTempFolder.getRoot().getAbsolutePath());
        underTest = new LevelDbDataSource("test");
        underTest.init();
    }

    @After
    public void close() {
        underTest.close();
    }

    @Test
    public void testBatchUpdating() {
        final int batchSize = 100;
        Map<byte[], byte[]> batch = createBatch(batchSize);
        
        underTest.updateBatch(batch);

        assertEquals(batchSize, underTest.keys().size());
    }

    @Test
    public void testDeleteSimple() {
        underTest.put(A_KEY, A_VALUE);
        assertNotNull(underTest.get(A_KEY));
        underTest.delete(A_KEY);
        assertNull(underTest.get(A_VALUE));
    }

    @Test
    public void testDeleteMultipleWithKeysAssertion() {
        int batchSize = 100;
        Map<byte[], byte[]> batch = createBatch(batchSize);
        underTest.updateBatch(batch);

        for (byte[] key: batch.keySet()) {
            underTest.delete(key);
            assertEquals(--batchSize, underTest.keys().size());
        }

        assertEquals(0, underTest.keys().size());
    }

    @Test
    public void testPutting() {
        byte[] key = randomBytes(32);
        underTest.put(key, randomBytes(32));

        assertNotNull(underTest.get(key));
        assertEquals(1, underTest.keys().size());
    }

    @Test
    public void testDestroy() {
        underTest = new LevelDbDataSource("to-destroy");
        underTest.init();
        underTest.close();

        File thisDbPath = Paths.get(dbsTempFolder.getRoot().getAbsolutePath(), "to-destroy").toFile();

        assertTrue(thisDbPath.exists());
        underTest.destroyDB(thisDbPath);
        assertFalse(thisDbPath.exists());
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static Map<byte[], byte[]> createBatch(int batchSize) {
        HashMap<byte[], byte[]> result = new HashMap<>();
        for (int i = 0; i < batchSize; i++) {
            result.put(randomBytes(32), randomBytes(32));
        }
        return result;
    }

}