/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.scoring;

import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class InetAddressCidrBlockTest {

    @Test
    public void equals() throws UnknownHostException {
        InetAddress address1 = InetAddress.getByName("192.168.0.47");
        InetAddress address2 = InetAddress.getByName("198.168.0.47");
        InetAddress address3 = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");

        InetAddressCidrBlock block1 = new InetAddressCidrBlock(address1, 8);
        InetAddressCidrBlock block2 = new InetAddressCidrBlock(address2, 9);
        InetAddressCidrBlock block3 = new InetAddressCidrBlock(address1, 1);
        InetAddressCidrBlock block4 = new InetAddressCidrBlock(address1, 8);
        InetAddressCidrBlock block5 = new InetAddressCidrBlock(address3, 8);

        Assertions.assertEquals(block1, block1);
        Assertions.assertEquals(block2, block2);
        Assertions.assertEquals(block3, block3);
        Assertions.assertEquals(block4, block4);
        Assertions.assertEquals(block5, block5);

        Assertions.assertEquals(block1, block4);
        Assertions.assertEquals(block4, block1);

        Assertions.assertNotEquals(block1, block2);
        Assertions.assertNotEquals(block1, block3);
        Assertions.assertNotEquals(block1, block5);

        Assertions.assertNotEquals(null, block1);

        Assertions.assertEquals(block1.hashCode(), block4.hashCode());
    }

    @Test
    public void subnetWithCidr0IPV4() throws UnknownHostException {
        InetAddress firstAddress = InetAddress.getByName("0.0.0.0");
        InetAddress anyAddress = InetAddress.getByName("192.168.1.127");
        InetAddress lastAddress = InetAddress.getByName("255.255.255.255");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(anyAddress, 0);

        Assertions.assertTrue(mask.contains(firstAddress));
        Assertions.assertTrue(mask.contains(anyAddress));
        Assertions.assertTrue(mask.contains(lastAddress));
    }

    @Test
    public void subnetWithCidr8IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("191.255.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.0.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.122.122.122");
        InetAddress lastAddressIn = InetAddress.getByName("192.255.255.255");
        InetAddress nextAddressOut = InetAddress.getByName("193.0.0.1");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 8);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr16IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("192.167.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.168.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.168.122.122");
        InetAddress lastAddressIn = InetAddress.getByName("192.168.255.255");
        InetAddress nextAddressOut = InetAddress.getByName("192.169.0.1");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 16);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr24IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("192.167.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.168.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.168.0.100");
        InetAddress lastAddressIn = InetAddress.getByName("192.168.0.255");
        InetAddress nextAddressOut = InetAddress.getByName("192.168.1.1");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 24);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr32IPV4() throws UnknownHostException {
        InetAddress uniqueAddress = InetAddress.getByName("192.168.0.47");
        InetAddress nextAddressOut = InetAddress.getByName("192.168.0.48");
        InetAddress previousAddressOut = InetAddress.getByName("192.168.1.47");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(uniqueAddress, 32);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(uniqueAddress));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void invalidCidrForIpv4() throws UnknownHostException {
        InetAddress anyAddress = InetAddress.getByName("192.168.1.127");
        TestUtils.assertThrows(IllegalArgumentException.class, () -> new InetAddressCidrBlock(anyAddress, -1));
        TestUtils.assertThrows(IllegalArgumentException.class, () -> new InetAddressCidrBlock(anyAddress, 33));
    }

    @Test
    public void subnetWithCidr0IPV6() throws UnknownHostException {
        InetAddress firstAddress = InetAddress.getByName("0:0:0:0:0:0:0:0");
        InetAddress anyAddress = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7333");
        InetAddress lastAddress = InetAddress.getByName("f:f:f:f:f:f:f:f");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(anyAddress, 0);

        Assertions.assertTrue(mask.contains(firstAddress));
        Assertions.assertTrue(mask.contains(anyAddress));
        Assertions.assertTrue(mask.contains(lastAddress));
    }

    @Test
    public void subnetWithCidr8IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("1fff:f:f:f:f:f:f:f");
        InetAddress firstAddressIn = InetAddress.getByName("2000:0:0:0:0:0:0:0");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("20ff:f:f:f:f:f:f:f");
        InetAddress nextAddressOut = InetAddress.getByName("2100:0:0:0:0:0:0:0");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 8);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr16IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2000:f:f:f:f:f:f:f");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0:0:0:0:0:0:0");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:f:f:f:f:f:f:f");
        InetAddress nextAddressOut = InetAddress.getByName("2002:0:0:0:0:0:0:0");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 16);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr24IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0cff:f:f:f:f:f:f");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0d00:0:0:0:0:0:0");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0dff:f:f:f:f:f:f");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0e00:0:0:0:0:0:0");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 24);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr32IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0db7:f:f:f:f:f:f");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0db8:0:0:0:0:0:0");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0db8:f:f:f:f:f:f");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db9:0:0:0:0:0:0");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 32);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr64IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0db8:85a2:f:f:f:f:f");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:0:0:0");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0db8:85a3:0:f:f:f:f");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db8:85a3:0001:0:0:0:0");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(firstAddressIn, 64);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(firstAddressIn));
        Assertions.assertTrue(mask.contains(middleAddressIn));
        Assertions.assertTrue(mask.contains(lastAddressIn));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr128IPV6() throws UnknownHostException {
        InetAddress uniqueAddress = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7334");

        InetAddress previousAddressOut = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7333");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7335");

        InetAddressCidrBlock mask = new InetAddressCidrBlock(uniqueAddress, 128);

        Assertions.assertFalse(mask.contains(previousAddressOut));
        Assertions.assertTrue(mask.contains(uniqueAddress));
        Assertions.assertFalse(mask.contains(nextAddressOut));
    }

    @Test
    public void invalidCidrForIpv6() throws UnknownHostException {
        InetAddress anyAddress = InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7333");
        TestUtils.assertThrows(IllegalArgumentException.class, () -> new InetAddressCidrBlock(anyAddress, -1));
        TestUtils.assertThrows(IllegalArgumentException.class, () -> new InetAddressCidrBlock(anyAddress, 129));
    }
}
