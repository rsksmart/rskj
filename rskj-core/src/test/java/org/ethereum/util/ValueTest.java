/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.util;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class ValueTest {

    public static boolean cmp(Value value, Value o) {
        return Objects.deepEquals(value.asObj(), o.asObj());
    }

    @Test
    public void testCmp() {
        Value val1 = new Value("hello");
        Value val2 = new Value("world");

        assertFalse(cmp(val1, val2), "Expected values not to be equalBytes");

        Value val3 = new Value("hello");
        Value val4 = new Value("hello");

        assertTrue(cmp(val3, val4), "Expected values to be equalBytes");
    }

    @Test
    public void testTypes() {
        Value str = new Value("str");
        assertEquals(str.asString(), "str");

        Value num = new Value(1);
        assertEquals(num.asInt(), 1);

        Value inter = new Value(new Object[]{1});
        Object[] interExp = new Object[]{1};
        assertTrue(cmp(new Value(inter.asObj()), new Value(interExp)));

        Value byt = new Value(new byte[]{1, 2, 3, 4});
        byte[] bytExp = new byte[]{1, 2, 3, 4};
        assertTrue(Arrays.equals(byt.asBytes(), bytExp));

        Value bigInt = new Value(BigInteger.valueOf(10));
        BigInteger bigExp = BigInteger.valueOf(10);
        assertEquals(bigInt.asBigInt(), bigExp);
    }

    @Test
    public void longListRLPBug_1() {
        String testRlp = "f7808080d387206f72726563748a626574656c676575736580d387207870726573738a70726564696361626c658080808080808080808080";

        Value val = Value.fromRlpEncoded(Hex.decode(testRlp));

        assertEquals(testRlp, ByteUtil.toHexString(val.encode()));
    }

    @Test
    public void toString_Empty() {
        Value val = new Value(null);
        String str = val.toString();

        assertEquals("", str);
    }

    @Test
    public void toString_SameString() {
        Value val = new Value("hello");
        String str = val.toString();

        assertEquals("hello", str);
    }

    @Test
    public void toString_Array() {
        Value val = new Value(new String[] {"hello", "world", "!"});
        String str = val.toString();

        assertEquals(" ['hello', 'world', '!'] ", str);
    }

    @Test
    public void toString_UnsupportedType() {
        Value val = new Value('a');
        String str = val.toString();

        assertEquals("Unexpected type", str);
    }

    @Test
    public void isEmpty_Null() {
        Value val = new Value(null);
        assertTrue(val.isEmpty());
    }

    @Test
    public void isEmpty_EmptyString() {
        Value val = new Value("");
        assertTrue(val.isEmpty());
    }

    @Test
    public void isEmpty_Bytes() {
        Value val = new Value(new byte[0]);
        assertTrue(val.isEmpty());
    }

    @Test
    public void isEmpty_Array() {
        Value val = new Value(new String[0]);
        assertTrue(val.isEmpty());
    }

}
