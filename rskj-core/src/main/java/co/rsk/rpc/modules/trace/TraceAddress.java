/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc.modules.trace;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public class TraceAddress {
    private static final int[] EMPTY_ADDRESS = new int[0];

    private final TraceAddress parent;
    private final int index;

    public TraceAddress() {
        this.parent = null;
        this.index = 0;
    }

    public TraceAddress(TraceAddress parent, int index) {
        this.parent = parent;
        this.index = index;
    }

    @JsonValue
    public int[] toAddress() {
        if (this.parent == null) {
            return EMPTY_ADDRESS;
        }

        int[] parentAddress = this.parent.toAddress();

        int[] address = Arrays.copyOf(parentAddress, parentAddress.length + 1);

        address[address.length - 1] = this.index;

        return address;
    }
}
