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

import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 01/02/2017.
 */
public class CallTransactionTest {
    @Test
    public void fromSignature() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        Assert.assertNotNull(func);

        Assert.assertNotNull(func.inputs);
        Assert.assertEquals(1, func.inputs.length);
        Assert.assertEquals("string", func.inputs[0].getType());

        Assert.assertNotNull(func.outputs);
        Assert.assertEquals(1, func.outputs.length);
        Assert.assertEquals("int", func.outputs[0].getType());
    }

    @Test
    public void tooManyArguments() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        try {
            func.encode("first", "second");
            Assert.fail();
        }
        catch (CallTransaction.CallTransactionException ex) {
            Assert.assertTrue(ex.getMessage().startsWith("Too many arguments"));
        }
    }

    @Test
    public void tooManyArgumentsUsingParams() {
        CallTransaction.Function func = CallTransaction.Function.fromSignature("func", new String[] { "string" }, new String[] { "int" });

        try {
            func.encode(func.inputs, "first", "second");
            Assert.fail();
        }
        catch (CallTransaction.CallTransactionException ex) {
            Assert.assertTrue(ex.getMessage().startsWith("Too many arguments"));
        }
    }

    @Test
    public void intTypeGetCanonicalNameForInt() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assert.assertEquals("int256", type.getCanonicalName());
    }

    @Test
    public void intTypeGetCanonicalNameForUInt() {
        CallTransaction.IntType type = new CallTransaction.IntType("uint");

        Assert.assertEquals("uint256", type.getCanonicalName());
    }

    @Test
    public void intTypeEncodeHexadecimalString() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assert.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("01020304", 16)), type.encode("0x01020304"));
    }

    @Test
    public void intTypeEncodeHexadecimalStringWithoutPrefix() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assert.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("0102030b", 16)), type.encode("0102030b"));
    }

    @Test
    public void intTypeEncodeDecimalString() {
        CallTransaction.IntType type = new CallTransaction.IntType("int");

        Assert.assertArrayEquals(CallTransaction.IntType.encodeInt(new BigInteger("01020304", 10)), type.encode("01020304"));
    }

    @Test
    public void decodeString() {
        SolidityType.StringType type = new SolidityType.StringType();
        byte[] toDecode = new byte[] {
                // len of string = 5
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5,
                // string
                104, 101, 108, 108, 111, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        Assert.assertEquals("hello", type.decode(toDecode));
    }
}
