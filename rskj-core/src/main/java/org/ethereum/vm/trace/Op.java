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

package org.ethereum.vm.trace;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;

import java.util.ArrayList;
import java.util.List;

public class Op {

    private OpCode code;
    private int deep;
    private int pc;
    private long gas;
    private OpActions actions;
    private List<String> memory = new ArrayList<>();
    private List<String> stack = new ArrayList<>();

    public OpCode getCode() {
        return code;
    }

    public void setCode(OpCode code) {
        this.code = code;
    }

    public int getDeep() {
        return deep;
    }

    public void setDeep(int deep) {
        this.deep = deep;
    }

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public long getGas() {
        return gas;
    }

    public void setGas(long gas) {
        this.gas = gas;
    }

    public OpActions getActions() {
        return actions;
    }

    public void setActions(OpActions actions) {
        this.actions = actions;
    }

    public void setMemory(Memory memory) {
        int size = memory.size();

        for (int k = 0; k < size; k += 32) {
            byte[] bytes = memory.read(k, Math.min(32, size - k));
            this.memory.add(Hex.toHexString(bytes));
        }
    }

    public void setStack(Stack stack) {
        int size = stack.size();

        for (int k = 0; k < size; k++) {
            this.stack.add(Hex.toHexString(stack.get(k).getData()));
        }
    }
}
