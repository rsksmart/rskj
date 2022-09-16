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

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.util.*;

import static org.ethereum.TestUtils.randomBytes;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class RocksDbDataSourceTest {

    @Rule
    public TemporaryFolder databaseDir = new TemporaryFolder();

    @Test
    public void testBatchUpdating() throws IOException {
        RocksDbDataSource dataSource = new RocksDbDataSource("test", databaseDir.newFolder().getPath());
        dataSource.init();

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);
        
        dataSource.updateBatch(batch, Collections.emptySet());

        assertEquals(batchSize, dataSource.keys().size());

        try (DataSourceKeyIterator iterator = dataSource.keyIterator()){

            iterator.seekToFirst();

            assertTrue(iterator.hasNext());
            assertNotNull(iterator.next());
        } catch (Exception e) {
            e.printStackTrace();
        }

        dataSource.close();
    }

    @Test
    public void testPutting() throws IOException {
        RocksDbDataSource dataSource = new RocksDbDataSource("test", databaseDir.newFolder().getPath());
        dataSource.init();

        byte[] key = randomBytes(32);
        dataSource.put(key, randomBytes(32));

        assertNotNull(dataSource.get(key));
        assertEquals(1, dataSource.keys().size());
        
        dataSource.close();
    }
    private static Map<ByteArrayWrapper, byte[]> createBatch(int batchSize) {
        HashMap<ByteArrayWrapper, byte[]> result = new HashMap<>();
        for (int i = 0; i < batchSize; i++) {
            result.put(ByteUtil.wrap(randomBytes(32)), randomBytes(32));
        }
        return result;
    }

}
