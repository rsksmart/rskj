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

import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.slf4j.LoggerFactory.getLogger;

public class DataSourcePool {

    private static final Logger logger = getLogger("db");
    private static ConcurrentMap<String, DataSourceEx> pool = new ConcurrentHashMap<>();

    public static KeyValueDataSource levelDbByName(String name) {
        DataSource dataSource = new LevelDbDataSource(name);
        DataSourceEx dataSourceEx = new DataSourceEx(dataSource);
        DataSourceEx result = pool.putIfAbsent(name, dataSourceEx);
        if (result == null) {
            result = dataSourceEx;
            logger.debug("Data source '{}' created and added to pool.", name);
        } else {
            logger.debug("Data source '{}' returned from pool.", name);
        }

        synchronized (result) {
            result.reserve();
            if (!result.getDataSource().isAlive()) {
                result.getDataSource().init();
            }
        }

        return (KeyValueDataSource) result.getDataSource();
    }

    public static void closeDataSource(String name){
        DataSourceEx dataSourceEx = pool.get(name);

        if (dataSourceEx == null)
            return;

        DataSource dataSource = dataSourceEx.getDataSource();

        if (dataSource instanceof HashMapDB)
            return;

        dataSourceEx.release();

        if (dataSourceEx.getUseCounter() > 0)
            return;

        pool.remove(name);

        synchronized (dataSource) {
            dataSource.close();

            logger.debug("Data source '{}' closed and removed from pool.\n", dataSource.getName());
        }
    }

    private static class DataSourceEx {
        private DataSource dataSource;
        private int counter;

        public DataSourceEx(DataSource dataSource) {
            this.dataSource = dataSource;
            this.counter = 0;
        }

        public DataSource getDataSource() {
            return this.dataSource;
        }

        public int getUseCounter() {
            return this.counter;
        }

        public synchronized void reserve() {
            this.counter++;
        }

        public synchronized void release() {
            this.counter--;
        }
    }
}
