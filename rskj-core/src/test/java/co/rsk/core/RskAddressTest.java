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
import org.spongycastle.util.encoders.DecoderException;

public class RskAddressTest {
    @Test
    public void testEquals() {
        RskAddress senderA = RskAddress.fromHex("0000000000000000000000000000000001000006");
        RskAddress senderB = RskAddress.fromHex("0000000000000000000000000000000001000006");
        RskAddress senderC = RskAddress.fromHex("0000000000000000000000000000000001000008");
        RskAddress senderD = RskAddress.nullAddress();
        RskAddress senderE = RskAddress.fromHex("0x00002000f000000a000000330000000001000006");

        Assert.assertEquals(senderA, senderB);
        Assert.assertNotEquals(senderA, senderC);
        Assert.assertNotEquals(senderA, senderD);
        Assert.assertNotEquals(senderA, senderE);
    }

    @Test
    public void nullAddress() {
        RskAddress senderA = RskAddress.fromHex("0000000000000000000000000000000000000000");
        RskAddress senderB = RskAddress.fromHex("0x0000000000000000000000000000000000000000");
        RskAddress senderC = new RskAddress(new byte[20]);
        RskAddress senderD = RskAddress.fromHex("0000000000000000000000000000000000000001");

        Assert.assertEquals(RskAddress.nullAddress(), senderA);
        Assert.assertEquals(RskAddress.nullAddress(), senderB);
        Assert.assertEquals(RskAddress.nullAddress(), senderC);
        Assert.assertNotEquals(RskAddress.nullAddress(), senderD);
    }

    @Test(expected = RuntimeException.class)
    public void invalidLongAddress() {
        RskAddress.fromHex("00000000000000000000000000000000010000060");
    }

    @Test(expected = RuntimeException.class)
    public void invalidShortAddress() {
        RskAddress.fromHex("0000000000000000000000000000000001006");
    }

    @Test
    public void oddLengthAddressPaddedWithOneZero() {
        RskAddress.fromHex("000000000000000000000000000000000100006");
    }

    @Test(expected = DecoderException.class)
    public void invalidHexAddress() {
        RskAddress.fromHex("000000000000000000000000000000000100000X");
    }

    @Test(expected = NullPointerException.class)
    public void invalidNullAddress() {
        new RskAddress(null);
    }

    @Test(expected = RuntimeException.class)
    public void invalidShortAddressBytes() {
        new RskAddress(new byte[19]);
    }

    @Test(expected = RuntimeException.class)
    public void invalidLongAddressBytes() {
        new RskAddress(new byte[21]);
    }

}
