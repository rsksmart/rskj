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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class BytecodeCompiler {
    public byte[] compile(String code) {
        return compile(code.split("\\s+"));
    }

    private byte[] compile(String[] tokens) {
        List<Byte> bytecodes = new ArrayList<>();
        int ntokens = tokens.length;

        for (int i = 0; i < ntokens; i++) {
            String token = tokens[i].toLowerCase();

            if (token.isEmpty())
                continue;

            bytecodes.add(compileToken(token));
        }

        int nbytes = bytecodes.size();
        byte[] result = new byte[nbytes];

        for (int k = 0; k < nbytes; k++)
            result[k] = bytecodes.get(k).byteValue();

        return result;
    }

    private byte compileToken(String token) {
        if ("push1".equals(token))
            return 0x60;
        if ("push2".equals(token))
            return 0x61;
        if ("push32".equals(token))
            return 0x7f;
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

        if ("dup1".equals(token))
            return (byte)0x80;
        if ("dup2".equals(token))
            return (byte)0x81;
        if ("dup3".equals(token))
            return (byte)0x82;
        if ("dup4".equals(token))
            return (byte)0x83;
        if ("dup5".equals(token))
            return (byte)0x84;
        if ("dup6".equals(token))
            return (byte)0x85;
        if ("dup7".equals(token))
            return (byte)0x86;
        if ("dup8".equals(token))
            return (byte)0x87;
        if ("dup9".equals(token))
            return (byte)0x88;
        if ("dup10".equals(token))
            return (byte)0x89;
        if ("dup11".equals(token))
            return (byte)0x8a;
        if ("dup12".equals(token))
            return (byte)0x8b;
        if ("dup13".equals(token))
            return (byte)0x8c;
        if ("dup14".equals(token))
            return (byte)0x8d;
        if ("dup15".equals(token))
            return (byte)0x8e;
        if ("dup16".equals(token))
            return (byte)0x8f;
        if ("dupn".equals(token))
            return (byte)0xa9;

        if ("swap1".equals(token))
            return (byte)0x90;
        if ("swap2".equals(token))
            return (byte)0x91;
        if ("swap3".equals(token))
            return (byte)0x92;
        if ("swap4".equals(token))
            return (byte)0x93;
        if ("swap5".equals(token))
            return (byte)0x94;
        if ("swap6".equals(token))
            return (byte)0x95;
        if ("swap7".equals(token))
            return (byte)0x96;
        if ("swap8".equals(token))
            return (byte)0x97;
        if ("swap9".equals(token))
            return (byte)0x98;
        if ("swap10".equals(token))
            return (byte)0x99;
        if ("swap11".equals(token))
            return (byte)0x9a;
        if ("swap12".equals(token))
            return (byte)0x9b;
        if ("swap13".equals(token))
            return (byte)0x9c;
        if ("swap14".equals(token))
            return (byte)0x9d;
        if ("swap15".equals(token))
            return (byte)0x9e;
        if ("swap16".equals(token))
            return (byte)0x9f;

        if ("swapn".equals(token))
            return (byte)0xaa;

        if ("jump".equals(token))
            return (byte)0x56;
        if ("jumpi".equals(token))
            return (byte)0x57;
        if ("jumpdest".equals(token))
            return (byte)0x5b;

        if (token.startsWith("0x"))
            return (byte)Integer.parseInt(token.substring(2), 16);

        return (byte)Integer.parseInt(token);
    }
}
