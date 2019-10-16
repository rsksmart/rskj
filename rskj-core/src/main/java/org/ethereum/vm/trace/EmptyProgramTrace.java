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

import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.Storage;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.util.List;

public class EmptyProgramTrace implements ProgramTrace {
    @Override
    public ProgramInvoke getProgramInvoke() {
        return null;
    }

    @Override
    public void saveGasCost(long gasCost) {

    }

    @Override
    public Op addOp(byte code, int pc, int deep, long gas, Memory memory, Stack stack, Storage storage) {
        return null;
    }

    @Override
    public void addSubTrace(ProgramSubtrace programSubTrace) {

    }

    @Override
    public List<ProgramSubtrace> getSubtraces() {
        return null;
    }

    @Override
    public void merge(ProgramTrace programTrace) {

    }

    @Override
    public ProgramTrace result(byte[] result) {
        return this;
    }

    @Override
    public ProgramTrace error(Exception error) {
        return this;
    }

    @Override
    public ProgramTrace revert(boolean reverted) {
        return this;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
