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

import co.rsk.datasources.FlatDbDataSource;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.ethereum.TestUtils.randomBytes;
import static org.junit.Assert.*;

public class FlatDbDataSourceTest {

    public String getTmpDbPath() throws IOException {
        return databaseDir.newFolder().toPath().resolve("test").toString();
    }

    public FlatDbDataSource createTmpFlatDb(String tmpDbPath) throws IOException {
        return new FlatDbDataSource(1000,10_000,
                tmpDbPath ,
                FlatDbDataSource.CreationFlag.All,
                FlatDbDataSource.latestDBVersion,false);
    }
    @Rule
    public TemporaryFolder databaseDir = new TemporaryFolder();

    @Test
    public void testBatchUpdating() throws IOException {
        String tmpPath = getTmpDbPath();
        FlatDbDataSource dataSource = createTmpFlatDb(tmpPath);
        dataSource.init();

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);
        
        dataSource.updateBatch(batch, Collections.emptySet());

        assertEquals(batchSize, dataSource.keys().size());
        
        dataSource.close();
    }

    @Test
    public void testPutting() throws IOException {
        testPut(false);
    }
    @Test
    public void testPutting_reopen() throws IOException {
        testPut(true);
    }
    private void testPut(boolean closeAndReopen) throws IOException {
        String tmpPath = getTmpDbPath();
        FlatDbDataSource dataSource = createTmpFlatDb(tmpPath);
        dataSource.init();

        byte[] data = randomBytes(32);
        byte[] key = Keccak256Helper.keccak256(data);
        dataSource.put(key, data);

        if (closeAndReopen) {
            dataSource.close();
            dataSource = createTmpFlatDb(tmpPath);
            dataSource.init();

        }
        assertNotNull(dataSource.get(key));
        assertArrayEquals(dataSource.get(key),data);
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
