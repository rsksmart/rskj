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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.ethereum.TestUtils.randomBytes;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void mergeMultiTrieStoreDBs() throws IOException {
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();

        List<Path> sourcePaths = new ArrayList<>();
        Set<byte[]> sourceKeys = new HashSet<>();
        int sourcesCount = 3;
        for (int i = 0; i < sourcesCount; i++) {
            Path sourcePath = testDatabasesDirectory.resolve(String.format("src-%d", i));
            sourcePaths.add(sourcePath);
            KeyValueDataSource originDataSource = RocksDbDataSource.makeDataSource(sourcePath);
            byte[] currentElement = {(byte) i};
            sourceKeys.add(currentElement);
            originDataSource.put(currentElement, currentElement);
            originDataSource.close();
        }

        Path destination = testDatabasesDirectory.resolve("destination");
        RocksDbDataSource.mergeDataSources(destination, sourcePaths);
        KeyValueDataSource destinationDataSource = RocksDbDataSource.makeDataSource(destination);
        try {
            Set<byte[]> destinationKeys = destinationDataSource.keys();
            Assert.assertThat(destinationKeys, hasSize(sourcesCount));

            for (byte[] destinationKey : destinationKeys) {
                boolean keyFound = false;
                for (byte[] sourceKey : sourceKeys) {
                    if (Arrays.equals(destinationKey, sourceKey)) {
                        keyFound = true;
                        break;
                    }
                }
                if (keyFound) {
                    Assert.assertThat(destinationDataSource.get(destinationKey), equalTo(destinationKey));
                } else {
                    StringBuilder sourceKeysToString = new StringBuilder("[");
                    for (byte[] sourceKey : sourceKeys) {
                        sourceKeysToString.append(Arrays.toString(sourceKey)).append(", ");
                    }
                    sourceKeysToString.delete(sourceKeysToString.length() - 2, sourceKeysToString.length());
                    sourceKeysToString.append("]");
                    Assert.fail(String.format("%s wasn't found in %s", Arrays.toString(destinationKey), sourceKeysToString.toString()));
                }
            }
        } finally {
            destinationDataSource.close();
        }
    }

    private static Map<ByteArrayWrapper, byte[]> createBatch(int batchSize) {
        HashMap<ByteArrayWrapper, byte[]> result = new HashMap<>();
        for (int i = 0; i < batchSize; i++) {
            result.put(ByteUtil.wrap(randomBytes(32)), randomBytes(32));
        }
        return result;
    }

}
