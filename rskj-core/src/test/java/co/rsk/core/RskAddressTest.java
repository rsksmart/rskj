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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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

        Assertions.assertEquals(senderA, senderB);
        Assertions.assertNotEquals(senderA, senderC);
        Assertions.assertNotEquals(senderA, senderD);
        Assertions.assertNotEquals(senderA, senderE);
    }

    @Test
    public void zeroAddress() {
        RskAddress senderA = new RskAddress("0000000000000000000000000000000000000000");
        RskAddress senderB = new RskAddress("0x0000000000000000000000000000000000000000");
        RskAddress senderC = new RskAddress(new byte[20]);

        Assertions.assertEquals(senderA, senderB);
        Assertions.assertEquals(senderB, senderC);
        Assertions.assertNotEquals(RskAddress.nullAddress(), senderC);
    }

    @Test
    public void nullAddress() {
        Assertions.assertArrayEquals(RskAddress.nullAddress().getBytes(), new byte[0]);
    }

    @Test
    public void jsonString_nullAddress() {
        Assertions.assertNull(RskAddress.nullAddress().toJsonString());
    }

    @Test
    public void jsonString_otherAddress() {
        String address = "0x0000000000000000000000000000000000000001";
        RskAddress rskAddress = new RskAddress(address);

        Assertions.assertEquals(address, rskAddress.toJsonString());
    }

    @Test
    public void invalidLongAddress() {
        Assertions.assertThrows(RuntimeException.class, () -> new RskAddress("00000000000000000000000000000000010000060"));
    }

    @Test
    public void invalidShortAddress() {
        Assertions.assertThrows(RuntimeException.class, () -> new RskAddress("0000000000000000000000000000000001006"));
    }

    @Test
    public void oddLengthAddressPaddedWithOneZero() {
        new RskAddress("000000000000000000000000000000000100006");
    }

    @Test
    public void invalidHexAddress() {
        Assertions.assertThrows(DecoderException.class, () -> new RskAddress("000000000000000000000000000000000100000X"));
    }

    @Test
    public void invalidNullAddressBytes() {
        Assertions.assertThrows(NullPointerException.class, () -> new RskAddress((byte[]) null));
    }

    @Test
    public void invalidNullAddressString() {
        Assertions.assertThrows(NullPointerException.class, () -> new RskAddress((String) null));
    }

    @Test
    public void invalidShortAddressBytes() {
        Assertions.assertThrows(RuntimeException.class, () -> new RskAddress(new byte[19]));
    }

    @Test
    public void invalidLongAddressBytes() {
        Assertions.assertThrows(RuntimeException.class, () -> new RskAddress(new byte[21]));
    }
}
