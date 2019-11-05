package org.ethereum.crypto.altbn128.rust;

import java.math.BigInteger;

/**
 * Created by mraof on 2019 October 29 at 9:59 PM.
 */
public class BN128G2 extends BN128<Fp2> {
    // the point at infinity
    static public final BN128<Fp2> ZERO = new BN128G2(Fp2.ZERO, Fp2.ZERO, Fp2.ZERO);

    static {
        System.loadLibrary("rskj_bn128");
    }

    public BN128G2(Fp2 x, Fp2 y, Fp2 z) {
        super(x, y, z);
    }

    private BN128G2(long[] data) {
        super(
                new Fp2(data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]),
                new Fp2(data[8], data[9], data[10], data[11], data[12], data[13], data[14], data[15]),
                new Fp2(data[16], data[17], data[18], data[19], data[20], data[21], data[22], data[23])
        );
    }

    private static native void affine(
            long xa0,
            long xa1,
            long xa2,
            long xa3,
            long xb0,
            long xb1,
            long xb2,
            long xb3,
            long ya0,
            long ya1,
            long ya2,
            long ya3,
            long yb0,
            long yb1,
            long yb2,
            long yb3,
            long za0,
            long za1,
            long za2,
            long za3,
            long zb0,
            long zb1,
            long zb2,
            long zb3,
            long[] ret);

    private static native void nadd(
            long lxa0,
            long lxa1,
            long lxa2,
            long lxa3,
            long lxb0,
            long lxb1,
            long lxb2,
            long lxb3,
            long lya0,
            long lya1,
            long lya2,
            long lya3,
            long lyb0,
            long lyb1,
            long lyb2,
            long lyb3,
            long lza0,
            long lza1,
            long lza2,
            long lza3,
            long lzb0,
            long lzb1,
            long lzb2,
            long lzb3,
            long rxa0,
            long rxa1,
            long rxa2,
            long rxa3,
            long rxb0,
            long rxb1,
            long rxb2,
            long rxb3,
            long rya0,
            long rya1,
            long rya2,
            long rya3,
            long ryb0,
            long ryb1,
            long ryb2,
            long ryb3,
            long rza0,
            long rza1,
            long rza2,
            long rza3,
            long rzb0,
            long rzb1,
            long rzb2,
            long rzb3,
            long[] ret);

    private static native void nmul(
            long lxa0,
            long lxa1,
            long lxa2,
            long lxa3,
            long lxb0,
            long lxb1,
            long lxb2,
            long lxb3,
            long lya0,
            long lya1,
            long lya2,
            long lya3,
            long lyb0,
            long lyb1,
            long lyb2,
            long lyb3,
            long lza0,
            long lza1,
            long lza2,
            long lza3,
            long lzb0,
            long lzb1,
            long lzb2,
            long lzb3,
            byte[] right,
            long[] ret);

    private static native boolean onCurve(
            long xa0,
            long xa1,
            long xa2,
            long xa3,
            long xb0,
            long xb1,
            long xb2,
            long xb3,
            long ya0,
            long ya1,
            long ya2,
            long ya3,
            long yb0,
            long yb1,
            long yb2,
            long yb3
    );

    public static BN128G2 create(byte[] xaBytes, byte[] xbBytes, byte[] yaBytes, byte[] ybBytes) {
        long[] xaRet = new long[4];
        long[] xbRet = new long[4];
        long[] yaRet = new long[4];
        long[] ybRet = new long[4];
        boolean valid = Fp.newFq(xaBytes, xaRet);
        valid &= Fp.newFq(xbBytes, xbRet);
        valid &= Fp.newFq(yaBytes, yaRet);
        valid &= Fp.newFq(ybBytes, ybRet);
        BN128G2 p = new BN128G2(
                new Fp2(xaRet[0], xaRet[1], xaRet[2], xaRet[3], xbRet[0], xbRet[1], xbRet[2], xbRet[3]),
                new Fp2(yaRet[0], yaRet[1], yaRet[2], yaRet[3], ybRet[0], ybRet[1], ybRet[2], ybRet[3]),
                Fp2.ONE
        );
        if (valid && p.isOnCurve()) {
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
    protected BN128<Fp2> zero() {
        return ZERO;
    }

    @Override
    protected BN128<Fp2> instance(Fp2 x, Fp2 y, Fp2 z) {
        return new BN128G2(x, y, z);
    }

    @Override
    protected Fp2 b() {
        return null;
    }

    @Override
    protected Fp2 one() {
        return Fp2.ONE;
    }

    @Override
    public BN128<Fp2> toAffine() {
        long[] ret = new long[16];
        affine(
                this.x.a0,
                this.x.a1,
                this.x.a2,
                this.x.a3,
                this.x.b0,
                this.x.b1,
                this.x.b2,
                this.x.b3,
                this.y.a0,
                this.y.a1,
                this.y.a2,
                this.y.a3,
                this.y.b0,
                this.y.b1,
                this.y.b2,
                this.y.b3,
                this.z.a0,
                this.z.a1,
                this.z.a2,
                this.z.a3,
                this.z.b0,
                this.z.b1,
                this.z.b2,
                this.z.b3,
                ret
        );
        Fp2 x = new Fp2(ret[0], ret[1], ret[2], ret[3], ret[4], ret[5], ret[6], ret[7]);
        Fp2 y = new Fp2(ret[8], ret[9], ret[10], ret[11], ret[12], ret[13], ret[14], ret[15]);

        return new BN128G2(x, y, one());
    }

    @Override
    public BN128<Fp2> add(BN128<Fp2> o) {
        long[] ret = new long[24];
        nadd(
                this.x.a0,
                this.x.a1,
                this.x.a2,
                this.x.a3,
                this.x.b0,
                this.x.b1,
                this.x.b2,
                this.x.b3,
                this.y.a0,
                this.y.a1,
                this.y.a2,
                this.y.a3,
                this.y.b0,
                this.y.b1,
                this.y.b2,
                this.y.b3,
                this.z.a0,
                this.z.a1,
                this.z.a2,
                this.z.a3,
                this.z.b0,
                this.z.b1,
                this.z.b2,
                this.z.b3,
                o.x.a0,
                o.x.a1,
                o.x.a2,
                o.x.a3,
                o.x.b0,
                o.x.b1,
                o.x.b2,
                o.x.b3,
                o.y.a0,
                o.y.a1,
                o.y.a2,
                o.y.a3,
                o.y.b0,
                o.y.b1,
                o.y.b2,
                o.y.b3,
                o.z.a0,
                o.z.a1,
                o.z.a2,
                o.z.a3,
                o.z.b0,
                o.z.b1,
                o.z.b2,
                o.z.b3,
                ret
        );
        return new BN128G2(ret);
    }

    @Override
    public BN128<Fp2> mul(BigInteger s) {
        long[] ret = new long[24];
        byte[] bytes = s.toByteArray();
        nmul(
                this.x.a0,
                this.x.a1,
                this.x.a2,
                this.x.a3,
                this.x.b0,
                this.x.b1,
                this.x.b2,
                this.x.b3,
                this.y.a0,
                this.y.a1,
                this.y.a2,
                this.y.a3,
                this.y.b0,
                this.y.b1,
                this.y.b2,
                this.y.b3,
                this.z.a0,
                this.z.a1,
                this.z.a2,
                this.z.a3,
                this.z.b0,
                this.z.b1,
                this.z.b2,
                this.z.b3,
                bytes,
                ret
        );
        return new BN128G2(ret);
    }

    @Override
    protected boolean isOnCurve() {
        return onCurve(
                this.x.a0,
                this.x.a1,
                this.x.a2,
                this.x.a3,
                this.x.b0,
                this.x.b1,
                this.x.b2,
                this.x.b3,
                this.y.a0,
                this.y.a1,
                this.y.a2,
                this.y.a3,
                this.y.b0,
                this.y.b1,
                this.y.b2,
                this.y.b3
        );
    }
}
