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

import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ValueTest {

    public static boolean cmp(Value value, Value o) {
        return Objects.deepEquals(value.asObj(), o.asObj());
    }

    @Test
    void testCmp() {
        Value val1 = new Value("hello");
        Value val2 = new Value("world");

        assertFalse(cmp(val1, val2), "Expected values not to be equalBytes");

        Value val3 = new Value("hello");
        Value val4 = new Value("hello");

        assertTrue(cmp(val3, val4), "Expected values to be equalBytes");
    }

     // this test make no sense, it is testing Hex.decode vs. ByteUtil.toHexString
     // Value will set rlp = passed value, and return rlp in .encode()
    @Ignore
    void longListRLPBug_1() {
        String testRlp = "f7808080d387206f72726563748a626574656c676575736580d387207870726573738a70726564696361626c658080808080808080808080";

        // Value val = Value.fromRlpEncoded(Hex.decode(testRlp));

        // assertEquals(testRlp, ByteUtil.toHexString(val.encode()));
    }

    @Test
    void toString_Empty() {
        Value val = new Value(null);
        String str = val.asString();

        assertEquals("", str);
    }

    @Test
    void toString_SameString() {
        Value val = new Value("hello");
        String str = val.asString();

        assertEquals("hello", str);
    }

    @Test
    void isEmpty_Null() {
        Value val = new Value(null);
        assertTrue(val.isEmpty());
    }

    @Test
    void isEmpty_EmptyString() {
        Value val = new Value("");
        assertTrue(val.isEmpty());
    }

    @Test
    void isEmpty_Bytes() {
        Value val = new Value(new byte[0]);
        assertTrue(val.isEmpty());
    }

    @Test
    void isEmpty_Array() {
        Value val = new Value(new String[0]);
        assertTrue(val.isEmpty());
    }

}
