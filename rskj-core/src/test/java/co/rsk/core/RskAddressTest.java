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
import org.bouncycastle.util.encoders.DecoderException;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class RskAddressTest {
    @Test
    public void testEquals() {
        RskAddress senderA = new RskAddress("0000000000000000000000000000000001000006");
        RskAddress senderB = new RskAddress("0000000000000000000000000000000001000006");
        RskAddress senderC = new RskAddress("0000000000000000000000000000000001000008");
        RskAddress senderD = RskAddress.nullAddress();
        RskAddress senderE = new RskAddress("0x00002000f000000a000000330000000001000006");

        Assert.assertEquals(senderA, senderB);
        Assert.assertNotEquals(senderA, senderC);
        Assert.assertNotEquals(senderA, senderD);
        Assert.assertNotEquals(senderA, senderE);
    }

    @Test
    public void zeroAddress() {
        RskAddress senderA = new RskAddress("0000000000000000000000000000000000000000");
        RskAddress senderB = new RskAddress("0x0000000000000000000000000000000000000000");
        RskAddress senderC = new RskAddress(new byte[20]);

        Assert.assertEquals(senderA, senderB);
        Assert.assertEquals(senderB, senderC);
        Assert.assertNotEquals(RskAddress.nullAddress(), senderC);
    }

    @Test
    public void nullAddress() {
        Assert.assertArrayEquals(RskAddress.nullAddress().getBytes(), new byte[0]);
    }

    @Test
    public void jsonString_nullAddress() {
        Assert.assertNull(RskAddress.nullAddress().toJsonString());
    }

    @Test
    public void jsonString_otherAddress() {
        String address = "0x0000000000000000000000000000000000000001";
        RskAddress rskAddress = new RskAddress(address);

        Assert.assertEquals(address, rskAddress.toJsonString());
    }

    @Test(expected = RuntimeException.class)
    public void invalidLongAddress() {
        new RskAddress("00000000000000000000000000000000010000060");
    }

    @Test(expected = RuntimeException.class)
    public void invalidShortAddress() {
        new RskAddress("0000000000000000000000000000000001006");
    }

    @Test
    public void oddLengthAddressPaddedWithOneZero() {
        new RskAddress("000000000000000000000000000000000100006");
    }

    @Test(expected = DecoderException.class)
    public void invalidHexAddress() {
        new RskAddress("000000000000000000000000000000000100000X");
    }

    @Test(expected = NullPointerException.class)
    public void invalidNullAddressBytes() {
        new RskAddress((byte[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void invalidNullAddressString() {
        new RskAddress((String) null);
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
