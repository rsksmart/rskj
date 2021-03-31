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
import org.ethereum.vm.program.invoke.InvokeData;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.TransferInvoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.toHexStringOrEmpty;

public class SummarizedProgramTrace implements ProgramTrace {
    private final ProgramInvoke programInvoke;
    private final TransferInvoke transferInvoke;
    private final List<ProgramSubtrace> subtraces = new ArrayList<>();

    private String result;
    private String error;
    private boolean reverted;

    public SummarizedProgramTrace(ProgramInvoke programInvoke) {
        this.programInvoke = programInvoke;
        this.transferInvoke = null;
    }

    // TODO refactor to have two implementations
    // one for program invocations
    // another for simple transfers
    public SummarizedProgramTrace(TransferInvoke transferInvoke) {
        this.programInvoke = null;
        this.transferInvoke = transferInvoke;
    }

    @Override
    public ProgramInvoke getProgramInvoke() {
        return this.programInvoke;
    }

    public InvokeData getInvokeData() {
        if (this.programInvoke != null) {
            return this.programInvoke;
        } else {
            return this.transferInvoke;
        }
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
        this.subtraces.add(programSubTrace);
    }

    @Override
    public List<ProgramSubtrace> getSubtraces() {
        return Collections.unmodifiableList(this.subtraces);
    }

    @Override
    public void merge(ProgramTrace programTrace) {

    }

    @Override
    public ProgramTrace result(byte[] result) {
        setResult(toHexStringOrEmpty(result));
        return this;
    }

    @Override
    public ProgramTrace revert(boolean reverted) {
        this.reverted = reverted;
        return this;
    }

    public boolean getReverted() { return this.reverted; }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    @Override
    public ProgramTrace error(Exception error) {
        setError(error == null ? "" : format("%s: %s", error.getClass(), error.getMessage()));
        return this;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
