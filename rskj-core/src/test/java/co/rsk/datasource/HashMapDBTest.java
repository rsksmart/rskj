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

import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.Assert;
import org.junit.Test;

/** Created by ajlopez on 3/1/2016. */
public class HashMapDBTest {
    @Test
    public void putKeyValue() {
        KeyValueDataSource ds = new HashMapDB();

        byte[] key = new byte[] {0x01, 0x02};
        byte[] value = new byte[] {0x03, 0x03};

        byte[] result = ds.put(key, value);

        Assert.assertNull(result);
    }

    @Test
    public void getUnknownKeyValue() {
        KeyValueDataSource ds = new HashMapDB();

        byte[] key = new byte[] {0x01, 0x02};

        byte[] result = ds.get(key);

        Assert.assertNull(result);
    }

    @Test
    public void putAndGetKeyValue() {
        KeyValueDataSource ds = new HashMapDB();

        byte[] key = new byte[] {0x01, 0x02};
        byte[] value = new byte[] {0x03, 0x03};

        ds.put(key, value);
        byte[] result = ds.get(key);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);
    }

    @Test
    public void putAndDeleteKeyValue() {
        KeyValueDataSource ds = new HashMapDB();

        byte[] key = new byte[] {0x01, 0x02};
        byte[] value = new byte[] {0x03, 0x03};

        ds.put(key, value);
        ds.delete(key);
        byte[] result = ds.get(key);

        Assert.assertNull(result);
    }
}
