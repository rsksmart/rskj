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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class BytecodeCompilerTest {
    @Test
    public void compileSimplePushWithHexadecimal() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x60, 0x43 }, compiler.compile("PUSH1 0x43"));
    }

    @Test
    public void compileAdd() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x01 }, compiler.compile("ADD"));
    }

    @Test
    public void compileMul() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x02 }, compiler.compile("MUL"));
    }

    @Test
    public void compileSub() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x03 }, compiler.compile("SUB"));
    }

    @Test
    public void compileDiv() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x04 }, compiler.compile("DIV"));
    }

    @Test
    public void compileSDiv() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x05 }, compiler.compile("SDIV"));
    }

    @Test
    public void compileMod() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x06 }, compiler.compile("MOD"));
    }

    @Test
    public void compileSMod() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x07 }, compiler.compile("SMOD"));
    }

    @Test
    public void compileDups() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0x80, (byte)0x81, (byte)0x82, (byte)0x83, (byte)0x84, (byte)0x85, (byte)0x86, (byte)0x87, (byte)0x88, (byte)0x89, (byte)0x8a, (byte)0x8b, (byte)0x8c, (byte)0x8d, (byte)0x8e, (byte)0x8f }, compiler.compile("DUP1 DUP2 DUP3 DUP4 DUP5 DUP6 DUP7 DUP8 DUP9 DUP10 DUP11 DUP12 DUP13 DUP14 DUP15 DUP16"));
    }

    @Test
    public void compileJumpDest() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0x5b }, compiler.compile("JUMPDEST"));
    }

    @Test
    public void compileJump() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0x56 }, compiler.compile("JUMP"));
    }

    @Test
    public void compileJumpI() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0x57 }, compiler.compile("JUMPI"));
    }

    @Test
    public void compileSimplePushWithHexadecimalUpperX() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x60, 0X43 }, compiler.compile("PUSH1 0x43"));
    }

    @Test
    public void compilePush2() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x61, 0x01, 0x02 }, compiler.compile("PUSH2 0x01 0x02"));
    }

    @Test
    public void compilePushes() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        for (int k = 1; k <= 32; k++) {
            byte[] bytecodes = new byte[k + 1];
            bytecodes[0] = (byte)(0x60 + k - 1);

            for (int j = 1; j <= k; j++)
                bytecodes[j] = (byte)j;

            String code = "PUSH" + k;

            for (int j = 1; j <= k; j++)
                code += " " + j;

            Assert.assertArrayEquals(bytecodes, compiler.compile(code));
        }
    }

    @Test
    public void compileDupNWithValue() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0xa8, 0x00 }, compiler.compile("DUPN 0x00"));
    }

    @Test
    public void compileSwapNWithValue() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0xa9, 0x01 }, compiler.compile("SWAPN 0x01"));
    }

    @Test
    public void compileTxIndex() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { (byte)0xaa }, compiler.compile("TXINDEX"));
    }

    @Test
    public void compileSimplePushWithDecimal() {
        BytecodeCompiler compiler = new BytecodeCompiler();

        Assert.assertArrayEquals(new byte[] { 0x60, 0x20 }, compiler.compile("PUSH1 32"));
    }
}
