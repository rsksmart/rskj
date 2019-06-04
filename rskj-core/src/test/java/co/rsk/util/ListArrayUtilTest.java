package co.rsk.util;

import org.junit.Assert;
import org.junit.Test;

public class ListArrayUtilTest {


    @Test
    public void isBlank() {
        String address = "";
        Assert.assertTrue(address == null || ("".equals(address.trim()) || "".equals(address)) );
    }

    @Test
    public void isBlankWithSpaces() {
        String address = " ";
        Assert.assertTrue(address == null || (address.trim().equals("") || "".equals(address)) );
    }

    @Test
    public void isBlankWithNull() {
        String address = null;
        Assert.assertTrue(address == null || ("".equals(address.trim()) || "".equals(address)) );
    }

    @Test
    public void testNullToEmpty(){
        Assert.assertNotNull(ListArrayUtil.nullToEmpty(null));
    }

    @Test
    public void testGetLength(){
        Assert.assertEquals(0, ListArrayUtil.getLength(null));
    }

}
