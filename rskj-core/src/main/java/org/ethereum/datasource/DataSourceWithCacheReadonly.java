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
import javax.annotation.Nullable;

public class DataSourceWithCacheReadonly extends DataSourceWithCache {

    public static DataSourceWithCacheReadonly create(@Nonnull KeyValueDataSource base, int cacheSize) {
        return new DataSourceWithCacheReadonly(base, cacheSize, null);
    }

    public static DataSourceWithCacheReadonly createWithSnapshot(@Nonnull KeyValueDataSource base, int cacheSize, @Nullable CacheSnapshotHandler cacheSnapshotHandler) {
        return new DataSourceWithCacheReadonly(base, cacheSize, cacheSnapshotHandler);
    }

    private DataSourceWithCacheReadonly(@Nonnull KeyValueDataSource base, int cacheSize, @Nullable CacheSnapshotHandler cacheSnapshotHandler) {
        super(base, cacheSize, cacheSnapshotHandler);
    }

    @Override
    protected void updateSnapshot() {
        // nothing to do, in readonly we don't want to update snapshot
    }

    @Override
    public void flush() {
        // nothing to do, in readonly we don't want to flush to base
    }

}
