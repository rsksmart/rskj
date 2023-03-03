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

import com.google.common.primitives.Bytes;
import org.ethereum.config.Constants;
import org.ethereum.crypto.ECKey;

import java.math.BigInteger;

import static org.ethereum.util.BIUtil.isLessThan;

/**
 * Groups the two components that make up a signature, and provides a way to encode to Base64 form, which is
 * how ECDSA signatures are represented when embedded in other data structures in the Ethereum protocol. The raw
 * components can be useful for doing further EC maths on them.
 */
public class ECDSASignature {
    /**
     * The two components of the signature.
     */
    private final BigInteger r;
    private final BigInteger s;
    private byte v;

    /**
     * Constructs a signature with the given components. Does NOT automatically canonicalise the signature.
     *
     * @param r -
     * @param s -
     */
    public ECDSASignature(BigInteger r, BigInteger s) {
        this.r = r;
        this.s = s;
    }

    /**
     *  Constructs a signature with the given components. Does NOT automatically canonicalise the signature.
     *
     * @param r
     * @param s
     * @param v
     */
    public ECDSASignature(BigInteger r, BigInteger s, byte v) {
        this.r = r;
        this.s = s;
        this.v = v;
    }

    /**
     * Warning: Used in Fed Node.
     *
     * @param r -
     * @param s -
     * @param hash - the hash used to compute this signature
     * @param pub - public key bytes, used to calculate the recovery byte 'v'
     * @return -
     */
    public static ECDSASignature fromComponentsWithRecoveryCalculation(byte[] r, byte[] s, byte[] hash, byte[] pub) {
        byte v = calculateRecoveryByte(r, s, hash, pub);
        return fromComponents(r, s, v);
    }

    private static byte calculateRecoveryByte(byte[] r, byte[] s, byte[] hash, byte[] pub) {
        ECDSASignature sig = ECDSASignature.fromComponents(r, s);
        ECKey pubKey = ECKey.fromPublicOnly(pub);

        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = Secp256k1.getInstance().recoverFromSignature(i, sig, hash, false);
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

    /**
     * Only for compatibility until we could finally remove old {@link org.ethereum.crypto.ECKey.ECDSASignature}.
     *
     * @param sign
     * @return
     */
    public static ECDSASignature fromSignature(ECKey.ECDSASignature sign) {
        return ECDSASignature.fromComponents(sign.r.toByteArray(), sign.s.toByteArray(), sign.v);
    }

    public BigInteger getR() {
        return r;
    }

    public BigInteger getS() {
        return s;
    }

    public byte getV() {
        return v;
    }

    public void setV(byte v) {
        this.v = v;
    }

    public byte[] getRaw() {
        return Bytes.concat(new byte[]{this.v}, this.getR().toByteArray(), this.getS().toByteArray());
    }

    /**
     * With no recovery byte "v"
     * @param r
     * @param s
     * @return -
     */
    public static ECDSASignature fromComponents(byte[] r, byte[] s) {
        return new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
    }

    /**
     *
     * @param r -
     * @param s -
     * @param v -
     * @return -
     */
    public static ECDSASignature fromComponents(byte[] r, byte[] s, byte v) {
        ECDSASignature signature = fromComponents(r, s);
        signature.v = v;
        return signature;
    }

    public boolean validateComponents() {
        return validateComponents(r, s, v);
    }

    public boolean validateComponentsWithoutV() {
        return validateComponents(r, s);
    }

    public static boolean validateComponents(BigInteger r, BigInteger s, byte v) {

        if (v != 27 && v != 28) {
            return false;
        }
        return validateComponents(r, s);
    }

    private static boolean validateComponents(BigInteger r, BigInteger s) {
        if (isLessThan(r, BigInteger.ONE)) {
            return false;
        }

        if (isLessThan(s, BigInteger.ONE)) {
            return false;
        }

        if (!isLessThan(r, Constants.getSECP256K1N())) {
            return false;
        }

        return isLessThan(s, Constants.getSECP256K1N());
    }

    /**
     * Will automatically adjust the S component to be less than or equal to half the curve order, if necessary.
     * This is required because for every signature (r,s) the signature (r, -s (mod N)) is a valid signature of
     * the same message. However, we dislike the ability to modify the bits of a Ethereum transaction after it's
     * been signed, as that violates various assumed invariants. Thus in future only one of those forms will be
     * considered legal and the other will be banned.
     *
     * @return  -
     */
    public ECDSASignature toCanonicalised() {
        if (s.compareTo(ECKey.HALF_CURVE_ORDER) > 0) {
            // The order of the curve is the number of valid points that exist on that curve. If S is in the upper
            // half of the number of valid points, then bring it back to the lower half. Otherwise, imagine that
            //    N = 10
            //    s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid solutions.
            //    10 - 8 == 2, giving us always the latter solution, which is canonical.
            return new ECDSASignature(r, ECKey.CURVE.getN().subtract(s));
        } else {
            return this;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ECDSASignature signature = (ECDSASignature) o;

        if (!r.equals(signature.r)) {
            return false;
        }

        return s.equals(signature.s);
    }

    @Override
    public int hashCode() {
        int result = r.hashCode();
        result = 31 * result + s.hashCode();
        return result;
    }
}
