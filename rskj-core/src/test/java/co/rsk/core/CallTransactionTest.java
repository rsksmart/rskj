/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 01/02/2017.
 */
class CallTransactionTest {
    @Test
    void fromSignature() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        Assertions.assertNotNull(func);

        Assertions.assertNotNull(func.inputs);
        Assertions.assertEquals(1, func.inputs.length);
        Assertions.assertEquals("string", func.inputs[0].getType());

        Assertions.assertNotNull(func.outputs);
        Assertions.assertEquals(1, func.outputs.length);
        Assertions.assertEquals("int", func.outputs[0].getType());
    }

    @Test
    void tooManyArguments() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        try {
            func.encode("first", "second");
            Assertions.fail();
        }
        catch (CallTransaction.CallTransactionException ex) {
            Assertions.assertTrue(ex.getMessage().startsWith("Too many arguments"));
        }
    }

    @Test
    void tooManyArgumentsUsingParams() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        try {
            func.encode(func.inputs, "first", "second");
            Assertions.fail();
        }
        catch (CallTransaction.CallTransactionException ex) {
            Assertions.assertTrue(ex.getMessage().startsWith("Too many arguments"));
        }
    }

    @Test
    void intTypeGetCanonicalNameForInt() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assertions.assertEquals("int256", type.getCanonicalName());
    }

    @Test
    void intTypeGetCanonicalNameForUInt() {
        CallTransaction.IntType type = new CallTransaction.IntType("uint");

        Assertions.assertEquals("uint256", type.getCanonicalName());
    }

    @Test
    void intTypeEncodeHexadecimalString() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assertions.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("01020304", 16)), type.encode("0x01020304"));
    }

    @Test
    void intTypeEncodeHexadecimalStringWithoutPrefix() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assertions.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("0102030b", 16)), type.encode("0102030b"));
    }

    @Test
    void intTypeEncodeDecimalString() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assertions.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("01020304", 10)), type.encode("01020304"));
    }

    @Test
    void decodeString() {
        SolidityType.StringType type = new SolidityType.StringType();
        byte[] toDecode = new byte[] {
                // len of string = 5
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5,
                // string
                104, 101, 108, 108, 111, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        Assertions.assertEquals("hello", type.decode(toDecode));
    }

    @Test
    void decodeEventData() {
        CallTransaction.Function event = CallTransaction.Function.fromEventSignature("test", new CallTransaction.Param[]{
                new CallTransaction.Param(true, "txHash", SolidityType.getType("bytes32")),
                new CallTransaction.Param(false, "amount", SolidityType.getType("uint"))
        });

        String value = "0000000000000000000000000000000000000000000000000000000000000020";
        Assertions.assertEquals(new BigInteger("32"), event.decodeEventData(Hex.decode(value))[0]);
    }
}
