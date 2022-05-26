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

package co.rsk.core.bc;

import org.ethereum.db.ByteArrayWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

//TODO(JULI):
// * Next step should be to check whether a key is written in the cache but also deleted in the same transaction. This key shouldn't be considered as a written key.

public class ReadWrittenKeysTracker implements IReadWrittenKeysTracker {
    private Map<ByteArrayWrapper, Long> threadByReadKey;
    private Map<ByteArrayWrapper, Long> threadByWrittenKey;
    private boolean collision;

    public ReadWrittenKeysTracker() {
        this.threadByReadKey = new HashMap<>();
        this.threadByWrittenKey = new HashMap<>();
        this.collision = false;
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalReadKeys(){
        return this.threadByReadKey.keySet();
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalWrittenKeys(){
        return this.threadByWrittenKey.keySet();
    }

    public boolean hasCollided() { return this.collision;}

    @Override
    public void addNewReadKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();

        if (threadByWrittenKey.containsKey(key)) {
            collision |= threadId != threadByWrittenKey.get(key);
        }

        threadByReadKey.put(key, threadId);
    }

    @Override
    public void addNewWrittenKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();
        if (threadByWrittenKey.containsKey(key)) {
            collision |= threadId != threadByWrittenKey.get(key);
        }

        if (threadByReadKey.containsKey(key)) {
            collision |= threadId != threadByReadKey.get(key);
        }

        threadByWrittenKey.put(key, threadId);
    }

    @Override
    public void clear() {
        this.threadByReadKey = new HashMap<>();
        this.threadByWrittenKey = new HashMap<>();
    }
}
