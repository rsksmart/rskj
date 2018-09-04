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

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

public class Keccak256Helper {

    public static final int DEFAULT_SIZE = 256;
    public static final int DEFAULT_SIZE_BYTES = DEFAULT_SIZE / 8;

    public static String keccak256String(String message) {
        return keccak256String(message, new KeccakDigest(DEFAULT_SIZE), true);
    }

    public static String keccak256String(byte[] message) {
        return keccak256String(message, new KeccakDigest(DEFAULT_SIZE), true);
    }

    public static byte[] keccak256(String message) {
        return keccak256(Hex.decode(message), new KeccakDigest(DEFAULT_SIZE), true);
    }

    public static byte[] keccak256(byte[] message) {
        return keccak256(message, new KeccakDigest(DEFAULT_SIZE), true);
    }

    public static byte[] keccak256(byte[] message, Size sz) {
        return keccak256(message, new KeccakDigest(sz.bits), true);
    }

    public static byte[] keccak256(byte[] m1, byte[] m2) {
        return keccak256(m1, m2, new KeccakDigest(DEFAULT_SIZE), true);
    }

    public static byte[] keccak256(byte[] message, int start, int length) {
        return keccak256(message, start, length, new KeccakDigest(DEFAULT_SIZE), true);
    }

    protected static String keccak256String(String message, Size bitSize) {
        KeccakDigest digest = new KeccakDigest(bitSize.bits);
        return keccak256String(message, digest, true);
    }

    protected static String keccak256String(byte[] message, Size bitSize) {
        KeccakDigest digest = new KeccakDigest(bitSize.bits);
        return keccak256String(message, digest, true);
    }

    protected static String keccak256String(String message, Size bitSize, boolean bouncyencoder) {
        KeccakDigest digest = new KeccakDigest(bitSize.bits);
        return keccak256String(message, digest, bouncyencoder);
    }

    protected static String keccak256String(byte[] message, Size bitSize, boolean bouncyencoder) {
        KeccakDigest digest = new KeccakDigest(bitSize.bits);
        return keccak256String(message, digest, bouncyencoder);
    }

    private static String keccak256String(String message, KeccakDigest digest, boolean bouncyencoder) {
        if (message != null) {
            return keccak256String(Hex.decode(message), digest, bouncyencoder);
        }
        throw new NullPointerException("Can't hash a NULL value");
    }

    private static String keccak256String(byte[] message, KeccakDigest digest, boolean bouncyencoder) {
        byte[] hash = doKeccak256(message, digest, bouncyencoder);
        if (bouncyencoder) {
            return Hex.toHexString(hash);
        } else {
            BigInteger bigInt = new BigInteger(1, hash);
            return bigInt.toString(16);
        }
    }

    private static byte[] keccak256(byte[] message, KeccakDigest digest, boolean bouncyencoder) {
        return doKeccak256(message, digest, bouncyencoder);
    }

    private static byte[] keccak256(byte[] m1, byte[] m2, KeccakDigest digest, boolean bouncyencoder) {
        return doKeccak256(m1, m2, digest, bouncyencoder);
    }

    private static byte[] keccak256(byte[] message, int start, int length, KeccakDigest digest, boolean bouncyencoder) {
        byte[] hash = new byte[digest.getDigestSize()];

        if (message.length != 0) {
            digest.update(message, start, length);
        }
        digest.doFinal(hash, 0);
        return hash;
    }


    private static byte[] doKeccak256(byte[] message, KeccakDigest digest, boolean bouncyencoder) {
        byte[] hash = new byte[digest.getDigestSize()];

        if (message.length != 0) {
            digest.update(message, 0, message.length);
        }
        digest.doFinal(hash, 0);
        return hash;
    }

    private static byte[] doKeccak256(byte[] m1, byte[] m2, KeccakDigest digest, boolean bouncyencoder) {
        byte[] hash = new byte[digest.getDigestSize()];
        digest.update(m1, 0, m1.length);
        digest.update(m2, 0, m2.length);

        digest.doFinal(hash, 0);
        return hash;
    }

    public enum Size {

        S224(224),
        S256(256),
        S384(384),
        S512(512);

        int bits = 0;

        Size(int bits) {
            this.bits = bits;
        }

        public int getValue() {
            return this.bits;
        }
    }

}
