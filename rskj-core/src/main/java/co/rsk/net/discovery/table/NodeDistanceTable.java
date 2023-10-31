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

import co.rsk.net.NodeID;
import org.ethereum.net.rlpx.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by mario on 21/02/17.
 */
public class NodeDistanceTable {
    private final Map<Integer, Bucket> buckets = new ConcurrentHashMap<>();
    private final Node localNode;
    private final DistanceCalculator distanceCalculator;

    public NodeDistanceTable(int numberOfBuckets, int entriesPerBucket, Node localNode) {
        this.localNode = localNode;
        this.distanceCalculator = new DistanceCalculator(numberOfBuckets);

        for (int i = 0; i < numberOfBuckets; i++) {
            buckets.put(i, new Bucket(entriesPerBucket, i));
        }
    }

    public synchronized OperationResult addNode(Node node) {
        return getNodeBucket(node).addNode(node);
    }

    public synchronized OperationResult removeNode(Node node) {
        return getNodeBucket(node).removeNode(node);
    }

    public synchronized List<Node> getClosestNodes(NodeID nodeId) {
        return getAllNodes().stream()
                .sorted(new NodeDistanceComparator(nodeId, this.distanceCalculator))
                .collect(Collectors.toList());
    }

    private Bucket getNodeBucket(Node node) {
        int distance = this.distanceCalculator.calculateDistance(this.localNode.getId(), node.getId()) - 1;
        distance = (distance >= 0) ? distance : 0;

        return this.buckets.get(distance);
    }

    public Set<Node> getAllNodes() {
        Set<Node> ret = new HashSet<>();

        for (Bucket bucket : this.buckets.values()) {
            ret.addAll(bucket.getEntries().stream()
                    .map(BucketEntry::getNode).collect(Collectors.toList()));
        }

        return ret;
    }

    public void updateEntry(Node node) {
        Bucket bucket = getNodeBucket(node);
        bucket.updateEntry(node);
    }

}
