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

package org.ethereum.crypto.altbn128;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a Pairing Check operation over points of two twisted Barreto–Naehrig curves {@link BN128Fp}, {@link BN128Fp2}<br/>
 * <br/>
 * <p>
 * The Pairing itself is a transformation of the form G1 x G2 -> Gt, <br/>
 * where G1 and G2 are members of {@link BN128G1} {@link BN128G2} respectively, <br/>
 * Gt is a subgroup of roots of unity in Fp12 field, root degree equals to {@link Params#R} <br/>
 * <br/>
 * <p>
 * Pairing Check input is a sequence of point pairs, the result is either 1 or 0, 1 is considered as success, 0 as fail <br/>
 * <br/>
 * <p>
 * Usage:
 * <ul>
 *      <li>add pairs sequentially with {@link #addPair(BN128G1, BN128G2)}</li>
 *      <li>run check with {@link #run()} after all paris have been added</li>
 *      <li>get result with {@link #result()}</li>
 * </ul>
 * <p>
 * Arithmetic has been ported from <a href="https://github.com/scipr-lab/libff/blob/master/libff/algebra/curves/alt_bn128/alt_bn128_pairing.cpp">libff</a>
 * Ate pairing algorithms
 *
 * @author Mikhail Kalinin
 * @since 01.09.2017
 */
public class PairingCheck {
    private static final BigInteger LOOP_COUNT = new BigInteger("29793968203157093288");

    static {
        System.loadLibrary("rskj_bn128");
    }

    private List<BN128Pair> pairs = new ArrayList<>();
    private boolean one = true;

    private PairingCheck() {
    }

    public static PairingCheck create() {
        return new PairingCheck();
    }

    public void addPair(BN128G1 g1, BN128G2 g2) {
        pairs.add(BN128Pair.of(g1, g2));
    }

    public void run() {
        long[] pairData = new long[pairs.size() * 36];
        int o = 0;
        for (BN128Pair pair : pairs) {
            pairData[o] = pair.g1.x.a;
            pairData[o + 1] = pair.g1.x.b;
            pairData[o + 2] = pair.g1.x.c;
            pairData[o + 3] = pair.g1.x.d;
            pairData[o + 4] = pair.g1.y.a;
            pairData[o + 5] = pair.g1.y.b;
            pairData[o + 6] = pair.g1.y.c;
            pairData[o + 7] = pair.g1.y.d;
            pairData[o + 8] = pair.g1.z.a;
            pairData[o + 9] = pair.g1.z.b;
            pairData[o + 10] = pair.g1.z.c;
            pairData[o + 11] = pair.g1.z.d;

            pairData[o + 12] = pair.g2.x.a0;
            pairData[o + 13] = pair.g2.x.a1;
            pairData[o + 14] = pair.g2.x.a2;
            pairData[o + 15] = pair.g2.x.a3;
            pairData[o + 16] = pair.g2.x.b0;
            pairData[o + 17] = pair.g2.x.b1;
            pairData[o + 18] = pair.g2.x.b2;
            pairData[o + 19] = pair.g2.x.b3;
            pairData[o + 20] = pair.g2.y.a0;
            pairData[o + 21] = pair.g2.y.a1;
            pairData[o + 22] = pair.g2.y.a2;
            pairData[o + 23] = pair.g2.y.a3;
            pairData[o + 24] = pair.g2.y.b0;
            pairData[o + 25] = pair.g2.y.b1;
            pairData[o + 26] = pair.g2.y.b2;
            pairData[o + 27] = pair.g2.y.b3;
            pairData[o + 28] = pair.g2.z.a0;
            pairData[o + 29] = pair.g2.z.a1;
            pairData[o + 30] = pair.g2.z.a2;
            pairData[o + 31] = pair.g2.z.a3;
            pairData[o + 32] = pair.g2.z.b0;
            pairData[o + 33] = pair.g2.z.b1;
            pairData[o + 34] = pair.g2.z.b2;
            pairData[o + 35] = pair.g2.z.b3;

            o += 36;
        }
        one = nrun(pairData);
    }

    public int result() {
        return one ? 1 : 0;
    }

    private native boolean nrun(long[] data);
}
