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

package co.rsk.core;

import org.junit.Assert;
import org.junit.Test;

public class RskAddressTest {
    @Test
    public void testEquals() {
        RskAddress senderA = RskAddress.fromHex("aabbccdd");
        RskAddress senderB = RskAddress.fromHex("aabbccdd");
        RskAddress senderC = RskAddress.fromHex("aabbccddee");
        RskAddress senderD = RskAddress.fromHex("");
        RskAddress senderE = RskAddress.fromHex("112233");

        Assert.assertEquals(senderA, senderB);
        Assert.assertNotEquals(senderA, senderC);
        Assert.assertNotEquals(senderA, senderD);
        Assert.assertNotEquals(senderA, senderE);
    }

}
