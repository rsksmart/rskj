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

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.slf4j.LoggerFactory.getLogger;

public class DataSourcePool {

    private static final Logger logger = getLogger("db");
    private static ConcurrentMap<String, DataSourceEx> pool = new ConcurrentHashMap<>();
    private static long timeUnused = 0L;

    private DataSourcePool() {
    }

    public static KeyValueDataSource hashMapDBByName(String name){
        return (KeyValueDataSource) getDataSourceFromPool(name, new HashMapDB());
    }

    public static KeyValueDataSource levelDbByName(String name) {
        return (KeyValueDataSource) getDataSourceFromPool(name, new LevelDbDataSource());
    }

    private static synchronized DataSource getDataSourceFromPool(String name, @Nonnull DataSource dataSource) {
        dataSource.setName(name);
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
            if (!result.getDataSource().isAlive()) result.getDataSource().init();
        }

        return result.getDataSource();
    }

    public static synchronized void closeDataSource(String name){
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

        dataSource.close();

        logger.debug("Data source '{}' closed and removed from pool.\n", dataSource.getName());
    }

    /**
     * Closes the opened datasources that were not used in the last timeUnused milliseconds
     */
    public static synchronized void closeUnusedDataSources() {
        if (timeUnused == 0)
            return;

        closeUnusedDataSources(timeUnused);
    }

    public static void setTimeUnused(long unused) {
        timeUnused = unused;
    }

    @VisibleForTesting
    public static synchronized void closeUnusedDataSources(long unused) {
        long now = System.currentTimeMillis();
        long expire = now - unused;

        List<String> toremove = new ArrayList<>();

        for (Map.Entry<String, DataSourceEx> entry : pool.entrySet()) {
            DataSourceEx dsx = entry.getValue();
            DataSource ds = dsx.getDataSource();

            if (!(ds instanceof LevelDbDataSource))
                continue;

            LevelDbDataSource ldb = (LevelDbDataSource)ds;

            if (ldb.getLastTimeUsed() <= expire) {
                ldb.close();
                toremove.add(entry.getKey());
            }
        }

        for (String key : toremove)
            pool.remove(key);
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
