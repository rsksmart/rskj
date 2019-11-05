package org.ethereum.crypto.altbn128.rust;

import org.ethereum.crypto.altbn128.Field;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Created by mraof on 2019 October 29 at 4:23 PM.
 */
public class Fp2 implements Field<Fp2> {
    public static final Fp2 ZERO;
    public static final Fp2 ONE;

    static {
        System.loadLibrary("rskj_bn128");
        ZERO = new Fp2(BigInteger.ZERO, BigInteger.ZERO);
        ONE = new Fp2(BigInteger.ONE, BigInteger.ZERO);
    }

    final long a0;
    final long a1;
    final long a2;
    final long a3;
    final long b0;
    final long b1;
    final long b2;
    final long b3;

    private final boolean valid;

    public Fp2(BigInteger a, BigInteger b) {
        byte[] aBytes = a.toByteArray();
        long[] aRet = new long[4];
        byte[] bBytes = b.toByteArray();
        long[] bRet = new long[4];
        this.valid = Fp.newFq(aBytes, aRet) && Fp.newFq(bBytes, bRet);
        this.a0 = aRet[0];
        this.a1 = aRet[1];
        this.a2 = aRet[2];
        this.a3 = aRet[3];
        this.b0 = bRet[0];
        this.b1 = bRet[1];
        this.b2 = bRet[2];
        this.b3 = bRet[3];
    }

    Fp2(long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3) {
        this.valid = true;
        this.a0 = a0;
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
        this.b0 = b0;
        this.b1 = b1;
        this.b2 = b2;
        this.b3 = b3;
    }

    private Fp2(long[] fq2) {
        this.valid = true;
        this.a0 = fq2[0];
        this.a1 = fq2[1];
        this.a2 = fq2[2];
        this.a3 = fq2[3];
        this.b0 = fq2[4];
        this.b1 = fq2[5];
        this.b2 = fq2[6];
        this.b3 = fq2[7];
    }

    private static native void nadd(
            long la0,
            long la1,
            long la2,
            long la3,
            long lb0,
            long lb1,
            long lb2,
            long lb3,
            long ra0,
            long ra1,
            long ra2,
            long ra3,
            long rb0,
            long rb1,
            long rb2,
            long rb3,
            long[] ret);

    private static native void nmul(
            long la0,
            long la1,
            long la2,
            long la3,
            long lb0,
            long lb1,
            long lb2,
            long lb3,
            long ra0,
            long ra1,
            long ra2,
            long ra3,
            long rb0,
            long rb1,
            long rb2,
            long rb3,
            long[] ret);

    private static native void nsub(
            long la0,
            long la1,
            long la2,
            long la3,
            long lb0,
            long lb1,
            long lb2,
            long lb3,
            long ra0,
            long ra1,
            long ra2,
            long ra3,
            long rb0,
            long rb1,
            long rb2,
            long rb3,
            long[] ret);

    private static native void nsquared(
            long a0,
            long a1,
            long a2,
            long a3,
            long b0,
            long b1,
            long b2,
            long b3,
            long[] ret);

    private static native void ndbl(long a0, long a1, long a2, long a3, long b0, long b1, long b2, long b3, long[] ret);

    private static native void ninverse(
            long a0,
            long a1,
            long a2,
            long a3,
            long b0,
            long b1,
            long b2,
            long b3,
            long[] ret);

    private static native void nnegate(
            long a0,
            long a1,
            long a2,
            long a3,
            long b0,
            long b1,
            long b2,
            long b3,
            long[] ret);

    public Fp a() {
        return new Fp(a0, a1, a2, a3);
    }

    public Fp b() {
        return new Fp(b0, b1, b2, b3);
    }

    @Override
    public Fp2 add(Fp2 o) {
        long[] ret = new long[8];
        nadd(
                this.a0,
                this.a1,
                this.a2,
                this.a3,
                this.b0,
                this.b1,
                this.b2,
                this.b3,
                o.a0,
                o.a1,
                o.a2,
                o.a3,
                o.b0,
                o.b1,
                o.b2,
                o.b3,
                ret
        );
        return new Fp2(ret);
    }

    @Override
    public Fp2 mul(Fp2 o) {
        long[] ret = new long[8];
        nmul(
                this.a0,
                this.a1,
                this.a2,
                this.a3,
                this.b0,
                this.b1,
                this.b2,
                this.b3,
                o.a0,
                o.a1,
                o.a2,
                o.a3,
                o.b0,
                o.b1,
                o.b2,
                o.b3,
                ret
        );
        return new Fp2(ret);
    }

    @Override
    public Fp2 sub(Fp2 o) {
        long[] ret = new long[8];
        nsub(
                this.a0,
                this.a1,
                this.a2,
                this.a3,
                this.b0,
                this.b1,
                this.b2,
                this.b3,
                o.a0,
                o.a1,
                o.a2,
                o.a3,
                o.b0,
                o.b1,
                o.b2,
                o.b3,
                ret
        );
        return new Fp2(ret);
    }

    @Override
    public Fp2 squared() {
        long[] ret = new long[8];
        nsquared(this.a0, this.a1, this.a2, this.a3, this.b0, this.b1, this.b2, this.b3, ret);
        return new Fp2(ret);
    }

    @Override
    public Fp2 dbl() {
        long[] ret = new long[8];
        ndbl(this.a0, this.a1, this.a2, this.a3, this.b0, this.b1, this.b2, this.b3, ret);
        return new Fp2(ret);
    }

    @Override
    public Fp2 inverse() {
        long[] ret = new long[8];
        ninverse(this.a0, this.a1, this.a2, this.a3, this.b0, this.b1, this.b2, this.b3, ret);
        return new Fp2(ret);
    }

    @Override
    public Fp2 negate() {
        long[] ret = new long[8];
        nnegate(this.a0, this.a1, this.a2, this.a3, this.b0, this.b1, this.b2, this.b3, ret);
        return new Fp2(ret);
    }

    @Override
    public boolean isZero() {
        return this.a0 == ZERO.a0 &&
                this.a1 == ZERO.a1 &&
                this.a2 == ZERO.a2 &&
                this.a3 == ZERO.a3 &&
                this.b0 == ZERO.b0 &&
                this.b1 == ZERO.b1 &&
                this.b2 == ZERO.b2 &&
                this.b3 == ZERO.b3;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Fp2 fp2 = (Fp2) o;
        return a0 == fp2.a0 &&
                a1 == fp2.a1 &&
                a2 == fp2.a2 &&
                a3 == fp2.a3 &&
                b0 == fp2.b0 &&
                b1 == fp2.b1 &&
                b2 == fp2.b2 &&
                b3 == fp2.b3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a0, a1, a2, a3, b0, b1, b2, b3);
    }
}
