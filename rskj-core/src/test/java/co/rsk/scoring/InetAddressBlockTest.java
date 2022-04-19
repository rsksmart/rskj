package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 11/07/2017.
 */
// TODO when InetAddressBlock==subnet gets clarified, update this test-base accordingly, probably some tests will no longer be needed
public class InetAddressBlockTest {
    private static Random random = new Random();

    @Test
    public void recognizeIPV4AddressMask8Bits() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertTrue(mask.contains(address));
    }

    @Test
    public void containsIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 3);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertTrue(mask.contains(address2));
    }

    @Test
    public void doesNotContainIPV4WithAlteredByte() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void doesNotContainIPV6() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = generateIPAddressV6();

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void using16BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 16);

        Assert.assertTrue(mask.contains(address2));
    }

    @Test
    public void usingIPV4With9BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        byte[] bytes = address.getAddress();
        bytes[2] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[2] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV6With9BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        byte[] bytes = address.getAddress();
        bytes[14] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[14] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV4With18BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        byte[] bytes = address.getAddress();
        bytes[1] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[1] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV6With18BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        byte[] bytes = address.getAddress();
        bytes[13] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[13] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void doesNotContainIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        InetAddress address2 = generateIPAddressV4();

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void equals() throws UnknownHostException {
        InetAddress address1 = generateIPAddressV4();
        InetAddress address2 = alterByte(address1, 0);
        InetAddress address3 = generateIPAddressV6();

        InetAddressBlock block1 = new InetAddressBlock(address1, 8);
        InetAddressBlock block2 = new InetAddressBlock(address2, 9);
        InetAddressBlock block3 = new InetAddressBlock(address1, 1);
        InetAddressBlock block4 = new InetAddressBlock(address1, 8);
        InetAddressBlock block5 = new InetAddressBlock(address3, 8);

        Assert.assertTrue(block1.equals(block1));
        Assert.assertTrue(block2.equals(block2));
        Assert.assertTrue(block3.equals(block3));
        Assert.assertTrue(block4.equals(block4));
        Assert.assertTrue(block5.equals(block5));

        Assert.assertTrue(block1.equals(block4));
        Assert.assertTrue(block4.equals(block1));

        Assert.assertFalse(block1.equals(block2));
        Assert.assertFalse(block1.equals(block3));
        Assert.assertFalse(block1.equals(block5));

        Assert.assertFalse(block1.equals(null));
        Assert.assertFalse(block1.equals("block"));

        Assert.assertEquals(block1.hashCode(), block4.hashCode());
    }

    private static InetAddress generateIPAddressV4() throws UnknownHostException {
        byte[] bytes = new byte[4];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress generateIPAddressV6() throws UnknownHostException {
        byte[] bytes = new byte[16];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress alterByte(InetAddress address, int nbyte) throws UnknownHostException {
        byte[] bytes = address.getAddress();

        bytes[nbyte]++;

        return InetAddress.getByAddress(bytes);
    }

    @Test
    public void subnetWithCidr8IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("191.255.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.0.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.122.122.122");
        InetAddress lastAddressIn = InetAddress.getByName("192.255.255.255");
        InetAddress nextAddressOut = InetAddress.getByName("193.0.0.1");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 8);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr16IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("192.167.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.168.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.168.122.122");
        InetAddress lastAddressIn = InetAddress.getByName("192.168.255.255");
        InetAddress nextAddressOut = InetAddress.getByName("192.169.0.1");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 16);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr24IPV4() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("192.167.255.255");
        InetAddress firstAddressIn = InetAddress.getByName("192.168.0.1");
        InetAddress middleAddressIn = InetAddress.getByName("192.168.0.100");
        InetAddress lastAddressIn = InetAddress.getByName("192.168.0.255");
        InetAddress nextAddressOut = InetAddress.getByName("192.168.1.1");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 24);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr32IPV4() throws UnknownHostException {
        InetAddress uniqueAddress = InetAddress.getByName("192.168.0.47");
        InetAddress nextAddressOut = InetAddress.getByName("192.168.0.48");
        InetAddress previousAddressOut = InetAddress.getByName("192.168.1.47");

        InetAddressBlock mask = new InetAddressBlock(uniqueAddress, 32);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(uniqueAddress));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr8IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress firstAddressIn = InetAddress.getByName("2000:0000:0000:0000:0000:0000:0000:0000");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("20ff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress nextAddressOut = InetAddress.getByName("2100:0000:0000:0000:0000:0000:0000:0000");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 8);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr16IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2000:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0000:0000:0000:0000:0000:0000:0000");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress nextAddressOut = InetAddress.getByName("2002:0000:0000:0000:0000:0000:0000:0000");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 16);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr24IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0cff:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0d00:0000:0000:0000:0000:0000:0000");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0dff:ffff:ffff:ffff:ffff:ffff:ffff");

        InetAddress nextAddressOut = InetAddress.getByName("2001:0e00:0000:0000:0000:0000:0000:0000");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 24);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr32IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0db7:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0db8:0000:0000:0000:0000:0000:0000");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0db8:ffff:ffff:ffff:ffff:ffff:ffff");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db9:0000:0000:0000:0000:0000:0000");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 32);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr64IPV6() throws UnknownHostException {
        InetAddress previousAddressOut = InetAddress.getByName("2001:0db8:85a2:ffff:ffff:ffff:ffff:ffff");
        InetAddress firstAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:0000:0000:0000");
        InetAddress middleAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");
        InetAddress lastAddressIn = InetAddress.getByName("2001:0db8:85a3:0000:ffff:ffff:ffff:ffff");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db8:85a3:0001:0000:0000:0000:0000");

        InetAddressBlock mask = new InetAddressBlock(firstAddressIn, 64);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(firstAddressIn));
        Assert.assertTrue(mask.subnetContains(middleAddressIn));
        Assert.assertTrue(mask.subnetContains(lastAddressIn));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }

    @Test
    public void subnetWithCidr128IPV6() throws UnknownHostException {
        InetAddress uniqueAddress = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

        InetAddress previousAddressOut = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7333");
        InetAddress nextAddressOut = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7335");

        InetAddressBlock mask = new InetAddressBlock(uniqueAddress, 128);

        Assert.assertFalse(mask.subnetContains(previousAddressOut));
        Assert.assertTrue(mask.subnetContains(uniqueAddress));
        Assert.assertFalse(mask.subnetContains(nextAddressOut));
    }
}
