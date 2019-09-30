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

import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvoke;

public class ProgramSubTrace {
    private final ProgramInvoke programInvoke;
    private final ProgramResult programResult;

    public ProgramSubTrace(ProgramInvoke programInvoke, ProgramResult programResult) {
        this.programInvoke = programInvoke;
        this.programResult = programResult;
    }

    public ProgramInvoke getProgramInvoke() {
        return this.programInvoke;
    }

    public ProgramResult getProgramResult() {
        return this.programResult;
    }
}
