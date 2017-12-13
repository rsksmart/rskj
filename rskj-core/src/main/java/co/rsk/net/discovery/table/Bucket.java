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

package co.rsk.net.discovery.table;

import org.ethereum.net.rlpx.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by mario on 21/02/17.
 */
public class Bucket {
    private Map<String, BucketEntry> entries;
    private final int bucketSize;
    private final int id;

    public Bucket(int bucketSize, int bucketId) {
        this.bucketSize = bucketSize;
        this.entries = new ConcurrentHashMap<>();
        this.id = bucketId;
    }

    public int getId() {
        return id;
    }

    public synchronized OperationResult addNode(Node node) {
        if (entries.size() == bucketSize) {
            return new OperationResult(false, this.getOldestEntry());
        } else {
            String nodeId = node.getHexId();
            BucketEntry entry = this.entries.get(nodeId);
            if (entry == null) {
                entry = new BucketEntry(node);
                entries.put(nodeId, entry);
            }
            entry.updateTime();
            return new OperationResult(true, entry);
        }
    }

    public synchronized OperationResult removeNode(Node node) {
        BucketEntry toRemove = this.entries.remove(node.getHexId());

        if (toRemove != null) {
            return new OperationResult(true, toRemove);
        } else {
            return new OperationResult(false, null);
        }
    }

    public synchronized Set<BucketEntry> getEntries() {
        return new HashSet<>(this.entries.values());
    }

    public synchronized BucketEntry getOldestEntry() {
        List<BucketEntry> bucketEntries = new ArrayList<>();
        bucketEntries.addAll(this.entries.values());
        Collections.sort(bucketEntries, new BucketEntryComparator());
        return bucketEntries.get(0);
    }

    public void updateEntry(Node node) {
        BucketEntry entry = this.entries.get(node.getHexId());

        if (entry != null) {
            entry.updateTime();
        }
    }
}
