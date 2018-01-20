package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by mario on 30/08/2016.
 */
public class KeyFileHandlerTest {
    private static final String TEST_KEY_FILE = "src/test/resources/peg/federate.test.key";
    private static final String PRIVATE_KEY = "b23e4ef8b06c31404d78e9910831284fe35dc8b451af006dc9e662ac3d7a2a0d";
    private static final String PUBLIC_KEY = "03ee45f3636cf14f2e4d2ef55dd8514938d340fb8217a2a791d6c1864a7c42d10d";

    @Test
    public void testConstructor() {
        KeyFileHandler keyFileHandler = new KeyFileHandler(TEST_KEY_FILE);
        Assert.assertNotNull(keyFileHandler);
    }

    @Test
    public void privateKeyFromFile() throws IOException {
        KeyFileHandler keyFileHandler = new KeyFileHandler(TEST_KEY_FILE);
        byte[] check = Hex.decode(PRIVATE_KEY);
        byte[] privateKey = keyFileHandler.privateKey();

        Assert.assertTrue(Arrays.equals(check, privateKey));

        Assert.assertTrue(StringUtils.equals(PUBLIC_KEY, Hex.toHexString(ECKey.fromPrivate(privateKey).getPubKey(true))));
    }

    @Test(expected = FileNotFoundException.class)
    public void noKeyDefaultValue() throws IOException {
        KeyFileHandler keyFileHandler = new KeyFileHandler("");

        keyFileHandler.privateKey();
    }

}
