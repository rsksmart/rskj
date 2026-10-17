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

package co.rsk.test;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.vm.BytecodeCompiler;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;

import java.util.HashSet;

/**
 * Created by ajlopez on 11/16/2019.
 */
public class VirtualMachineWorld {
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final ProgramInvokeMockImpl invoke;

    private byte[] memory;
    private DataWord[] stack;
    private long gasUsed;

    public VirtualMachineWorld() {
        this.invoke = new ProgramInvokeMockImpl();
        this.reset();
    }

    public void reset() {
        this.memory = null;
        this.stack = null;
        this.gasUsed = 0;
    }

    public byte[] getMemory() {
        return this.memory;
    }

    public void setMemory(byte[] memory) {
        this.memory = memory;
    }

    public DataWord[] getStack() {
        return this.stack;
    }

    public void setStack(DataWord[] stack) {
        this.stack = stack;
    }

    public void execute(String code) {
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] bytecodes = compiler.compile(code);

        VM vm = new VM(vmConfig, null);
        Program program = new Program(vmConfig, null, null, null, bytecodes, this.invoke, null, new HashSet<>());

        if (this.memory != null)
            program.initMem(this.memory);

        Stack programStack = program.getStack();

        if (this.stack != null) {
            for (int k = 0; k < this.stack.length; k++) {
                programStack.push(this.stack[k]);
            }
        }

        vm.steps(program, Long.MAX_VALUE);

        this.memory = program.getMemory();
        programStack = program.getStack();
        this.stack = new DataWord[programStack.size()];

        for (int k = this.stack.length; k > 0;) {
            this.stack[--k] = programStack.pop();
        }

        this.gasUsed = program.getResult().getGasUsed();
    }

    public long getGasUsed() {
        return this.gasUsed;
    }
}
