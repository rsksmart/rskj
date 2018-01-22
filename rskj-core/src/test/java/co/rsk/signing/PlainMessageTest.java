package co.rsk.signing;

import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

public class PlainMessageTest {
    @Test
    public void equality() {
        Message m1 = new PlainMessage(Hex.decode("aabb"));
        Message m2 = new PlainMessage(Hex.decode("aabb"));
        Message m3 = new PlainMessage(Hex.decode("aabbcc"));

        Assert.assertEquals(m1, m2);
        Assert.assertNotEquals(m1, m3);
        Assert.assertNotEquals(m2, m3);
    }

    @Test
    public void getBytes() {
        byte[] bytes = Hex.decode("aabb");
        Message m = new PlainMessage(bytes);

        Assert.assertTrue(Arrays.equals(bytes, m.getBytes()));
        Assert.assertNotSame(bytes, m.getBytes());
    }
}
