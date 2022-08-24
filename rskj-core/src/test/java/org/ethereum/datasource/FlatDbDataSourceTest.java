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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.ethereum.TestUtils.randomBytes;
import static org.junit.Assert.*;

public class FlatDbDataSourceTest {

    public String getTmpDbPath() throws IOException {
        return databaseDir.newFolder().toPath().resolve("test").toString();
    }

    public FlatDbDataSource createTmpFlatDb(String tmpDbPath) throws IOException {
        // FlatDbDataSource.CreationFlag.All
        EnumSet<FlatDbDataSource.CreationFlag> someFlags =
                EnumSet.of(
                        FlatDbDataSource.CreationFlag.atomicBatches,
                        FlatDbDataSource.CreationFlag.useDBForDescriptions);

        return new FlatDbDataSource(1000,10_000,
                tmpDbPath ,
                someFlags ,
                FlatDbDataSource.latestDBVersion,false);
    }
    @Rule
    public TemporaryFolder databaseDir = new TemporaryFolder(new File("/tmp/myTmp"));

    @Test
    public void testBatchUpdatingInterrupted() throws IOException {
        // We test a batch update that is interrupted by a power failure.
        // the result should be that nothing gets written.
        String tmpPath = getTmpDbPath();
        FlatDbDataSource dataSource = createTmpFlatDb(tmpPath);
        dataSource.init();

        // first write a single key
        byte[] data1 = randomBytes(32);
        byte[] key1 = Keccak256Helper.keccak256(data1);
        dataSource.put(key1, data1);
        dataSource.flush(); // make sure we flush the data to disk.

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);


        dataSource.updateBatchInterrupted(batch, Collections.emptySet(),50);

        dataSource.powerFailure();

        // Now create another database for the same files:
        FlatDbDataSource dataSource2 = createTmpFlatDb(tmpPath);
        dataSource2.init();

        // The key1/value1 must still ve there
        assertNotNull(dataSource2.get(key1));
        assertArrayEquals(dataSource2.get(key1),data1);
        assertEquals(1, dataSource2.keys().size());

        // But the batch should not be there
        for(Map.Entry<ByteArrayWrapper, byte[]> entry : batch.entrySet()) {
            assertNull(dataSource2.get(entry.getKey().getData()));
        }

    }
    @Test
    public void testBatchUpdatingInterrupted2() throws IOException {
        // We test a batch update that is interrupted by a power failure.
        // the result should be that nothing gets written.
        String tmpPath = getTmpDbPath();
        FlatDbDataSource dataSource = createTmpFlatDb(tmpPath);
        dataSource.init();

        // first write a single key
        byte[] data1 = randomBytes(32);
        byte[] key1 = Keccak256Helper.keccak256(data1);
        dataSource.put(key1, data1);
        dataSource.flush(); // make sure we flush the data to disk.

        final int batchSize = 3;
        Map<ByteArrayWrapper, byte[]> batch1 = createBatch(batchSize);
        Map<ByteArrayWrapper, byte[]> batch2 = createBatch(batchSize);

        dataSource.updateBatch(batch1, Collections.emptySet());
        dataSource.updateBatch(batch2, Collections.emptySet());
        // we don't write the actual data until flush()
        // now we generate a power failure in the middle of flush
        dataSource.flushWithPowerFailure();

        // Now create another database for the same files:
        FlatDbDataSource dataSource2 = createTmpFlatDb(tmpPath);
        dataSource2.init();

        // The key1/value1 must still ve there
        assertNotNull(dataSource2.get(key1));
        assertArrayEquals(dataSource2.get(key1),data1);
        assertEquals(batchSize*2+1, dataSource2.keys().size());

        // But the batch should 1 be there
        for(Map.Entry<ByteArrayWrapper, byte[]> entry : batch1.entrySet()) {
            assertNull(dataSource2.get(entry.getKey().getData()));
        }
        for(Map.Entry<ByteArrayWrapper, byte[]> entry : batch2.entrySet()) {
            assertNull(dataSource2.get(entry.getKey().getData()));
        }
    }
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
