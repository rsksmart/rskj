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

import co.rsk.config.TestSystemProperties;
import org.ethereum.datasource.DataSourcePool;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by ajlopez on 26/07/2016.
 */
public class DataSourcePoolTest {

    private TestSystemProperties config;

    @Before
    public void setup(){
        config = new TestSystemProperties();
    }

    @Test
    public void openAndCloseDataSource() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName(config, "test1");
        dataSource.close();
    }

    @Test
    public void openUseAndCloseDataSource() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName(config, "test2");
        dataSource.put(new byte[] { 0x01 }, new byte[] { 0x02 });
        dataSource.close();
        KeyValueDataSource dataSource2 = DataSourcePool.levelDbByName(config, "test2");
        byte[] result = dataSource2.get(new byte[] { 0x01 });
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals(0x02, result[0]);
        dataSource2.close();
    }

    @Test
    public void openUseAndCloseDataSourceTwice() {
        KeyValueDataSource dataSource = DataSourcePool.levelDbByName(config, "test3");
        KeyValueDataSource dataSource2 = DataSourcePool.levelDbByName(config, "test3");

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
            KeyValueDataSource dataSource = DataSourcePool.levelDbByName(config, "test4");
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
}
