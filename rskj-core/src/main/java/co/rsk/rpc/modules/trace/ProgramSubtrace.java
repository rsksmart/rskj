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
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.util.List;

public class ProgramSubtrace {
    private final CallType callType;
    private final byte[] creationData;
    private final ProgramInvoke programInvoke;
    private final ProgramResult programResult;
    private final List<ProgramSubtrace> subtraces;

    public ProgramSubtrace(CallType callType, byte[] creationData, ProgramInvoke programInvoke, ProgramResult programResult, List<ProgramSubtrace> subtraces) {
        this.callType = callType;
        this.creationData = creationData;
        this.programInvoke = programInvoke;
        this.programResult = programResult;
        this.subtraces = subtraces;
    }

    public CallType getCallType() { return this.callType; }

    public byte[] getCreationData() { return this.creationData; }

    public ProgramInvoke getProgramInvoke() {
        return this.programInvoke;
    }

    public ProgramResult getProgramResult() {
        return this.programResult;
    }

    public List<ProgramSubtrace> getSubtraces() { return this.subtraces; }
}
