/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.crypto.altbn128java;

import java.math.BigInteger;

import static co.rsk.crypto.altbn128java.Params.P;

/**
 * Arithmetic in F_p, p = 21888242871839275222246405745257275088696311157297823662689037894645226208583
 *
 * @author Mikhail Kalinin
 * @since 01.09.2017
 */
public class Fp implements Field<Fp> {

    static public final Fp ZERO = new Fp(BigInteger.ZERO);
    static public final Fp _1 = new Fp(BigInteger.ONE);
    static public final Fp NON_RESIDUE = new Fp(new BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208582"));

    static public final Fp _2_INV = new Fp(BigInteger.valueOf(2).modInverse(P));

    private BigInteger v;

    Fp(BigInteger v) { this.v = v; }

    @Override public Fp add(Fp o) { return new Fp(this.v.add(o.v).mod(P)); }
    @Override public Fp mul(Fp o) { return new Fp(this.v.multiply(o.v).mod(P)); }
    @Override public Fp sub(Fp o) { return new Fp(this.v.subtract(o.v).mod(P)); }
    @Override public Fp squared() { return new Fp(v.multiply(v).mod(P)); }
    @Override public Fp dbl() { return new Fp(v.add(v).mod(P)); }
    @Override public Fp inverse() { return new Fp(v.modInverse(P)); }
    @Override public Fp negate() { return new Fp(v.negate().mod(P)); }
    @Override public boolean isZero() { return v.compareTo(BigInteger.ZERO) == 0; }

    /**
     * Checks if provided value is a valid Fp member
     */
    @Override
    public boolean isValid() {
        return v.compareTo(P) < 0;
    }

    Fp2 mul(Fp2 o) { return new Fp2(o.a().mul(this), o.b().mul(this)); }

    static Fp create(byte[] v) {
        return new Fp(new BigInteger(1, v));
    }

    static Fp create(BigInteger v) {
        return new Fp(v);
    }

    public byte[] bytes() {
        return v.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}

        Fp fp = (Fp) o;

        return !(v != null ? v.compareTo(fp.v) != 0 : fp.v != null);
    }

    @Override
    public int hashCode() {
        return v.hashCode();
    }

    @Override
    public String toString() {
        return v.toString();
    }
}
