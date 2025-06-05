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
 * Helper class containing tests for secp256k1 point addition operations.
 * This class extracts and encapsulates all point addition test logic from Secp256k1ServiceTest.
 */
public class Secp256k1AdditionTestHelper {
    // Constants for test vectors
    private static final String V1Y = "29896722852569046015560700294576055776214335159245303116488692907525646231534";
    private static final String V1BY2X = "90462569716653277674664832038037428010367175520031690655826237506178777087235";
    private static final String V1BY2Y = "30122570767565969031174451675354718271714177419582540229636601003470726681395";

    /**
     * Runs all point addition tests for the given Secp256k1Service implementation
     */
    public static void testAddition(Secp256k1Service secp256k1) {
        testSecpAddTwoPoints(secp256k1);
        testSecpAddZeroPointsShouldBeZero(secp256k1);
        testSecpAddEmptyInputShouldBeZero(secp256k1);
        testSecpAddPointPlusInfinityIsPoint(secp256k1);
        testSecpAddInfinityPlusPointIsPoint(secp256k1);
        testSecpAddPointNotOnCurveShouldFail(secp256k1);
        testReturnInfinityOnIdenticalInputPointValuesOfX(secp256k1);
        testReturnTrueAddAndComputeSlope(secp256k1);
    }

    /**
     * Test adding two identical points
     */
    private static void testSecpAddTwoPoints(Secp256k1Service secp256k1) {
        // given
        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000001" + 
            TestUtils.bigIntegerToHexDW(V1Y) +
            "0000000000000000000000000000000000000000000000000000000000000001" + 
            TestUtils.bigIntegerToHexDW(V1Y);

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var ox = new BigInteger(V1BY2X);
        final var oy = new BigInteger(V1BY2Y);
        final var outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        // when
        final var result = secp256k1.add(input);

        // then
        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test adding two zero points should return zero
     */
    private static void testSecpAddZeroPointsShouldBeZero(Secp256k1Service secp256k1) {
        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000";

        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test empty input should return zero point
     */
    private static void testSecpAddEmptyInputShouldBeZero(Secp256k1Service secp256k1) {
        final var input = HexUtils.stringHexToByteArray("");
        final var output = HexUtils.stringHexToByteArray(
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"
        );

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test adding point and infinity should return the original point
     */
    private static void testSecpAddPointPlusInfinityIsPoint(Secp256k1Service secp256k1) {
        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000001" + 
            TestUtils.bigIntegerToHexDW(V1Y);

        final var inputStr = outputStr +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test adding infinity and point should return the original point
     */
    private static void testSecpAddInfinityPlusPointIsPoint(Secp256k1Service secp256k1) {
        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000001" +
            TestUtils.bigIntegerToHexDW(V1Y);

        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" + outputStr;

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test adding point not on curve should fail
     */
    private static void testSecpAddPointNotOnCurveShouldFail(Secp256k1Service secp256k1) {
        final var inputStr =
            "1111111111111111111111111111111111111111111111111111111111111111" +
            "1111111111111111111111111111111111111111111111111111111111111111" +
            "1111111111111111111111111111111111111111111111111111111111111111" +
            "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var result = secp256k1.add(input);

        Assertions.assertNull(result);
    }

    /**
     * Test adding points with same x coordinate but different y coordinates
     */
    private static void testReturnInfinityOnIdenticalInputPointValuesOfX(Secp256k1Service secp256k1) {
        final var p0x = new BigInteger("3");
        final var p0y = new BigInteger("21320899557911560362763253855565071047772010424612278905734793689199612115787");
        final var p1x = new BigInteger("3");
        final var p1y = new BigInteger("-21320899557911560362763253855565071047772010424612278905734793689203907084060");
        final var input = new byte[128];

        encodeECPointsInput(input, p0x, p0y, p1x, p1y);

        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000";
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }

    /**
     * Test adding two different valid points
     */
    private static void testReturnTrueAddAndComputeSlope(Secp256k1Service secp256k1) {
        final var p0x = new BigInteger("4");
        final var p0y = new BigInteger("40508090799132825824753983223610497876805216745196355809233758402754120847507");
        final var p1x = new BigInteger("1624070059937464756887933993293429854168590106605707304006200119738501412969");
        final var p1y = new BigInteger("48810817106871756219742442189260392858217846784043974224646271552914041676099");
        final var input = new byte[128];

        encodeECPointsInput(input, p0x, p0y, p1x, p1y);

        final var ox = "59470963110652214182270290319243047549711080187995156844066669631124720856270";
        final var oy = "75549874947483386113764723043915448105868538368156141886808196158351727282824";
        final var output = buildECPointsOutput(ox, oy);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(output, result);
    }
}