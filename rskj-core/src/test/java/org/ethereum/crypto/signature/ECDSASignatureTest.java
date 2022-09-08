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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ECDSASignatureTest {

    private final String exampleMessage = "This is an example of a signed message.";
    private static final BigInteger SECP256K1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

    @Test
    public void testValidateComponents() {

        // Valid components.
        // valid v
        Assertions.assertTrue(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 27).validateComponents());
        Assertions.assertTrue(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 28).validateComponents());
        //valid r
        Assertions.assertTrue(new ECDSASignature(SECP256K1N.subtract(BigInteger.ONE), BigInteger.ONE, (byte) 28).validateComponents());
        //valid s
        Assertions.assertTrue(new ECDSASignature(BigInteger.ONE, SECP256K1N.subtract(BigInteger.ONE), (byte) 28).validateComponents());

        // Not Valid components.
        //invalid "r"
        Assertions.assertFalse(new ECDSASignature(BigInteger.ZERO, BigInteger.ONE, (byte) 27).validateComponents());
        Assertions.assertFalse(new ECDSASignature(SECP256K1N, BigInteger.ONE, (byte) 27).validateComponents());

        //invalid "s"
        Assertions.assertFalse(new ECDSASignature(BigInteger.ONE, BigInteger.ZERO, (byte) 27).validateComponents());
        Assertions.assertFalse(new ECDSASignature(BigInteger.ONE, SECP256K1N, (byte) 27).validateComponents());

        //invalid "v"
        Assertions.assertFalse(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 29).validateComponents());
        Assertions.assertFalse(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 26).validateComponents());
    }

    @Test
    public void testEquals() {
        ECDSASignature expected = new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 27);

        //same instance
        Assertions.assertEquals(expected, expected);

        //same values
        Assertions.assertEquals(expected, new ECDSASignature(expected.getR(), expected.getS(), expected.getV()));

        //same values - but diff v
        Assertions.assertEquals(expected, new ECDSASignature(expected.getR(), expected.getS(), (byte) 0));

        // null
        Assertions.assertNotEquals(expected, null);

        //dif classes
        Assertions.assertNotEquals(expected, BigInteger.ZERO);

        //diff r
        Assertions.assertNotEquals(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 27), new ECDSASignature(BigInteger.TEN, BigInteger.ONE, (byte) 27));

        //diff s
        Assertions.assertNotEquals(new ECDSASignature(BigInteger.ONE, BigInteger.ONE, (byte) 27), new ECDSASignature(BigInteger.ONE, BigInteger.TEN, (byte) 27));

    }

    @Test
    public void testValidateComponents_SignedMsg() {
        ECKey key = new ECKey();
        byte[] hash = HashUtil.keccak256(exampleMessage.getBytes());
        ECDSASignature signature = ECDSASignature.fromSignature(key.sign(hash));
        Assertions.assertTrue(signature.validateComponents());
    }

    @Test
    public void fromComponentsWithRecoveryCalculation() {
        ECKey key = new ECKey();
        byte[] hash = HashUtil.randomHash();
        ECDSASignature signature = ECDSASignature.fromSignature(key.sign(hash));

        // With uncompressed public key
        ECDSASignature signatureWithCalculatedV = ECDSASignature.fromComponentsWithRecoveryCalculation(
                signature.getR().toByteArray(),
                signature.getS().toByteArray(),
                hash,
                key.getPubKey(false)
        );

        Assertions.assertEquals(signature.getR(), signatureWithCalculatedV.getR());
        Assertions.assertEquals(signature.getS(), signatureWithCalculatedV.getS());
        Assertions.assertEquals(signature.getV(), signatureWithCalculatedV.getV());

        // With compressed public key
        signatureWithCalculatedV = ECDSASignature.fromComponentsWithRecoveryCalculation(
                signature.getR().toByteArray(),
                signature.getS().toByteArray(),
                hash,
                key.getPubKey(true)
        );

        Assertions.assertEquals(signature.getR(), signatureWithCalculatedV.getR());
        Assertions.assertEquals(signature.getS(), signatureWithCalculatedV.getS());
        Assertions.assertEquals(signature.getV(), signatureWithCalculatedV.getV());
    }
}
