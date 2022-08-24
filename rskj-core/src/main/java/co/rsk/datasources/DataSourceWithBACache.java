/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.datasources;


import co.rsk.bahashmaps.AbstractByteArrayHashMap;
import co.rsk.bahashmaps.MaxSizeByteArrayHashMap;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import java.io.IOException;
import java.util.*;


public class DataSourceWithBACache extends DataSourceWithCacheAndStats {

    public DataSourceWithBACache(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithBACache(KeyValueDataSource base, int cacheSize,
                                 CacheSnapshotHandler cacheSnapshotHandler) {
        super(base,cacheSize,cacheSnapshotHandler);
    }


    // We need to limit the CAHashMap cache.

    MaxSizeByteArrayHashMap innerCache;
    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                                     CacheSnapshotHandler cacheSnapshotHandler) {
        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
        int avgElementSize =88;
        long beHeapCapacity;
        boolean removeInBulk = true;
        float loadFActor =getDefaultLoadFactor();
        int initialSize = (int) (cacheSize/loadFActor);
        if (removeInBulk)
            beHeapCapacity =(long) cacheSize*avgElementSize*11/10;
        else
            beHeapCapacity =(long) cacheSize*avgElementSize*14/10;

        MaxSizeByteArrayHashMap bamap = null;
        try {
            bamap = new MaxSizeByteArrayHashMap(initialSize,loadFActor,myKR,
                    (long) beHeapCapacity,
                    null,cacheSize,
                    null,null
                    );
        } catch (IOException e) {
            e.printStackTrace();
        }
        innerCache = bamap;

        Map<ByteArrayWrapper, byte[]> cache =bamap;
        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        if (committedCache!=null) {
            list.add("longestFilledRun: "+innerCache.longestFilledRun());
            list.add("averageFilledRun: "+innerCache.averageFilledRun());
        }

        return list;
    }

    static public float getDefaultLoadFactor() {
        return 0.3f;
    }
}
