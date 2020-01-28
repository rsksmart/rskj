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

public class BN128Pair {

    private BN128G1 g1;
    private BN128G2 g2;

    public static BN128Pair of(BN128G1 g1, BN128G2 g2) {
        return new BN128Pair(g1, g2);
    }

    BN128Pair(BN128G1 g1, BN128G2 g2) {
        this.g1 = g1;
        this.g2 = g2;
    }

    public BN128G1 getG1() {
        return g1;
    }

    public BN128G2 getG2() {
        return g2;
    }

    Fp12 millerLoop() {

        // miller loop result equals "1" if at least one of the points is zero
        if (g1.isZero()) {return Fp12._1;}
        if (g2.isZero()) {return Fp12._1;}

        return PairingCheck.millerLoop(g1, g2);
    }
}
