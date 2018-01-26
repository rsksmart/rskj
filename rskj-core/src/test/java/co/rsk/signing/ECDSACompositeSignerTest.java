/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.signing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class ECDSACompositeSignerTest {
    ECDSASigner signer1, signer2;
    ECDSACompositeSigner signer;

    @Before
    public void createSigner() {
        signer = new ECDSACompositeSigner();

        signer1 = mock(ECDSASigner.class);
        signer2 = mock(ECDSASigner.class);
        signer.addSigner(signer1);
        signer.addSigner(signer2);
    }

    @Test
    public void canSignWithWhenNone() {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(false);
        Assert.assertFalse(signer.canSignWith(new KeyId("a-key")));
    }

    @Test
    public void canSignWithWhenOne() {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);
        when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(true);
        when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);

        Assert.assertTrue(signer.canSignWith(new KeyId("a-key")));
        Assert.assertTrue(signer.canSignWith(new KeyId("another-key")));
    }

    @Test
    public void check() throws Exception {
        when(signer1.check()).thenReturn(new ECDSASigner.ECDSASignerCheckResult(Arrays.asList("m1", "m2")));
        when(signer2.check()).thenReturn(new ECDSASigner.ECDSASignerCheckResult(Arrays.asList("m3", "m4")));
        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        Assert.assertFalse(checkResult.wasSuccessful());
        Assert.assertEquals(Arrays.asList("m1", "m2", "m3", "m4"), checkResult.getMessages());
    }

    @Test
    public void sign() throws Exception {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);
        SignAuthorization auth = mock(SignAuthorization.class);

        ECDSASignature mockedSignature = mock(ECDSASignature.class);
        when(signer2.sign(new KeyId("a-key"), new PlainMessage(Hex.decode("aabbccdd")), auth)).thenReturn(mockedSignature);

        ECDSASignature result = signer.sign(new KeyId("a-key"), new PlainMessage(Hex.decode("aabbccdd")), auth);

        verify(signer1, never()).sign(any(), any(), any());
        Assert.assertSame(mockedSignature, result);
    }

    @Test
    public void signNonMatchingKeyId() throws Exception {
        try {
            when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(false);
            when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);
            signer.sign(new KeyId("another-id"), new PlainMessage(Hex.decode("aabbcc")), mock(SignAuthorization.class));
            Assert.fail();
        } catch (Exception e) {}
    }

    @Test
    public void getPublicKey() throws Exception {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);
        SignAuthorization auth = mock(SignAuthorization.class);

        PublicKey mockedPublicKey = mock(PublicKey.class);
        when(signer2.getPublicKey(new KeyId("a-key"))).thenReturn(mockedPublicKey);

        PublicKey result = signer.getPublicKey(new KeyId("a-key"));

        verify(signer1, never()).getPublicKey(any());
        Assert.assertSame(mockedPublicKey, result);
    }

    @Test
    public void getPublicKeyNonMatchingKeyId() throws Exception {
        try {
            when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(false);
            when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);
            signer.getPublicKey(new KeyId("another-id"));
            Assert.fail();
        } catch (Exception e) {}
    }
}
