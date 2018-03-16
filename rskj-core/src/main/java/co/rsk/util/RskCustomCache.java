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

package co.rsk.util;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by mario on 09/09/2016.
 */
public class RskCustomCache<K, T> {

    private Long timeToLive;

    private Map<K, CacheElement<T>> cache = new ConcurrentHashMap<>();

    private ScheduledExecutorService cacheTimer;

    public RskCustomCache(Long timeToLive) {
        this.timeToLive = timeToLive;

        cacheTimer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                return new Thread(r, "BlockHeaderCacheTimer");
            }
        });
    }

    public void start() {
        cacheTimer.scheduleAtFixedRate(this::cleanUp, this.timeToLive, this.timeToLive, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        cacheTimer.shutdown();
    }

    public void remove(K key) {
        this.cache.remove(key);
    }

    public void put(K key, T data) {
        this.cache.put(key, new CacheElement<T>(data, this.timeToLive));
    }

    public T get(K key) {
        T ret = null;
        CacheElement<T> element = this.cache.get(key);
        if(element != null) {
            element.updateLastAccess();
            ret = element.value();
        }
        return ret;
    }


    private void cleanUp() {
        Iterator<Map.Entry<K, CacheElement<T>>> iter = this.cache.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<K, CacheElement<T>> entry = iter.next();
            if(entry.getValue().hasExpired()){
                iter.remove();
            }
        }
    }
}
