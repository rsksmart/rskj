package co.rsk.signing;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ECDSASignerFromDiskKey.class })
public class ECDSASignerFromDiskKeyTest {
    ECDSASignerFromDiskKey signer;

    @Before
    public void createSigner() {
        signer = new ECDSASignerFromDiskKey(new KeyId("an-id"), "a-file-path");
    }

    @Test
    public void canSignWith() {
        Assert.assertTrue(signer.canSignWith(new KeyId("an-id")));
        Assert.assertFalse(signer.canSignWith(new KeyId("another-id")));
    }

    @Test
    public void check() throws Exception {
        KeyFileChecker checkerMock = mock(KeyFileChecker.class);
        when(checkerMock.check()).thenReturn(Arrays.asList("message-1", "message-2"));
        PowerMockito.whenNew(KeyFileChecker.class).withArguments("a-file-path").thenReturn(checkerMock);

        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        Assert.assertFalse(checkResult.wasSuccessful());
        Assert.assertEquals(Arrays.asList("message-1", "message-2"), checkResult.getMessages());
    }

    @Test
    public void sign() throws Exception {
        KeyFileHandler handlerMock = mock(KeyFileHandler.class);
        when(handlerMock.privateKey()).thenReturn(Hex.decode("1122334455"));
        PowerMockito.whenNew(KeyFileHandler.class).withArguments("a-file-path").thenReturn(handlerMock);

        byte[] message = SHA3Helper.sha3("aabbccdd");

        ECDSASignature result = signer.sign(new KeyId("an-id"), new PlainMessage(message), null);

        ECKey.ECDSASignature expectedSignature = ECKey.fromPrivate(Hex.decode("1122334455")).sign(message);

        Assert.assertEquals(expectedSignature.r, result.getR());
        Assert.assertEquals(expectedSignature.s, result.getS());
    }

    @Test
    public void signNonMatchingKeyId() throws Exception {
        try {
            signer.sign(new KeyId("another-id"), new PlainMessage(Hex.decode("aabbcc")), null);
            Assert.fail();
        } catch (Exception e) {}
    }

}
