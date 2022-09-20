/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class DataSourceWithCacheAndSnapshot extends DataSourceWithCache {

    private final CacheSnapshotHandler cacheSnapshotHandler;

    public static DataSourceWithCacheAndSnapshot create(@Nonnull KeyValueDataSource base, int cacheSize, CacheSnapshotHandler cacheSnapshotHandler) {
        return new DataSourceWithCacheAndSnapshot(base, cacheSize, cacheSnapshotHandler);
    }

    private DataSourceWithCacheAndSnapshot(@Nonnull KeyValueDataSource base, int cacheSize, CacheSnapshotHandler cacheSnapshotHandler) {
        super(base, cacheSize, LoggerFactory.getLogger("datasourcewithcache-snapshot"));

        this.cacheSnapshotHandler = cacheSnapshotHandler;
        cacheSnapshotHandler.load(getCommittedCache());
    }

    @Override
    protected void customClose() {
        cacheSnapshotHandler.save(getCommittedCache());
    }

}
