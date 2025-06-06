/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;

import static org.ethereum.crypto.signature.Secp256k1ServiceTest.buildECPointsOutput;
import static org.ethereum.crypto.signature.Secp256k1ServiceTest.encodeECPointsInput;

/**
 * Helper class containing tests for secp256k1 point multiplication operations.
 */
public class Secp256k1MultiplicationHelper {
    // Constants from original test class
    private static final String V1Y = "29896722852569046015560700294576055776214335159245303116488692907525646231534";

    /**
     * Runs all point multiplication tests for the given Secp256k1Service implementation
     */
    public static void testMultiplication(Secp256k1Service secp256k1) {
        testMultiplyScalarAndPoint(secp256k1);
        testIdentityWhenMultipliedByScalarValueOne(secp256k1);
        testMultiplyPointByScalar(secp256k1);
        testSumMultiplyPointByScalar(secp256k1);
        testFailForPointNotOnCurve(secp256k1);
        testFailForNotEnoughParams(secp256k1);
        testFailEmptyParams(secp256k1);
    }

    private static void testMultiplyScalarAndPoint(Secp256k1Service secp256k1) {
        // given
        final var x = BigInteger.valueOf(1);
        final var y = new BigInteger(V1Y);
        final var multiplier = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
        final var input = new byte[96];

        encodeECPointsInput(input, x, y, multiplier);

        final var ox = "68306631035792818416930554521980007078198693994042647901813352646899028694565";
        final var oy = "763410389832780290161227297165449309800016629866253823160953352172730927280";
        final var output = buildECPointsOutput(ox, oy);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }

    private static void testIdentityWhenMultipliedByScalarValueOne(Secp256k1Service secp256k1) {
        // given
        final var x = new BigInteger("1");
        final var y = new BigInteger(V1Y);
        final var multiplier = BigInteger.valueOf(1);
        final var input = new byte[96];

        encodeECPointsInput(input, x, y, multiplier);

        final var outputStr = TestUtils.bigIntegerToHexDW("1") + TestUtils.bigIntegerToHexDW(V1Y);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }

    private static void testMultiplyPointByScalar(Secp256k1Service secp256k1) {
        // given
        final var x = BigInteger.valueOf(1);
        final var y = new BigInteger(V1Y);
        final var multiplier = BigInteger.valueOf(9);
        final var input = new byte[96];

        encodeECPointsInput(input, x, y, multiplier);

        final var v1by9x = "46171929588085016379679198610744759757996296651373714437564035753833216770329";
        final var v1by9y = "4076329532618667641907419885981677362511359868272295070859229146922980867493";
        final var output = buildECPointsOutput(v1by9x, v1by9y);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }

    private static void testSumMultiplyPointByScalar(Secp256k1Service secp256k1) {
        // given
        final var x = new BigInteger("1");
        final var y = new BigInteger(V1Y);
        final var multiplier = BigInteger.valueOf(2);
        final var input = new byte[96];

        encodeECPointsInput(input, x, y, multiplier);

        final var v1by2x = "90462569716653277674664832038037428010367175520031690655826237506178777087235";
        final var v1by2y = "30122570767565969031174451675354718271714177419582540229636601003470726681395";
        final var output = buildECPointsOutput(v1by2x, v1by2y);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }

    private static void testFailForPointNotOnCurve(Secp256k1Service secp256k1) {
        // given
        String inputStr = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertNull(result);
    }

    private static void testFailForNotEnoughParams(Secp256k1Service secp256k1) {
        // given
        String inputStr = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertNull(result);
    }

    private static void testFailEmptyParams(Secp256k1Service secp256k1) {
        // given
        final var input = HexUtils.stringHexToByteArray("");
        final var output = HexUtils.stringHexToByteArray(
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"
        );

        // when
        final var result = secp256k1.mul(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }
}