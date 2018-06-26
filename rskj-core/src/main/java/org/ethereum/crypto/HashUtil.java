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

import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.util.RLP;
import org.ethereum.util.Utils;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static java.util.Arrays.copyOfRange;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class HashUtil {
    public static final byte[] EMPTY_TRIE_HASH = keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY));

    private static final MessageDigest sha256digest;

    static {
        try {
            sha256digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }

    /**
     * @param input - data for hashing
     * @return - sha256 hash of the data
     */
    public static byte[] sha256(byte[] input) {
        return sha256digest.digest(input);
    }

    public static byte[] keccak256(byte[] input) {
        Keccak256 digest =  new Keccak256();
        digest.update(input);
        return digest.digest();
    }

    /**
     * hashing chunk of the data
     * @param input - data for hash
     * @param start - start of hashing chunk
     * @param length - length of hashing chunk
     * @return - sha3 hash of the chunk
     */
    public static byte[] keccak256(byte[] input, int start, int length) {
        return Keccak256Helper.keccak256(input, start, length);
    }


    /**
     * @param data - message to hash
     * @return - reipmd160 hash of the message
     */
    public static byte[] ripemd160(byte[] data) {
        Digest digest = new RIPEMD160Digest();
        if (data != null) {
            byte[] resBuf = new byte[digest.getDigestSize()];
            digest.update(data, 0, data.length);
            digest.doFinal(resBuf, 0);
            return resBuf;
        }
        throw new NullPointerException("Can't hash a NULL value");
    }


    /**
     * Calculates RIGTMOST160(SHA3(input)). This is used in address calculations.
     * *
     * @param input - data
     * @return - 20 right bytes of the hash sha3 of the data
     */
    public static byte[] sha3omit12(byte[] input) {
        byte[] hash = keccak256(input);
        return copyOfRange(hash, 12, hash.length);
    }

    /**
     * The way to calculate new address inside ethereum
     *
     * @param addr - creating addres
     * @param nonce - nonce of creating address
     * @return new address
     */
    public static byte[] calcNewAddr(byte[] addr, byte[] nonce) {

        byte[] encSender = RLP.encodeElement(addr);
        byte[] encNonce = RLP.encodeBigInteger(new BigInteger(1, nonce));

        return sha3omit12(RLP.encodeList(encSender, encNonce));
    }

    /**
     * @see #doubleDigest(byte[], int, int)
     *
     * @param input -
     * @return -
     */
    public static byte[] doubleDigest(byte[] input) {
        return doubleDigest(input, 0, input.length);
    }

    /**
     * Calculates the SHA-256 hash of the given byte range, and then hashes the resulting hash again. This is
     * standard procedure in Bitcoin. The resulting hash is in big endian form.
     *
     * @param input -
     * @param offset -
     * @param length -
     * @return -
     */
    public static byte[] doubleDigest(byte[] input, int offset, int length) {
        synchronized (sha256digest) {
            sha256digest.reset();
            sha256digest.update(input, offset, length);
            byte[] first = sha256digest.digest();
            return sha256digest.digest(first);
        }
    }

    /**
     * @return generates random peer id for the HelloMessage
     */
    public static byte[] randomPeerId() {

        byte[] peerIdBytes = new BigInteger(512, Utils.getRandom()).toByteArray();

        final String peerId;
        if (peerIdBytes.length > 64) {
            peerId = Hex.toHexString(peerIdBytes, 1, 64);
        } else {
            peerId = Hex.toHexString(peerIdBytes);
        }

        return Hex.decode(peerId);
    }

    /**
     * @return - generate random 32 byte hash
     */
    public static byte[] randomHash(){

        byte[] randomHash = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomHash);
        return randomHash;
    }

    @Nonnull
    public static String shortHash(@Nonnull final byte[] hash){
        return Hex.toHexString(hash).substring(0, Math.min(hash.length, 6));
    }
}
