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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReadWrittenKeysTracker implements IReadWrittenKeysTracker {

    private Map<Long, Set<ByteArrayWrapper>> readKeysByThread;

    private Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread;


    public ReadWrittenKeysTracker() {
        this.readKeysByThread = new HashMap<>();
        this.writtenKeysByThread = new HashMap<>();
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadReadKeys(){
        long threadId = Thread.currentThread().getId();
        if (this.readKeysByThread.containsKey(threadId)) {
            return new HashSet<>(this.readKeysByThread.get(threadId));
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadWrittenKeys(){
        long threadId = Thread.currentThread().getId();
        if (this.writtenKeysByThread.containsKey(threadId)) {
            return new HashSet<>(this.writtenKeysByThread.get(threadId));
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public Map<Long, Set<ByteArrayWrapper>> getReadKeysByThread() {
        return new HashMap<>(this.readKeysByThread);
    }

    @Override
    public Map<Long, Set<ByteArrayWrapper>> getWrittenKeysByThread() {
        return new HashMap<>(this.writtenKeysByThread);
    }

    @Override
    public synchronized void addNewReadKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();
        Set<ByteArrayWrapper> readKeys = readKeysByThread.containsKey(threadId)? readKeysByThread.get(threadId) : new HashSet<>();
        readKeys.add(key);
        readKeysByThread.put(threadId, readKeys);
    }

    @Override
    public synchronized void addNewWrittenKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();
        Set<ByteArrayWrapper> writtenKeys = writtenKeysByThread.containsKey(threadId)? writtenKeysByThread.get(threadId) : new HashSet<>();
        writtenKeys.add(key);
        writtenKeysByThread.put(threadId, writtenKeys);
    }

    @Override
    public synchronized void clear() {
        this.readKeysByThread = new HashMap<>();
        this.writtenKeysByThread = new HashMap<>();
    }
}
