package co.rsk.pcc.secp256k1.impls;

import co.rsk.crypto.altbn128java.*;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.util.BIUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

import static org.ethereum.util.ByteUtil.parseWord;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

public class JavaSecp256k1 extends AbstractSecp256k1 {

    X9ECParameters params = SECNamedCurves.getByName("secp256k1");
    ECDomainParameters ecParams = new ECDomainParameters(
            params.getCurve(), params.getG(), params.getN(), params.getH());

    private static byte[] encodeRes(byte[] w1, byte[] w2) {

        byte[] res = new byte[64];

        w1 = stripLeadingZeroes(w1);
        w2 = stripLeadingZeroes(w2);

        System.arraycopy(w1, 0, res, 32 - w1.length, w1.length);
        System.arraycopy(w2, 0, res, 64 - w2.length, w2.length);

        return res;
    }
    private static byte[] encodeInfinity() {

        byte[] res = new byte[64];
        return res;
    }

    boolean validPoint(byte[] x, byte[] y) {
        try {
            ECPoint vld = params.getCurve().validatePoint(
                    BIUtil.toBI(x),
                    BIUtil.toBI(y));
        } catch (java.lang.IllegalArgumentException e) {
            return false;
        }
        return true;
    }
    static public byte[] getNegate(byte[] x1,byte[]y1) {
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        ECDomainParameters ecParams = new ECDomainParameters(
                params.getCurve(), params.getG(), params.getN(), params.getH());
        ECPoint p1 = params.getCurve().createPoint(
                BIUtil.toBI( x1),
                BIUtil.toBI( y1));
        ECPoint neg =p1.negate().normalize();

        return neg.getAffineYCoord().getEncoded();
    }
    public static byte[] parseBytes(byte[] input, int offset, int len) {

        if (offset >= input.length || len == 0) {
            return new byte[len]; // return a byte array with len bytes, all zeros
        }

        byte[] bytes = new byte[len];
        System.arraycopy(input, offset, bytes, 0, Math.min(input.length - offset, len));
        return bytes;
    }

    /**
     * Parses 32-bytes word from given input.
     * Uses {@link #parseBytes(byte[], int, int)} method,
     * thus, result will be right-padded with zero bytes if there is not enough bytes in {@code input}
     *
     * @param idx an index of the word starting from {@code 0}
     */
    public static byte[] parseWord(byte[] input, int idx) {
        return parseBytes(input, 32 * idx, 32);
    }
    public static boolean isAllZeros(byte[] array) {
        for (byte b : array) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
    public ECPoint getPoint(byte[] x1,byte[] y1) {
        ECPoint p1;
        if (isAllZeros(x1) && (isAllZeros(y1))) {
            p1 = params.getCurve().getInfinity();
        } else {
            if (!validPoint(x1, y1)) {
                return null;
            }

            p1 = params.getCurve().createPoint(
                    BIUtil.toBI(x1),
                    BIUtil.toBI(y1));
        }
        return p1;
    }
    byte[] getOutput(ECPoint res) {
        byte[] o;
        res = res.normalize(); // allow affine coordinates
        if (res.isInfinity()) {
            o = encodeInfinity();
        } else
            o = encodeRes(res.getAffineXCoord().getEncoded(),
                    res.getAffineYCoord().getEncoded());
        return o;
    }

    @Override
    public int add(byte[] data, int length) {
        output = new byte[64];

        byte[] x1 = parseWord(data, 0);
        byte[] y1 = parseWord(data, 1);

        byte[] x2 = parseWord(data, 2);
        byte[] y2 = parseWord(data, 3);
        ECPoint p1 =getPoint(x1,y1);

        if (p1 == null) {
            return returnError();
        }
        ECPoint p2 = getPoint(x2, y2);
        if (p2 == null) {
            return returnError();
        }
        ECPoint res = p1.add(p2);
        output = getOutput(res);

        return 1;
    }

    @Override
    public int mul(byte[] data, int length) {
        output = new byte[64];

        byte[] x = parseWord(data, 0);
        byte[] y = parseWord(data, 1);

        byte[] s = parseWord(data, 2);


        ECPoint p = getPoint(x,y);

        if (p == null) {
            return returnError();
        }

        ECPoint res = p.multiply(BIUtil.toBI(s));
        res = res.normalize();
        output = getOutput(res);
        return 1;
    }


    static protected int returnError() {
        return -1;
    }
}
