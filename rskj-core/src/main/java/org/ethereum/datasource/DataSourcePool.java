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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.slf4j.LoggerFactory.getLogger;

public class DataSourcePool {

    private static final Logger logger = getLogger("db");
    private static ConcurrentMap<String, DataSource> pool = new ConcurrentHashMap<>();

    public static KeyValueDataSource levelDbByName(String name, String databaseDir) {
        KeyValueDataSource dataSource = new LevelDbDataSource(name, databaseDir);
        DataSource result = pool.putIfAbsent(name, dataSource);
        if (result == null) {
            result = dataSource;
            logger.debug("Data source '{}' created and added to pool.", name);
        } else {
            logger.debug("Data source '{}' returned from pool.", name);
        }

        synchronized (result) {
            if (!result.isAlive()) {
                result.init();
            }
        }

        return (KeyValueDataSource) result;
    }

    public static void closeDataSource(String name){
        DataSource dataSource = pool.get(name);
        if (dataSource == null) {
            return;
        }
        
        synchronized (dataSource) {
            pool.remove(name);
            dataSource.close();
            logger.debug("Data source '{}' closed and removed from pool.\n", dataSource.getName());
        }
    }

    public static void clear() {
         // All references to current dataSource should be destroyed after each flush.
         // We don't check count reference anymore. The only cleanup point is flush.
         // This method will be called for each contract referenced in DetailsDataStore
        Iterator<Map.Entry<String, DataSource>> iterator = pool.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, DataSource> next = iterator.next();
            DataSource dataSource = next.getValue();
            synchronized (dataSource) {
                iterator.remove();
                dataSource.close();
                logger.debug("Data source '{}' closed and removed from pool.\n", dataSource.getName());
            }
        }
    }
}
