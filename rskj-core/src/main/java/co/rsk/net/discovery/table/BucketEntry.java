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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.net.rlpx.Node;

/**
 * Created by mario on 21/02/17.
 */
public class BucketEntry {
    private final Node node;
    private long lastSeenTime;

    public BucketEntry(Node node) {
        this.node = node;
        this.lastSeenTime = System.currentTimeMillis();
    }

    public Node getNode() {
        return this.node;
    }

    public long lastSeen() {
        return this.lastSeenTime;
    }

    public void updateTime() {
        this.lastSeenTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("node", node)
                .append("lastSeenTime", lastSeenTime)
                .toString();
    }
}
