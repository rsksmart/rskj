package org.ethereum.crypto.altbn128.rust;

import org.ethereum.crypto.altbn128.Field;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Created by mraof on 2019 October 29 at 4:23 PM.
 */
public class Fp implements Field<Fp> {
    static final Fp ZERO;
    static final Fp ONE;

    static {
        System.loadLibrary("rskj_bn128");
        ZERO = new Fp(BigInteger.ZERO);
        ONE = new Fp(BigInteger.ONE);
    }

    final long a;
    final long b;
    final long c;
    final long d;

    private final boolean valid;

    public Fp(BigInteger n) {
        byte[] bytes = n.toByteArray();
        long[] ret = new long[4];
        this.valid = newFq(bytes, ret);
        this.a = ret[0];
        this.b = ret[1];
        this.c = ret[2];
        this.d = ret[3];
    }

    Fp(long[] fq) {
        this.valid = true;
        this.a = fq[0];
        this.b = fq[1];
        this.c = fq[2];
        this.d = fq[3];
    }

    Fp(long a, long b, long c, long d) {
        this.valid = true;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    static native boolean newFq(byte[] bytes, long[] ret);

    private static native void nadd(long la, long lb, long lc, long ld, long ra, long rb, long rc, long rd, long[] ret);

    private static native void nmul(long la, long lb, long lc, long ld, long ra, long rb, long rc, long rd, long[] ret);

    private static native void nsub(long la, long lb, long lc, long ld, long ra, long rb, long rc, long rd, long[] ret);

    private static native void nsquared(long a, long b, long c, long d, long[] ret);

    private static native void ndbl(long a, long b, long c, long d, long[] ret);

    private static native void ninverse(long a, long b, long c, long d, long[] ret);

    private static native void nnegate(long a, long b, long c, long d, long[] ret);

    private static native void nbytes(long a, long b, long c, long d, byte[] ret);

    @Override
    public Fp add(Fp o) {
        long[] ret = new long[4];
        nadd(this.a, this.b, this.c, this.d, o.a, o.b, o.c, o.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp mul(Fp o) {
        long[] ret = new long[4];
        nmul(this.a, this.b, this.c, this.d, o.a, o.b, o.c, o.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp sub(Fp o) {
        long[] ret = new long[4];
        nsub(this.a, this.b, this.c, this.d, o.a, o.b, o.c, o.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp squared() {
        long[] ret = new long[4];
        nsquared(this.a, this.b, this.c, this.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp dbl() {
        long[] ret = new long[4];
        ndbl(this.a, this.b, this.c, this.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp inverse() {
        long[] ret = new long[4];
        ninverse(this.a, this.b, this.c, this.d, ret);
        return new Fp(ret);
    }

    @Override
    public Fp negate() {
        long[] ret = new long[4];
        nnegate(this.a, this.b, this.c, this.d, ret);
        return new Fp(ret);
    }

    @Override
    public boolean isZero() {
        return this.a == ZERO.a &&
                this.b == ZERO.b &&
                this.c == ZERO.c &&
                this.d == ZERO.d;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public byte[] bytes() {
        byte[] ret = new byte[32];
        nbytes(this.a, this.b, this.c, this.d, ret);
        BigInteger n = new BigInteger(ret);
        return n.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Fp fp = (Fp) o;
        return a == fp.a &&
                b == fp.b &&
                c == fp.c &&
                d == fp.d;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d);
    }
}
