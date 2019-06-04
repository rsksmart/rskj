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

package co.rsk.crypto;

import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Base64;
import java.security.SignatureException;
import static org.junit.Assert.*;

public class ECKeyTest {
    private String exampleMessage = new String("This is an example of a signed message.");

    @Test
    public void fromComponentsWithRecoveryCalculation() {
        ECKey key = new ECKey();
        byte[] hash = HashUtil.randomHash();
        ECKey.ECDSASignature signature = key.sign(hash);

        // With uncompressed public key
        ECKey.ECDSASignature signatureWithCalculatedV = ECKey.ECDSASignature.fromComponentsWithRecoveryCalculation(
                signature.r.toByteArray(),
                signature.s.toByteArray(),
                hash,
                key.getPubKey(false)
        );

        Assert.assertEquals(signature.r, signatureWithCalculatedV.r);
        Assert.assertEquals(signature.s, signatureWithCalculatedV.s);
        Assert.assertEquals(signature.v, signatureWithCalculatedV.v);

        // With compressed public key
        signatureWithCalculatedV = ECKey.ECDSASignature.fromComponentsWithRecoveryCalculation(
                signature.r.toByteArray(),
                signature.s.toByteArray(),
                hash,
                key.getPubKey(true)
        );

        Assert.assertEquals(signature.r, signatureWithCalculatedV.r);
        Assert.assertEquals(signature.s, signatureWithCalculatedV.s);
        Assert.assertEquals(signature.v, signatureWithCalculatedV.v);
    }
}
