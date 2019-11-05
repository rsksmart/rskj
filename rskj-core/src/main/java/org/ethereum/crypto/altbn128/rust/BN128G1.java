package org.ethereum.crypto.altbn128.rust;

import java.math.BigInteger;

/**
 * Created by mraof on 2019 October 29 at 9:59 PM.
 */
public class BN128G1 extends BN128<Fp> {
    public static final Fp B_Fp = new Fp(BigInteger.valueOf(3));
    // the point at infinity
    static public final BN128<Fp> ZERO = new BN128G1(Fp.ZERO, Fp.ZERO, Fp.ZERO);

    static {
        System.loadLibrary("rskj_bn128");
    }

    protected BN128G1(Fp x, Fp y, Fp z) {
        super(x, y, z);
    }

    private BN128G1(long[] data) {
        super(
                new Fp(data[0], data[1], data[2], data[3]),
                new Fp(data[4], data[5], data[6], data[7]),
                new Fp(data[8], data[9], data[10], data[11])
        );
    }

    private static native void affine(
            long x0,
            long x1,
            long x2,
            long x3,
            long y0,
            long y1,
            long y2,
            long y3,
            long z0,
            long z1,
            long z2,
            long z3,
            long[] ret);

    private static native void nadd(
            long lx0,
            long lx1,
            long lx2,
            long lx3,
            long ly0,
            long ly1,
            long ly2,
            long ly3,
            long lz0,
            long lz1,
            long lz2,
            long lz3,
            long rx0,
            long rx1,
            long rx2,
            long rx3,
            long ry0,
            long ry1,
            long ry2,
            long ry3,
            long rz0,
            long rz1,
            long rz2,
            long rz3,
            long[] ret);

    private static native void nmul(
            long lx0,
            long lx1,
            long lx2,
            long lx3,
            long ly0,
            long ly1,
            long ly2,
            long ly3,
            long lz0,
            long lz1,
            long lz2,
            long lz3,
            byte[] right,
            long[] ret);

    private static native boolean onCurve(
            long lx0,
            long lx1,
            long lx2,
            long lx3,
            long ly0,
            long ly1,
            long ly2,
            long ly3);

    public static BN128G1 create(byte[] xBytes, byte[] yBytes) {
        long[] xRet = new long[4];
        long[] yRet = new long[4];
        Fp.newFq(xBytes, xRet);
        Fp.newFq(yBytes, yRet);
        BN128G1 p = new BN128G1(new Fp(xRet), new Fp(yRet), Fp.ONE);
        if (p.isValid()) {
            return p;
        } else {
            return null;
        }
    }

    /**
     * Point at infinity in Ethereum notation: should return (0; 0; 0),
     * {@link #isZero()} method called for that point, also, returns {@code true}
     */
    @Override
    protected BN128<Fp> zero() {
        return ZERO;
    }

    @Override
    protected BN128<Fp> instance(Fp x, Fp y, Fp z) {
        return new BN128G1(x, y, z);
    }

    @Override
    protected Fp b() {
        return B_Fp;
    }

    @Override
    protected Fp one() {
        return Fp.ONE;
    }

    @Override
    public BN128<Fp> toAffine() {
        long[] ret = new long[8];
        affine(x.a, x.b, x.c, x.d, y.a, y.b, y.c, y.d, z.a, z.b, z.c, z.d, ret);
        Fp x = new Fp(ret[0], ret[1], ret[2], ret[3]);
        Fp y = new Fp(ret[4], ret[5], ret[6], ret[7]);

        return new BN128G1(x, y, one());
    }

    @Override
    public BN128<Fp> add(BN128<Fp> o) {
        long[] ret = new long[12];
        nadd(
                this.x.a,
                this.x.b,
                this.x.c,
                this.x.d,
                this.y.a,
                this.y.b,
                this.y.c,
                this.y.d,
                this.z.a,
                this.z.b,
                this.z.c,
                this.z.d,
                o.x.a,
                o.x.b,
                o.x.c,
                o.x.d,
                o.y.a,
                o.y.b,
                o.y.c,
                o.y.d,
                o.z.a,
                o.z.b,
                o.z.c,
                o.z.d,
                ret
        );
        return new BN128G1(ret);
    }

    @Override
    public BN128<Fp> mul(BigInteger s) {
        long[] ret = new long[12];
        byte[] bytes = s.toByteArray();
        nmul(
                this.x.a,
                this.x.b,
                this.x.c,
                this.x.d,
                this.y.a,
                this.y.b,
                this.y.c,
                this.y.d,
                this.z.a,
                this.z.b,
                this.z.c,
                this.z.d,
                bytes,
                ret
        );
        return new BN128G1(ret);
    }

    @Override
    protected boolean isOnCurve() {
        return onCurve(this.x.a, this.x.b, this.x.c, this.x.d, this.y.a, this.y.b, this.y.c, this.y.d);
    }
}
