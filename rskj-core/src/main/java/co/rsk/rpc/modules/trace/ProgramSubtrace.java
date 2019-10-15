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

package co.rsk.rpc.modules.trace;

import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.InvokeData;

import java.util.Collections;
import java.util.List;

public class ProgramSubtrace {
    private final TraceType traceType;
    private final CallType callType;
    private final CreationData creationData;
    private final InvokeData invokeData;
    private final ProgramResult programResult;
    private final List<ProgramSubtrace> subtraces;

    public ProgramSubtrace(InvokeData invokeData) {
        this.traceType = TraceType.SUICIDE;
        this.callType = CallType.NONE;
        this.creationData = null;
        this.invokeData = invokeData;
        this.programResult = null;
        this.subtraces = Collections.EMPTY_LIST;
    }

    public ProgramSubtrace(CreationData creationData, InvokeData invokeData, ProgramResult programResult, List<ProgramSubtrace> subtraces) {
        this.traceType = TraceType.CREATE;
        this.callType = CallType.NONE;
        this.creationData = creationData;
        this.invokeData = invokeData;
        this.programResult = programResult;
        this.subtraces = subtraces;
    }

    public ProgramSubtrace(CallType callType, InvokeData invokeData, ProgramResult programResult, List<ProgramSubtrace> subtraces) {
        this.traceType = TraceType.CALL;
        this.callType = callType;
        this.creationData = null;
        this.invokeData = invokeData;
        this.programResult = programResult;
        this.subtraces = subtraces;
    }

    public TraceType getTraceType() { return this.traceType; }

    public CallType getCallType() { return this.callType; }

    public CreationData getCreationData() { return this.creationData; }

    public InvokeData getInvokeData() {
        return this.invokeData;
    }

    public ProgramResult getProgramResult() {
        return this.programResult;
    }

    public List<ProgramSubtrace> getSubtraces() { return this.subtraces; }
}
