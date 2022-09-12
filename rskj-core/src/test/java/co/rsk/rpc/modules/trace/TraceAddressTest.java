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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TraceAddressTest {
    @Test
    void getTopAddress() {
        TraceAddress address = new TraceAddress();

        Assertions.assertArrayEquals(new int[0], address.toAddress());
    }

    @Test
    void getChildAddress() {
        TraceAddress parent = new TraceAddress();
        TraceAddress address = new TraceAddress(parent, 1);

        Assertions.assertArrayEquals(new int[] { 1 }, address.toAddress());
    }

    @Test
    void getGrandChildAddress() {
        TraceAddress grandparent = new TraceAddress();
        TraceAddress parent = new TraceAddress(grandparent, 1);
        TraceAddress address = new TraceAddress(parent, 2);

        Assertions.assertArrayEquals(new int[] { 1, 2 }, address.toAddress());
    }
}
