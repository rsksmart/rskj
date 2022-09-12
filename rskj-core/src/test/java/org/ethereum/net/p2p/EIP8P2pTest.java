/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net.p2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.bouncycastle.util.encoders.Hex.decode;

/**
 * @author Mikhail Kalinin
 * @since 18.02.2016
 */
class EIP8P2pTest {

    // devp2p hello packet advertising version 55 and containing a few additional list elements
    @Test
    void test1() {

        HelloMessage msg = new HelloMessage(decode(
                "f87137916b6e6574682f76302e39312f706c616e39cdc5836574683dc6846d6f726b1682270fb840" +
                "fda1cff674c90c9a197539fe3dfb53086ace64f83ed7c6eabec741f7f381cc803e52ab2cd55d5569" +
                "bce4347107a310dfd5f88a010cd2ffd1005ca406f1842877c883666f6f836261720304"));

        assertEquals(55, msg.getP2PVersion());
    }
}
