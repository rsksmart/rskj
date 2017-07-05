/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.datasource;

import org.ethereum.datasource.DataSourcePool;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 26/07/2016.
 */
public class DataSourcePoolTest {
    @Test
    public void openAndCloseDataSource() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test1");
        dataSource.close();
    }

    @Test
    public void openUseAndCloseDataSource() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test2");
        dataSource.put(new byte[] { 0x01 }, new byte[] { 0x02 });
        dataSource.close();
        KeyValueDataSource dataSource2 = DataSourcePool.levelDbByName("test2");
        byte[] result = dataSource2.get(new byte[] { 0x01 });
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals(0x02, result[0]);
        dataSource2.close();
    }

    @Test
    public void openUseAndCloseDataSourceTwice() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test3");
        KeyValueDataSource dataSource2 = DataSourcePool.levelDbByName("test3");

        Assert.assertSame(dataSource, dataSource2);

        dataSource.put(new byte[] { 0x01 }, new byte[] { 0x02 });
        DataSourcePool.closeDataSource("test3");

        byte[] result = dataSource2.get(new byte[] { 0x01 });

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals(0x02, result[0]);
        DataSourcePool.closeDataSource("test3");
    }

    @Test
    public void openAndCloseTenTimes() {
        for (int k = 0; k < 10; k++) {
            KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test4");
            dataSource.put(new byte[] { (byte) k }, new byte[] { (byte) k });
            byte[] result = dataSource.get(new byte[] { (byte) k });

            Assert.assertNotNull(result);
            Assert.assertEquals(1, result.length);
            Assert.assertEquals((byte)k, result[0]);
        }

        for (int k = 0; k < 10; k++) {
            DataSourcePool.closeDataSource("test4");
        }
    }

    @Test
    public void openAndCloseLevelDBDataSource() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test5");

        Assert.assertNotNull(dataSource);
        Assert.assertTrue(dataSource instanceof LevelDbDataSource);

        LevelDbDataSource lds = (LevelDbDataSource)dataSource;

        Assert.assertNotEquals(0, lds.getLastTimeUsed());

        dataSource.close();
    }

    @Test
    public void updateTimeOnGetAndPutValue() throws InterruptedException {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName("test5");

        Assert.assertNotNull(dataSource);
        Assert.assertTrue(dataSource instanceof LevelDbDataSource);

        LevelDbDataSource lds = (LevelDbDataSource)dataSource;

        long timeinit = lds.getLastTimeUsed();
        TimeUnit.MILLISECONDS.sleep(100);
        Assert.assertEquals(timeinit, lds.getLastTimeUsed());

        lds.put(new byte[] { 0x01, 0x02 }, new byte[] { 0x03 });

        long timeput = lds.getLastTimeUsed();
        Assert.assertNotEquals(timeinit, timeput);
        Assert.assertTrue(timeinit < timeput);

        TimeUnit.MILLISECONDS.sleep(100);

        lds.get(new byte[] { 0x01, 0x02 });

        long timeget = lds.getLastTimeUsed();
        Assert.assertNotEquals(timeput, timeget);
        Assert.assertTrue(timeput < timeget);

        dataSource.close();
    }
}
