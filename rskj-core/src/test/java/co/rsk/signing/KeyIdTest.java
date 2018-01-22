package co.rsk.signing;

import org.junit.Assert;
import org.junit.Test;

public class KeyIdTest {
    @Test
    public void equality() {
        KeyId k1 = new KeyId("a-key-identifier");
        KeyId k2 = new KeyId("a-key-identifier");
        KeyId k3 = new KeyId("another-key-identifier");

        Assert.assertEquals(k1, k2);
        Assert.assertNotEquals(k1, k3);
        Assert.assertNotEquals(k2, k3);
    }
}
