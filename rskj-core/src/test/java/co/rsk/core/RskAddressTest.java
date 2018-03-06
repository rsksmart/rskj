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
    public void nullAddress() {
        RskAddress senderA = new RskAddress("0000000000000000000000000000000000000000");
        RskAddress senderB = new RskAddress("0x0000000000000000000000000000000000000000");
        RskAddress senderC = new RskAddress(new byte[20]);
        RskAddress senderD = new RskAddress("0000000000000000000000000000000000000001");

        Assert.assertEquals(RskAddress.nullAddress(), senderA);
        Assert.assertEquals(RskAddress.nullAddress(), senderB);
        Assert.assertEquals(RskAddress.nullAddress(), senderC);
        Assert.assertNotEquals(RskAddress.nullAddress(), senderD);
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

    private void testOKAddress(String address, String prefix) {
        RskAddress rskAddress = new RskAddress(address);
        Assert.assertEquals(address, rskAddress.toString());
    }

    private void testErrAddress(String address, String prefix) {
        RskAddress rskAddress = new RskAddress(address);
        Assert.assertNotEquals(address, rskAddress.hex(prefix));
    }

    @Test
    public void testEthOk() {
        String prefix = RskAddress.DEFAULT_CHECKSUMADDRESS_PREFIX;
        testOKAddress("0x52908400098527886E0F7030069857D2E4169EE7", "");
        testOKAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", "");
        testOKAddress("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359", "");
        testOKAddress("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB", "");
        testOKAddress("0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb", "");
        testOKAddress("0x52908400098527886E0F7030069857d2E4169EE7", prefix);
        testOKAddress("0x5Aaeb6053F3e94c9B9A09F33669435E7Ef1beaEd", prefix);
        testOKAddress("0xFB6916095CA1dF60BB79Ce92CE3EA74C37c5d359", prefix);
        testOKAddress("0xdbF03b407c01e7Cd3cBEa99509D93f8DDDC8C6Fb", prefix);
        testOKAddress("0xd1220A0cF47c7b9be7a2E6ba89f429762E7B9ADB", prefix);
    }

    @Test
    public void testEthErr() {
        String prefix = RskAddress.DEFAULT_CHECKSUMADDRESS_PREFIX;
        testErrAddress("0xd1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb", "");
        testErrAddress("0xA000000000000000000000000000000000000000", "");
        testErrAddress("0x52908400098527886E0F7030069857d2E4169EE7", "");
        testErrAddress("0x52908400098527886E0F7030069857D2E4169EE7", prefix);
        testErrAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", prefix);
        testErrAddress("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359", prefix);
        testErrAddress("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB", prefix);
        testErrAddress("0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb", prefix);
    }
    
}
