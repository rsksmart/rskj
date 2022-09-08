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

package co.rsk.vm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class BytecodeCompilerTest {
    @Test
    public void compileSimpleOpcode() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("ADD");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(1, result[0]);
    }

    @Test
    public void compileSimpleOpcodeWithSpaces() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile(" ADD ");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(1, result[0]);
    }

    @Test
    public void compileTwoOpcodes() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("ADD SUB");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
        Assertions.assertEquals(1, result[0]);
        Assertions.assertEquals(3, result[1]);
    }

    @Test
    public void compileFourOpcodes() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("ADD MUL SUB DIV");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.length);
        Assertions.assertEquals(1, result[0]);
        Assertions.assertEquals(2, result[1]);
        Assertions.assertEquals(3, result[2]);
        Assertions.assertEquals(4, result[3]);
    }

    @Test
    public void compileHexadecimalValueOneByte() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("0x01");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(1, result[0]);
    }

    @Test
    public void compileHexadecimalValueTwoByte() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("0x0102");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
        Assertions.assertEquals(1, result[0]);
        Assertions.assertEquals(2, result[1]);
    }

    @Test
    public void compileSimpleOpcodeInLowerCase() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("add");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(1, result[0]);
    }

    @Test
    public void compileSimpleOpcodeInMixedCase() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] result = compiler.compile("Add");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(1, result[0]);
    }
}
