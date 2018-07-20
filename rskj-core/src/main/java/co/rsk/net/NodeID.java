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

package co.rsk.net;

import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * NodeID is a wrapper over the nodeID byte array used by Ethereum.
 */
public class NodeID {

    private final byte[] nodeID;

    public NodeID(@Nonnull final byte[] nodeID) {
        this.nodeID = nodeID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NodeID nodeID1 = (NodeID) o;

        return Arrays.equals(nodeID, nodeID1.nodeID);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nodeID);
    }

    @Nonnull
    public byte[] getID() { return nodeID; }

    @Override
    public String toString() {
        return "NodeID{" + Hex.toHexString(nodeID) + '}';
    }
}
