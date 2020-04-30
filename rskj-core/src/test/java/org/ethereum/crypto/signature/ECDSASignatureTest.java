/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.crypto.signature;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

public class ECDSASignatureTest {

    @Test
    public void fromComponentsWithRecoveryCalculation() {
        ECKey key = new ECKey();
        byte[] hash = HashUtil.randomHash();
        ECDSASignature signature = key.sign(hash);

        // With uncompressed public key
        ECDSASignature signatureWithCalculatedV = fromComponentsWithRecoveryCalculation(
                signature.r.toByteArray(),
                signature.s.toByteArray(),
                hash,
                key.getPubKey(false)
        );

        Assert.assertEquals(signature.r, signatureWithCalculatedV.r);
        Assert.assertEquals(signature.s, signatureWithCalculatedV.s);
        Assert.assertEquals(signature.v, signatureWithCalculatedV.v);

        // With compressed public key
        signatureWithCalculatedV = fromComponentsWithRecoveryCalculation(
                signature.r.toByteArray(),
                signature.s.toByteArray(),
                hash,
                key.getPubKey(true)
        );

        Assert.assertEquals(signature.r, signatureWithCalculatedV.r);
        Assert.assertEquals(signature.s, signatureWithCalculatedV.s);
        Assert.assertEquals(signature.v, signatureWithCalculatedV.v);
    }


    /**
     *
     * @param r -
     * @param s -
     * @param hash - the hash used to compute this signature
     * @param pub - public key bytes, used to calculate the recovery byte 'v'
     * @return -
     */
    public static ECDSASignature fromComponentsWithRecoveryCalculation(byte[] r, byte[] s, byte[] hash, byte[] pub) {
        byte v = calculateRecoveryByte(r, s, hash, pub);
        return ECDSASignature.fromComponents(r, s, v);
    }

    private static byte calculateRecoveryByte(byte[] r, byte[] s, byte[] hash, byte[] pub) {
        ECDSASignature sig = ECDSASignature.fromComponents(r, s);
        ECKey pubKey = ECKey.fromPublicOnly(pub);

        // TODO: same logic in ECKey.sign
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = SignatureService.getInstance().recoverFromSignature(i, sig, hash, false);
            if (k != null && k.equalsPub(pubKey)) {
                recId = i;
                break;
            }
        }

        if (recId == -1) {
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        }

        return (byte) (recId + 27);
    }
}
