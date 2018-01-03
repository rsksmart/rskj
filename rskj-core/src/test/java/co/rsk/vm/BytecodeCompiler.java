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

import org.ethereum.vm.OpCode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class BytecodeCompiler {
    public byte[] compile(String code) {
        return compile(code.split("\\s+"));
    }

    class CompilerState {
        public Map<String,Integer> constants = new HashMap<>();
        public Map<Integer,String> refTable = new HashMap<>();
        int start;
    }

    private byte[] compile(String[] tokens) {
        //Map<String,Integer> constants = new HashMap<>();
        //Map<Integer,String> refTable = new HashMap<>();
        CompilerState state = new CompilerState();

        byte[]  code = compilePass(tokens,state);
        // Set the internal variables
        state.constants.put("INSTALLCODELEN",code.length-state.start);
        fix(code,state );
        return code;
    }

    private void fix(byte[] code,CompilerState state ) {
        for(Integer i : state.refTable.keySet()) {
            String label =state.refTable.get(i);
            if (!state.constants.containsKey(label))
                throw new RuntimeException("Undefined label at "+i);
            code[i] = state.constants.get(label).byteValue();
        }
    }

    private byte[] compilePass(String[] tokens,CompilerState state) {
        List<Byte> bytecodes = new ArrayList<>();
        int ntokens = tokens.length;

        for (int i = 0; i < ntokens; i++) {
            String token = tokens[i].toLowerCase();

            if (token.isEmpty())
                continue;

            String upToken = token.toUpperCase();

            if (upToken.charAt(0)=='.') {
                if (upToken.equals(".START")) {
                    state.start =bytecodes.size();
                    continue;
                }
            }

            if (upToken.charAt(0)=='$') {
                state.refTable.put(bytecodes.size(),upToken.substring(1));
                bytecodes.add((byte) 0);
                continue;
            }

            if (upToken.charAt(upToken.length()-1)==':') {
                state.constants.put(upToken.substring(0,token.length()-1),bytecodes.size()-state.start);
                continue;
            }

            bytecodes.add(compileToken(token));
        }

        int nbytes = bytecodes.size();
        byte[] result = new byte[nbytes];

        for (int k = 0; k < nbytes; k++)
            result[k] = bytecodes.get(k).byteValue();

        return result;
    }

    private boolean isOpcode(String opcode) {
        return OpCode.contains(opcode);
    }

    private byte compileToken(String token) {

        if (token.length() > 4 && "push".equals(token.substring(0, 4)))
            return (byte)(0x60 + Integer.parseInt(token.substring(4)) - 1);

       String upToken = token.toUpperCase();

        if (isOpcode(upToken ))
         return OpCode.byteVal(upToken );
       // The manual opcode checks should not be required anymore
       // Remove in a later clan up
        else
        if ("add".equals(token))
            return 0x01;
        if ("mul".equals(token))
            return 0x02;
        if ("sub".equals(token))
            return 0x03;
        if ("div".equals(token))
            return 0x04;
        if ("sdiv".equals(token))
            return 0x05;
        if ("mod".equals(token))
            return 0x06;
        if ("smod".equals(token))
            return 0x07;

        if ("dupn".equals(token))
            return (byte)0xa8;

        if (token.length() > 3 && "dup".equals(token.substring(0, 3)))
            return (byte)(0x80 + Integer.parseInt(token.substring(3)) - 1);

        if ("swapn".equals(token))
            return (byte)0xa9;

        if (token.length() > 4 && "swap".equals(token.substring(0, 4)))
            return (byte)(0x90 + Integer.parseInt(token.substring(4)) - 1);

        if ("txindex".equals(token))
            return (byte)0xaa;

        if ("jump".equals(token))
            return (byte)0x56;
        if ("jumpi".equals(token))
            return (byte)0x57;
        if ("jumpdest".equals(token))
            return (byte)0x5b;

        // only accept bytes
        if (token.startsWith("0x"))
            return (byte)Integer.parseInt(token.substring(2), 16);

        return (byte)Integer.parseInt(token);
    }
}
