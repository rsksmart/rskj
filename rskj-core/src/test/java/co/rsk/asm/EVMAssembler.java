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

package co.rsk.asm;

import co.rsk.lll.asm.CodeBlock;
import co.rsk.lll.asm.EVMAssemblerHelper;
import org.ethereum.vm.OpCode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.StringTokenizer;

import static org.ethereum.vm.OpCode.PUSH1;

/**
 * Created by Sergio on 02/07/2016.
 */
public class EVMAssembler {
    CodeBlock block;
    EVMAssemblerHelper helper;

    // To insert an code or a binary string manually by offset, use !num
    public static String extractInsertBinary(String str) {
        if ((str.length() >= 1) && (str.charAt(0) == '!')) {
            return str.substring(1);
        }
        return null;
    }

    public static String extractHex(String str) {
        if ((str.length() >= 2) && (str.charAt(0) == '0') && (str.charAt(1) == 'x')) {
            return str.substring(2);
        }
        return null;
    }

    public BigInteger parseValue(String tok) {
        BigInteger num;
        String aHex = extractHex(tok);
        if (aHex != null) {
            num = new BigInteger(aHex, 16);
        } else {
            num = new BigInteger(tok, 10);
        }
        return num;
    }
    // BigInteger.toByteArray() will return "0x00 0xff" if the value stored is 255, because
    // it uses 8-bit of first value to distinguish negative values.
    // bigUIntToByteArray() will return 0xff.
    public byte[] bigUIntToByteArray(BigInteger b) {
        byte[] value = b.toByteArray();
        if ((value.length > 1) && (value[0] == 0))
            value = Arrays.copyOfRange(value, 1, value.length);
        // No negative values accepted: remove starting zero.
        return value;
    }

    public byte[] assemble(String text) {
        StringTokenizer tokens = new StringTokenizer(text," \n\t",false);
        int errorToken = 0;
        block = new CodeBlock(null);
        block.startWrite();
        helper = new EVMAssemblerHelper();
        try {
            while (tokens.hasMoreTokens()) {
                String tok = tokens.nextToken();
                assembleToken(block,tok);
                errorToken++;
            }
            block.endWrite();
            if (!helper.performFixUp(block))
                return null;

            return block.getCode();
        } catch (Exception e) {
            return null;
        }

    }

    public void assembleToken(CodeBlock block,String tok) throws Exception {
        try {
            if (tok.endsWith(":")) { // label
                String label = tok.substring(0, tok.length() - 1);
                int id = helper.findLabel(label);
                if (id < 0)
                    id = helper.getNewLabel(label);
                helper.setLabelPosition(id, block, block.writeOffset());
            } else if (tok.startsWith("@")) { // ref
                String label = tok.substring(1);
                int id = helper.findLabel(label);
                if (id == -1)
                    id = helper.getNewLabel(label);

                int pushOpcode = 4 + PUSH1.val() - 1; // four bytes ref
                block.writer().write(pushOpcode);
                block.addTag(block.writer().size(), id);
                block.writer().write(0);
                block.writer().write(0);
                block.writer().write(0);
                block.writer().write(0);

            } else {
                // Data can be inserted by prefixing by '!' (e.g. !0x00 inserts a STOP opcode)
                String aBin = extractInsertBinary(tok);
                if (aBin != null) {
                    byte[] value = bigUIntToByteArray(parseValue(aBin));

                    block.writer().write(value);
                } else {
                    // Other hex data is inserted as a PUSH
                    byte[] value = bigUIntToByteArray(parseValue(tok));
                    if (value.length > 32) {
                        throw new Exception("Invalid value");
                    }
                    int pushOpcode = value.length + PUSH1.val() - 1;
                    block.writer().write(pushOpcode);
                    block.writer().write(value);
                }
            }
        } catch (NumberFormatException e) {
            byte opcode = OpCode.byteVal(tok);
            block.writer().write(opcode);
                /*if ((code>=PUSH1.val()) && (code<=PUSH32.val())) {
                    int nPush = code - PUSH1.val() + 1;
                }*/
        }
    }
}
