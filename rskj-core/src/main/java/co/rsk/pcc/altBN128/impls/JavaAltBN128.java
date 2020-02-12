package co.rsk.pcc.altBN128.impls;

import co.rsk.crypto.altbn128java.*;
import org.ethereum.util.BIUtil;
import org.ethereum.vm.DataWord;

import static co.rsk.pcc.altBN128.BN128Pairing.PAIR_SIZE;
import static org.ethereum.util.ByteUtil.*;

public class JavaAltBN128 extends AbstractAltBN128 {

    private static byte[] encodeRes(byte[] w1, byte[] w2) {

        byte[] res = new byte[64];

        w1 = stripLeadingZeroes(w1);
        w2 = stripLeadingZeroes(w2);

        System.arraycopy(w1, 0, res, 32 - w1.length, w1.length);
        System.arraycopy(w2, 0, res, 64 - w2.length, w2.length);

        return res;
    }

    private BN128Pair decodePair(byte[] in, int offset) {

        byte[] x = parseWord(in, offset, 0);
        byte[] y = parseWord(in, offset, 1);

        BN128G1 p1 = BN128G1.create(x, y);

        // fail if point is invalid
        if (p1 == null) {
            return null;
        }

        // (b, a)
        byte[] b = parseWord(in, offset, 2);
        byte[] a = parseWord(in, offset, 3);

        // (d, c)
        byte[] d = parseWord(in, offset, 4);
        byte[] c = parseWord(in, offset, 5);

        BN128G2 p2 = BN128G2.create(a, b, c, d);

        // fail if point is invalid
        if (p2 == null) {
            return null;
        }

        return BN128Pair.of(p1, p2);
    }


    @Override
    public int add(byte[] data, int length) {
        output = new byte[64];

        byte[] x1 = parseWord(data, 0);
        byte[] y1 = parseWord(data, 1);

        byte[] x2 = parseWord(data, 2);
        byte[] y2 = parseWord(data, 3);

        BN128<Fp> p1 = BN128Fp.create(x1, y1);

        if (p1 == null) {
            output = EMPTY_BYTE_ARRAY;
            return 1;
        }

        BN128<Fp> p2 = BN128Fp.create(x2, y2);
        if (p2 == null) {
            output = EMPTY_BYTE_ARRAY;
            return 1;
        }

        BN128<Fp> res = p1.add(p2).toEthNotation();

        output = encodeRes(res.x().bytes(), res.y().bytes());
        return 1;
    }

    @Override
    public int mul(byte[] data, int length) {
        output = new byte[64];

        byte[] x = parseWord(data, 0);
        byte[] y = parseWord(data, 1);

        byte[] s = parseWord(data, 2);

        BN128<Fp> p = BN128Fp.create(x, y);

        if (p == null) {
            output = EMPTY_BYTE_ARRAY;
            return 1;
        }

        BN128<Fp> res = p.mul(BIUtil.toBI(s)).toEthNotation();

        output = encodeRes(res.x().bytes(), res.y().bytes());
        return 1;
    }

    @Override
    public int pairing(byte[] data, int length) {

        output = new byte[32];

        // fail if input len is not a multiple of PAIR_SIZE
        if (data.length % PAIR_SIZE > 0) {
            output = EMPTY_BYTE_ARRAY;
            return 1;
        }

        PairingCheck check = PairingCheck.create();

        // iterating over all pairs
        for (int offset = 0; offset < data.length; offset += PAIR_SIZE) {

            BN128Pair pair = decodePair(data, offset);

            // fail if decoding has failed
            if (pair == null) {
                output = EMPTY_BYTE_ARRAY;
                return 1;
            }

            check.addPair(pair.getG1(), pair.getG2());
        }

        check.run();
        int result = check.result();

        output = DataWord.valueOf(result).getData();
        return 1;
    }
}
