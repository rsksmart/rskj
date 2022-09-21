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

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

public class DataSourceWithCacheAndSnapshotTest extends DataSourceWithCacheTest {

    private CacheSnapshotHandler cacheSnapshotHandler;

    @Before
    public void setupDataSources() {
        this.baseDataSource = spy(new HashMapDB());
        this.cacheSnapshotHandler = mock(CacheSnapshotHandler.class);
        this.dataSourceWithCache = DataSourceWithCacheAndSnapshot.create(baseDataSource, CACHE_SIZE, cacheSnapshotHandler);
    }

    @Test
    public void checkCacheSnapshotLoadTriggered() {
        verify(cacheSnapshotHandler, atLeastOnce()).load(anyMap());
    }

    @Test
    public void checkCacheSnapshotSaveTriggered() {
        dataSourceWithCache.close();
        verify(cacheSnapshotHandler, atLeastOnce()).save(anyMap());
    }

}
