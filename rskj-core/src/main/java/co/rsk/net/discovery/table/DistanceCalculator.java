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
import org.ethereum.crypto.HashUtil;

/**
 * Calculates the distance between 2 nodes
 */
public class DistanceCalculator {
    private final int maxDistance;

    public DistanceCalculator(int maxDistance) {
        this.maxDistance = maxDistance;
    }

    /**
     * The distance is calculated as the Most Significant Bit (MSB) position
     * of the XOR between the keccak256(keccak256(nodeId)) of the 2 nodes
     * @param node1 id
     * @param node2 id
     * @return The distance between 2 nodes
     */
    public int calculateDistance(NodeID node1, NodeID node2) {
        byte[] nodeId1 = HashUtil.keccak256(HashUtil.keccak256(node1.getID()));
        byte[] nodeId2 = HashUtil.keccak256(HashUtil.keccak256(node2.getID()));
        byte[] result = new byte[nodeId1.length];

        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (((int) nodeId1[i]) ^ ((int) nodeId2[i]));
        }

        return msbPosition(result);
    }

    /**
     *
     * @param result
     * @return The MSB position of the XOR result.
     */
    private int msbPosition(byte[] result) {
        int distance = this.maxDistance;

        for (byte b : result) {
            if (b == 0) {
                distance -= 8;
            } else {
                distance -= positionsToMove(b);
                return distance;
            }
        }

        return distance;
    }

    private int positionsToMove(byte b) {
        int count = 0;

        for (int i = 7; i >= 0; i--) {
            if (((b & 0xff)& (1 << i)) == 0) {
                count++;
            } else {
                return count;
            }
        }

        return count;
    }
}
