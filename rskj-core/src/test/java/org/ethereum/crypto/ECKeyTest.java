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

package org.ethereum.crypto;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ECKeyTest {
    private static final Logger log = LoggerFactory.getLogger(ECKeyTest.class);

    private String privString = "3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4";
    private BigInteger privateKey = new BigInteger(Hex.decode(privString));

    private String pubString = "0497466f2b32bc3bb76d4741ae51cd1d8578b48d3f1e68da206d47321aec267ce78549b514e4453d74ef11b0cd5e4e4c364effddac8b51bcfc8de80682f952896f";
    private String compressedPubString = "0397466f2b32bc3bb76d4741ae51cd1d8578b48d3f1e68da206d47321aec267ce7";
    private byte[] pubKey = Hex.decode(pubString);
    private byte[] compressedPubKey = Hex.decode(compressedPubString);
    private String address = "8a40bfaa73256b60764c1bf40675a99083efb075";

    private String exampleMessage = "This is an example of a signed message.";

    // Signature components
    private final BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
    private final BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
    byte v = 28;

    @Test
    void testHashCode() {
        Assertions.assertEquals(1866897155, ECKey.fromPrivate(privateKey).hashCode());
    }

    @Test
    void testECKey() {
        ECKey key = new ECKey();
        assertTrue(key.isPubKeyCanonical());
        assertNotNull(key.getPubKey());
        assertNotNull(key.getPrivKeyBytes());
        log.debug(ByteUtil.toHexString(key.getPrivKeyBytes()) + " :Generated privkey");
        log.debug(ByteUtil.toHexString(key.getPubKey()) + " :Generated pubkey");
    }

    @Test
    void testFromPrivateKey() {
        ECKey key = ECKey.fromPrivate(privateKey).decompress();
        assertTrue(key.isPubKeyCanonical());
        assertTrue(key.hasPrivKey());
        assertArrayEquals(pubKey, key.getPubKey());
    }

    @Test
    void testPrivatePublicKeyBytesNoArg() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ECKey(null, null), "Expecting an IllegalArgumentException for using only null-parameters");
    }

    @Test
    void testIsPubKeyOnly() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        assertTrue(key.isPubKeyCanonical());
        assertTrue(key.isPubKeyOnly());
        assertArrayEquals(key.getPubKey(), pubKey);
    }


    @Test
    void testPublicKeyFromPrivate() {
        byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, false);
        assertArrayEquals(pubKey, pubFromPriv);
    }

    @Test
    void testPublicKeyFromPrivateCompressed() {
        byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, true);
        assertArrayEquals(compressedPubKey, pubFromPriv);
    }

    @Test
    void testGetAddress() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        assertArrayEquals(Hex.decode(address), key.getAddress());
    }

    @Test
    void testToString() {
        ECKey key = ECKey.fromPrivate(BigInteger.TEN); // An example private key.
        assertEquals("pub:04a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7", key.toString());
    }

    @Test
    void testEthereumSign() throws IOException {
        // TODO: Understand why key must be decompressed for this to work
        ECKey key = ECKey.fromPrivate(privateKey).decompress();
        System.out.println("Secret\t: " + ByteUtil.toHexString(key.getPrivKeyBytes()));
        System.out.println("Pubkey\t: " + ByteUtil.toHexString(key.getPubKey()));
        System.out.println("Data\t: " + exampleMessage);
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        ECDSASignature signature = ECDSASignature.fromSignature(key.sign(messageHash));

        assertEquals(r, signature.getR());
        assertEquals(s, signature.getS());
        assertEquals(v, signature.getV());
    }

    @Test
    void testSValue() throws Exception {
        // Check that we never generate an S value that is larger than half the curve order. This avoids a malleability
        // issue that can allow someone to change a transaction [hash] without invalidating the signature.
        final int ITERATIONS = 10;
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(ITERATIONS));
        List<ListenableFuture<ECDSASignature>> sigFutures = Lists.newArrayList();
        final ECKey key = new ECKey();
        for (byte i = 0; i < ITERATIONS; i++) {
            final byte[] hash = HashUtil.keccak256(new byte[]{i});
            sigFutures.add(executor.submit(new Callable<ECDSASignature>() {
                @Override
                public ECDSASignature call() throws Exception {
                    return ECDSASignature.fromSignature(key.doSign(hash));
                }
            }));
        }
        List<ECDSASignature> sigs = Futures.allAsList(sigFutures).get();
        for (ECDSASignature signature : sigs) {
            assertTrue(signature.getS().compareTo(ECKey.HALF_CURVE_ORDER) <= 0);
        }
        final ECDSASignature duplicate = new ECDSASignature(sigs.get(0).getR(), sigs.get(0).getS());
        assertEquals(sigs.get(0), duplicate);
        assertEquals(sigs.get(0).hashCode(), duplicate.hashCode());
    }

    @Test
    void testIsPubKeyCanonicalCorect() {
        // Test correct prefix 4, right length 65
        byte[] canonicalPubkey1 = new byte[65];
        canonicalPubkey1[0] = 0x04;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey1));
        // Test correct prefix 2, right length 33
        byte[] canonicalPubkey2 = new byte[33];
        canonicalPubkey2[0] = 0x02;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey2));
        // Test correct prefix 3, right length 33
        byte[] canonicalPubkey3 = new byte[33];
        canonicalPubkey3[0] = 0x03;
        assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey3));
    }

    @Test
    void testIsPubKeyCanonicalWrongLength() {
        // Test correct prefix 4, but wrong length !65
        byte[] nonCanonicalPubkey1 = new byte[64];
        nonCanonicalPubkey1[0] = 0x04;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey1));
        // Test correct prefix 2, but wrong length !33
        byte[] nonCanonicalPubkey2 = new byte[32];
        nonCanonicalPubkey2[0] = 0x02;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey2));
        // Test correct prefix 3, but wrong length !33
        byte[] nonCanonicalPubkey3 = new byte[32];
        nonCanonicalPubkey3[0] = 0x03;
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey3));
    }

    @Test
    void testIsPubKeyCanonicalWrongPrefix() {
        // Test wrong prefix 4, right length 65
        byte[] nonCanonicalPubkey4 = new byte[65];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey4));
        // Test wrong prefix 2, right length 33
        byte[] nonCanonicalPubkey5 = new byte[33];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey5));
        // Test wrong prefix 3, right length 33
        byte[] nonCanonicalPubkey6 = new byte[33];
        assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey6));
    }

    @Test
    void testGetPrivKeyBytes() {
        ECKey key = new ECKey();
        assertNotNull(key.getPrivKeyBytes());
        assertEquals(32, key.getPrivKeyBytes().length);
    }

    @Test
    void testEqualsObject() {
        ECKey key0 = new ECKey();
        ECKey key1 = ECKey.fromPrivate(privateKey);
        ECKey key2 = ECKey.fromPrivate(privateKey);

        assertNotEquals(key0, key1);
        assertEquals(key1, key1);
        assertEquals(key1, key2);
    }

    @Test
    void decryptAECSIC() {
        ECKey key = ECKey.fromPrivate(Hex.decode("abb51256c1324a1350598653f46aa3ad693ac3cf5d05f36eba3f495a1f51590f"));
        byte[] payload = key.decryptAES(Hex.decode("84a727bc81fa4b13947dc9728b88fd08"));
        Assertions.assertDoesNotThrow(() -> ByteUtil.toHexString(payload));
    }

}
