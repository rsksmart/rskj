/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.BIUtil;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Implementation of SignatureService with Bouncy Castle.
 */
class Secp256k1ServiceBC implements Secp256k1Service {

    private static final Logger logger = LoggerFactory.getLogger(Secp256k1ServiceBC.class);
    /**
     * The parameters of the secp256k1 curve that Ethereum uses.
     */
    public static final ECDomainParameters CURVE;
    public static final BigInteger HALF_CURVE_ORDER;
    // All clients must agree on the curve to use by agreement. Ethereum uses secp256k1.
    private static final X9ECParameters X_9_EC_PARAMETERS = SECNamedCurves.getByName("secp256k1");

    static {
        CURVE = new ECDomainParameters(X_9_EC_PARAMETERS.getCurve(), X_9_EC_PARAMETERS.getG(), X_9_EC_PARAMETERS.getN(), X_9_EC_PARAMETERS.getH());
        HALF_CURVE_ORDER = X_9_EC_PARAMETERS.getN().shiftRight(1);
    }

    /**
     * Part of the Singleton Signature service.
     * {@link Secp256k1#getInstance()}
     */
    Secp256k1ServiceBC() {
    }

    @Nullable
    @Override
    public ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed) {
        check(recId >= 0, "recId must be positive");
        check(recId <= 3, "recId must be less than or equal to 3");
        check(sig.getR().signum() >= 0, "r must be positive");
        check(sig.getS().signum() >= 0, "s must be positive");
        check(messageHash != null, "messageHash must not be null");
        // 1.0 For j from 0 to h   (h == recId here and the loop is outside this function)
        //   1.1 Let x = r + jn
        BigInteger n = CURVE.getN();  // Curve order.
        BigInteger i = BigInteger.valueOf((long) recId / 2);
        BigInteger x = sig.getR().add(i.multiply(n));
        //   1.2. Convert the integer x to an octet string X of length mlen using the conversion routine
        //        specified in Section 2.3.7, where mlen = ⌈(log2 p)/8⌉ or mlen = ⌈m/8⌉.
        //   1.3. Convert the octet string (16 set binary digits)||X to an elliptic curve point R using the
        //        conversion routine specified in Section 2.3.4. If this conversion routine outputs “invalid”, then
        //        do another iteration of Step 1.
        //
        // More concisely, what these points mean is to use X as a compressed public key.
        ECCurve.Fp curve = (ECCurve.Fp) CURVE.getCurve();
        BigInteger prime = curve.getQ();  // Bouncy Castle is not consistent about the letter it uses for the prime.
        if (x.compareTo(prime) >= 0) {
            // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
            return null;
        }
        // Compressed keys require you to know an extra bit of data about the y-coord as there are two possibilities.
        // So it's encoded in the recId.
        ECPoint r = decompressKey(x, (recId & 1) == 1);
        //   1.4. If nR != point at infinity, then do another iteration of Step 1 (callers responsibility).
        if (!r.multiply(n).isInfinity()) {
            return null;
        }
        //   1.5. Compute e from M using Steps 2 and 3 of ECDSA signature verification.
        BigInteger e = new BigInteger(1, messageHash);
        //   1.6. For k from 1 to 2 do the following.   (loop is outside this function via iterating recId)
        //   1.6.1. Compute a candidate public key as:
        //               Q = mi(r) * (sR - eG)
        //
        // Where mi(x) is the modular multiplicative inverse. We transform this into the following:
        //               Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
        // Where -e is the modular additive inverse of e, that is z such that z + e = 0 (mod n). In the above equation
        // ** is point multiplication and + is point addition (the EC group operator).
        //
        // We can find the additive inverse by subtracting e from zero then taking the mod. For example the additive
        // inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and -3 mod 11 = 8.
        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
        BigInteger rInv = sig.getR().modInverse(n);
        BigInteger srInv = rInv.multiply(sig.getS()).mod(n);
        BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
        ECPoint.Fp q = (ECPoint.Fp) ECAlgorithms.sumOfTwoMultiplies(CURVE.getG(), eInvrInv, r, srInv);
        return ECKey.fromPublicOnly(q.getEncoded(compressed));
    }

    @Override
    public boolean verify(byte[] data, ECDSASignature signature, byte[] pub) {
        ECDSASigner signer = new ECDSASigner();
        ECPublicKeyParameters params = new ECPublicKeyParameters(CURVE.getCurve().decodePoint(pub), CURVE);
        signer.init(false, params);
        try {
            return signer.verifySignature(data, signature.getR(), signature.getS());
        } catch (NullPointerException npe) {
            // Bouncy Castle contains a bug that can cause NPEs given specially crafted signatures.
            // Those signatures are inherently invalid/attack sigs so we just fail them here rather than crash the thread.
            logger.error("Caught NPE inside bouncy castle", npe);
            return false;
        }
    }

    private static byte[] encodeRes(byte[] w1, byte[] w2) {
        var res = new byte[64];

        var w1Updated = ByteUtil.stripLeadingZeroes(w1);
        var w2Updated = ByteUtil.stripLeadingZeroes(w2);

        System.arraycopy(w1Updated, 0, res, 32 - w1Updated.length, w1Updated.length);
        System.arraycopy(w2Updated, 0, res, 64 - w2Updated.length, w2Updated.length);

        return res;
    }

    private static byte[] getOutput(ECPoint res) {
        final var normalizedRes = res.normalize(); // allow affine coordinates

        if (normalizedRes.isInfinity()) {
            return new byte[64];
        }

        return encodeRes(normalizedRes.getAffineXCoord().getEncoded(), normalizedRes.getAffineYCoord().getEncoded());
    }

    private static boolean isValidPoint(byte[] x, byte[] y) {
        try {
            X_9_EC_PARAMETERS.getCurve().validatePoint(BIUtil.toBI(x), BIUtil.toBI(y));
        } catch (java.lang.IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    public static ECPoint getPoint(byte[] x1, byte[] y1) {
        ECPoint p1;

        if (ByteUtil.isAllZeroes(x1) && (ByteUtil.isAllZeroes(y1))) {
            p1 = X_9_EC_PARAMETERS.getCurve().getInfinity();
        } else {
            if (!isValidPoint(x1, y1)) {
                return null;
            }

            p1 = X_9_EC_PARAMETERS.getCurve().createPoint(BIUtil.toBI(x1), BIUtil.toBI(y1));
        }

        return p1;
    }

    @Override
    public byte[] add(byte[] data) {
        final var x1 = ByteUtil.parseWord(data, 0);
        final var y1 = ByteUtil.parseWord(data, 1);

        final var x2 = ByteUtil.parseWord(data, 2);
        final var y2 = ByteUtil.parseWord(data, 3);
        final var p1 = getPoint(x1, y1);

        if (p1 == null) {
            return null;
        }

        final var p2 = getPoint(x2, y2);

        if (p2 == null) {
            return null;
        }
        final var res = p1.add(p2);

        return getOutput(res);
    }

    @Override
    public byte[] mul(byte[] data) {
        byte[] x = ByteUtil.parseWord(data, 0);
        byte[] y = ByteUtil.parseWord(data, 1);

        byte[] s = ByteUtil.parseWord(data, 2);


        ECPoint p = getPoint(x, y);

        if (p == null) {
            return null;
        }

        ECPoint res = p.multiply(BIUtil.toBI(s));
        res = res.normalize();

        return getOutput(res);
    }

    /**
     * Decompress a compressed public key (x co-ord and low-bit of y-coord).
     *
     * @param xBN  -
     * @param yBit -
     * @return -
     */
    private ECPoint decompressKey(BigInteger xBN, boolean yBit) {
        X9IntegerConverter x9 = new X9IntegerConverter();
        byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
        compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
        return CURVE.getCurve().decodePoint(compEnc);
    }

}
