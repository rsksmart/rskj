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
        if (token.length() > 4 && "push".equals(token.substring(0, 4)))
            return (byte)(0x60 + Integer.parseInt(token.substring(4)) - 1);

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

        if (token.startsWith("0x"))
            return (byte)Integer.parseInt(token.substring(2), 16);

        return (byte)Integer.parseInt(token);
    }
}
