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

package co.rsk.net.discovery.message;

/**
 * Created by mario on 13/02/17.
 */
public enum DiscoveryMessageType {

    PING(1), PONG(2), FIND_NODE(3), NEIGHBORS(4);

    private int type;

    DiscoveryMessageType(int type) {
        this.type = type;
    }

    public int getTypeValue() {
        return this.type;
    }

    public static DiscoveryMessageType valueOfType(int type) {
        for(DiscoveryMessageType t : DiscoveryMessageType.values()) {
            if (t.getTypeValue() == type) {
                return t;
            }
        }

        throw new IllegalArgumentException("Invalid peer discovery message type");
    }

}
