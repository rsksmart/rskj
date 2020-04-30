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
import static org.junit.Assert.*;
import static org.junit.Assert.fail;

public class SignatureServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignatureServiceTest.class);

    private String pubString = "0497466f2b32bc3bb76d4741ae51cd1d8578b48d3f1e68da206d47321aec267ce78549b514e4453d74ef11b0cd5e4e4c364effddac8b51bcfc8de80682f952896f";
    private byte[] pubKey = Hex.decode(pubString);

    private String privString = "3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4";
    private BigInteger privateKey = new BigInteger(Hex.decode(privString));

    private String exampleMessage = "This is an example of a signed message.";

    // Signature components
    private final BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
    private final BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
    byte v = 28;

    SignatureService signatureService = SignatureService.getInstance();

    @Test
    public void testVerifySignature() {
        BigInteger r = new BigInteger("c52c114d4f5a3ba904a9b3036e5e118fe0dbb987fe3955da20f2cd8f6c21ab9c", 16);
        BigInteger s = new BigInteger("6ba4c2874299a55ad947dbc98a25ee895aabf6b625c26c435e84bfd70edf2f69", 16);
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 0x1b);
        byte[] rawtx = Hex.decode("f82804881bc16d674ec8000094cd2a3d9f938e13cd947ec05abc7fe734df8dd8268609184e72a0006480");
        try {
            ECKey key = this.getSignatureService().signatureToKey(HashUtil.keccak256(rawtx), sig);
            LOGGER.debug("Signature public key\t: {}", Hex.toHexString(key.getPubKey()));
            LOGGER.debug("Sender is\t\t: {}", Hex.toHexString(key.getAddress()));
            assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", Hex.toHexString(key.getAddress()));
            this.getSignatureService().verify(HashUtil.keccak256(rawtx), sig, key.getPubKey());
        } catch (SignatureException e) {
            fail();
        }
    }

    @Test
    public void testVerifySignature1() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
        BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 28);
        this.getSignatureService().verify(HashUtil.keccak256(exampleMessage.getBytes()), sig, key.getPubKey());
    }

    @Test
    public void testVerifySignature2() {
        BigInteger r = new BigInteger("c52c114d4f5a3ba904a9b3036e5e118fe0dbb987fe3955da20f2cd8f6c21ab9c", 16);
        BigInteger s = new BigInteger("6ba4c2874299a55ad947dbc98a25ee895aabf6b625c26c435e84bfd70edf2f69", 16);
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 0x1b);
        byte[] rawtx = Hex.decode("f82804881bc16d674ec8000094cd2a3d9f938e13cd947ec05abc7fe734df8dd8268609184e72a0006480");
        try {
            ECKey key = this.getSignatureService().signatureToKey(HashUtil.keccak256(rawtx), sig);
            LOGGER.debug("Signature public key\t: {}", Hex.toHexString(key.getPubKey()));
            LOGGER.debug("Sender is\t\t: {}", Hex.toHexString(key.getAddress()));
            assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", Hex.toHexString(key.getAddress()));
            this.getSignatureService().verify(HashUtil.keccak256(rawtx), sig, key.getPubKey());
        } catch (SignatureException e) {
            fail();
        }
    }

    @Test
    @Ignore("The TX sender is not 20-byte long")
    public void testVerifySignature3() throws SignatureException {

        byte[] rawtx = Hex.decode("f86e80893635c9adc5dea000008609184e72a00082109f9479b08ad8787060333663d19704909ee7b1903e58801ba0899b92d0c76cbf18df24394996beef19c050baa9823b4a9828cd9b260c97112ea0c9e62eb4cf0a9d95ca35c8830afac567619d6b3ebee841a3c8be61d35acd8049");

        Transaction tx = new ImmutableTransaction(rawtx);
        ECKey key = this.getSignatureService().signatureToKey(HashUtil.keccak256(rawtx), tx.getSignature());

        LOGGER.debug("Signature public key\t: {}", Hex.toHexString(key.getPubKey()));
        LOGGER.debug("Sender is\t\t: {}", Hex.toHexString(key.getAddress()));

        //  sender: CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826
        // TODO: add test assertion when the sign/verify part actually works.
    }

    @Test
    public void testSignedMessageToKey() throws SignatureException {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());

        ECKey key = this.getSignatureService().signatureToKey(messageHash, ECDSASignature.fromComponents(ByteUtil.bigIntegerToBytes(r, 32),
                ByteUtil.bigIntegerToBytes(s, 32), v));

        assertNotNull(key);
        assertArrayEquals(pubKey, key.getPubKey());
    }

    @Test
    public void testSignVerify() {
        ECKey key = ECKey.fromPrivate(privateKey);
        String message = "This is an example of a signed message.";
        ECDSASignature output = key.doSign(message.getBytes());
        assertTrue(this.getSignatureService().verify(message.getBytes(), output, key.getPubKey()));
    }

    @Test
    public void keyRecovery() throws Exception {
        ECKey key = new ECKey();
        String message = "Hello World!";
        byte[] hash = HashUtil.sha256(message.getBytes());
        ECDSASignature sig = key.doSign(hash);
        key = ECKey.fromPublicOnly(key.getPubKeyPoint());
        boolean found = false;
        for (int i = 0; i < 4; i++) {
            ECKey key2 = this.getSignatureService().recoverFromSignature(i, sig, hash, true);
            checkNotNull(key2);
            if (key.equals(key2)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

}
