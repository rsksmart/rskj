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

import com.fasterxml.jackson.annotation.JsonFilter;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonFilter("opFilter")
public class Op {

    private OpCode op;
    private int depth;
    private int pc;
    private long gas;
    private long gasCost;

    // Note that "memory" and "stack" are included in JSON serialization (debug_traceTransaction)
    // so we remove them from LGTM warnings.
    private List<String> memory = new ArrayList<>(); // lgtm [java/unused-container]
    private List<String> stack = new ArrayList<>(); // lgtm [java/unused-container]
    private Map<String, String> storage = new HashMap<>();

    public OpCode getOp() {
        return op;
    }

    public void setOp(OpCode op) {
        this.op = op;
    }

    public void setDepth(int depth) {
        this.depth = depth;
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

    public void setGasCost(long gasCost) { this.gasCost = gasCost; }

    public void setMemory(Memory memory) {
        int size = memory.size();

        for (int k = 0; k < size; k += 32) {
            byte[] bytes = memory.read(k, Math.min(32, size - k));
            this.memory.add(ByteUtil.toHexString(bytes));
        }
    }

    public void setStorage(Map<String, String> storage) {
        this.storage = storage;
    }

    public void setStack(Stack stack) {
        int size = stack.size();

        for (int k = 0; k < size; k++) {
            this.stack.add(ByteUtil.toHexString(stack.get(k).getData()));
        }
    }
}
