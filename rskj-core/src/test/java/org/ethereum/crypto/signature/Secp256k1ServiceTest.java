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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SignatureException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@Ignore
public abstract class Secp256k1ServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(Secp256k1ServiceTest.class);

    private final String pubString = "0497466f2b32bc3bb76d4741ae51cd1d8578b48d3f1e68da206d47321aec267ce78549b514e4453d74ef11b0cd5e4e4c364effddac8b51bcfc8de80682f952896f";
    private final byte[] pubKey = Hex.decode(pubString);

    private final String privString = "3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4";
    private final BigInteger privateKey = new BigInteger(Hex.decode(privString));

    private final String exampleMessage = "This is an example of a signed message.";

    // Signature components
    private final BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
    private final BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
    private final byte v = 28;

    private Secp256k1Service secp256k1;

    protected Secp256k1ServiceTest(Secp256k1Service secp256k1) {
        this.secp256k1 = secp256k1;
    }

    protected Secp256k1Service getSecp256k1() {
        return this.secp256k1;
    }

    @Test
    public void test() {
        String messageHash = "f7cf90057f86838e5efd677f4741003ab90910e4e2736ff4d7999519d162d1ed";
        BigInteger r = new BigInteger("28824799845160661199077176548860063813328724131408018686643359460017962873020");
        BigInteger s = new BigInteger("48456094880180616145578324187715054843822774625773874469802229460318542735739");
        ECDSASignature signature = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray());
        ECKey expected = ECKey.fromPrivate(new BigInteger("0"));
        String pub = "00";
        ECKey k = this.getSecp256k1().recoverFromSignature((byte) 0, signature, Hex.decode(messageHash), false);
        if (k == null || !expected.equalsPub(k)) {
            fail();
        }

    }

    @Test
    public void testVerify_from_signatureToKey() {
        BigInteger r = new BigInteger("c52c114d4f5a3ba904a9b3036e5e118fe0dbb987fe3955da20f2cd8f6c21ab9c", 16);
        BigInteger s = new BigInteger("6ba4c2874299a55ad947dbc98a25ee895aabf6b625c26c435e84bfd70edf2f69", 16);
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 0x1b);
        byte[] rawtx = Hex.decode("f82804881bc16d674ec8000094cd2a3d9f938e13cd947ec05abc7fe734df8dd8268609184e72a0006480");
        try {
            ECKey key = this.getSecp256k1().signatureToKey(HashUtil.keccak256(rawtx), sig);
            logger.debug("Signature public key\t: {}", ByteUtil.toHexString(key.getPubKey()));
            logger.debug("Sender is\t\t: {}", ByteUtil.toHexString(key.getAddress()));
            assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(key.getAddress()));
            assertTrue(this.getSecp256k1().verify(HashUtil.keccak256(rawtx), sig, key.getPubKey()));
        } catch (SignatureException e) {
            fail();
        }
    }

    @Test
    public void testVerify_fixed_values() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
        BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
        byte v = (byte) 28;
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v);
        assertTrue(this.getSecp256k1().verify(HashUtil.keccak256(exampleMessage.getBytes()), sig, key.getPubKey()));
    }

    @Test
    public void testVerify_after_doSign() {
        ECKey key = ECKey.fromPrivate(privateKey);
        String message = "This is an example of a signed message.";
        byte[] messageBytes = HashUtil.keccak256(message.getBytes());
        ECDSASignature output = ECDSASignature.fromSignature(key.doSign(messageBytes));
        assertTrue(this.getSecp256k1().verify(messageBytes, output, key.getPubKey()));
    }

    @Test
    public void testSignatureToKey_invalid_params() {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        try {
            byte v = (byte) 35;
            this.getSecp256k1().signatureToKey(messageHash, ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v));
            fail();
        } catch (SignatureException e) {
            assertThat(e.getMessage(), containsString("Header byte out of range"));
        }
        try {
            byte v = (byte) 26;
            this.getSecp256k1().signatureToKey(messageHash, ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v));
            fail();
        } catch (SignatureException e) {
            assertThat(e.getMessage(), containsString("Header byte out of range"));
        }
    }

    @Test
    public void testSignatureToKey_from_Tx() throws SignatureException {

        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] pk = this.privateKey.toByteArray();
        String receiver = "CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826";
        ECKey fromPrivate = ECKey.fromPrivate(pk);
        ECKey fromPrivateDecompress = fromPrivate.decompress();
        String pubKeyExpected = Hex.toHexString(fromPrivateDecompress.getPubKey());
        String addressExpected = Hex.toHexString(fromPrivateDecompress.getAddress());

        // Create tx and sign, then recover from serialized.
        Transaction newTx = new Transaction(2l, 2l, 2l, receiver, 2l, messageHash, (byte) 0);

        logger.debug("1");
        newTx.sign(pk);
        logger.debug("1.1");
        ImmutableTransaction recoveredTx = new ImmutableTransaction(newTx.getEncoded());

        logger.debug("2");
        // Recover Pub Key from recovered tx
        ECKey actualKey = this.getSecp256k1().signatureToKey(HashUtil.keccak256(recoveredTx.getEncodedRaw()), recoveredTx.getSignature());
        logger.debug("3");

        // Recover PK and Address.

        String pubKeyActual = Hex.toHexString(actualKey.getPubKey());
        logger.debug("Signature public key\t: {}", pubKeyActual);
        assertEquals(pubKeyExpected, pubKeyActual);
        assertEquals(pubString, pubKeyActual);
        assertArrayEquals(pubKey, actualKey.getPubKey());

        String addressActual = Hex.toHexString(actualKey.getAddress());
        logger.debug("Sender is\t\t: {}", addressActual);
        assertEquals(addressExpected, addressActual);
    }

    @Test
    public void testSignatureToKey_fixed_values() throws SignatureException {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] s = ByteUtil.bigIntegerToBytes(this.s, 32);
        byte[] r = ByteUtil.bigIntegerToBytes(this.r, 32);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
        ECKey key = this.getSecp256k1().signatureToKey(messageHash, signature);
        assertNotNull(key);
        assertArrayEquals(pubKey, key.getPubKey());
    }

    @Test
    public void testRecoverFromSignature_invalid_params() {

        BigInteger validBigInt = BigInteger.valueOf(0l);
        byte[] validBytes = validBigInt.toByteArray();
        BigInteger invalidBigInt = BigInteger.valueOf(-1l);
        byte[] invalidNullBytes = null;
        boolean validBoolean = false;
        int validRecId = 0;
        int invalidRecId = -1;
        try {
            this.getSecp256k1().recoverFromSignature(invalidRecId, ECDSASignature.fromComponents(validBytes, validBytes), validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){assertThat(e.getMessage(), containsString("recId must be positive"));}

        try {
            this.getSecp256k1().recoverFromSignature(validRecId, new ECDSASignature(invalidBigInt, validBigInt), validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){assertThat(e.getMessage(), containsString("r must be positive"));}

        try {
            this.getSecp256k1().recoverFromSignature(validRecId, new ECDSASignature(validBigInt, invalidBigInt), validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){assertThat(e.getMessage(), containsString("s must be positive"));}

        try {
            this.getSecp256k1().recoverFromSignature(validRecId, new ECDSASignature(validBigInt, validBigInt), invalidNullBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){assertThat(e.getMessage(), containsString("messageHash must not be null"));}

    }

    @Test
    public void testRecoverFromSignature_without_V() {
        ECKey key = new ECKey();
        String message = "Hello World!";
        byte[] hash = HashUtil.sha256(message.getBytes());
        ECDSASignature sig = ECDSASignature.fromSignature(key.doSign(hash));
        key = ECKey.fromPublicOnly(key.getPubKeyPoint());
        boolean found = false;
        for (int i = 0; i < 4; i++) {
            ECKey key2 = this.getSecp256k1().recoverFromSignature(i, sig, hash, true);
            checkNotNull(key2);
            if (key.equals(key2)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

}
