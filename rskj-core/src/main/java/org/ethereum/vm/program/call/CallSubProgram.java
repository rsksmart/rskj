/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package org.ethereum.vm.program.call;

import org.ethereum.vm.program.Program;

public class CallSubProgram {

    private final int depth;
    private final long requiredGas;
    private final long availableGas;
    private final Program baseProgram;

    public CallSubProgram(Program baseProgram, long requiredGas, long availableGas) {
        this.depth = baseProgram.getCallDeep();
        this.requiredGas = requiredGas;
        this.availableGas = availableGas;
        this.baseProgram = baseProgram;
    }

    void consumeGas(long value) {
        this.baseProgram.spendGas(value, "Locked for CALL depth " + depth);
    }

    void futureRefund(long value) {
        this.baseProgram.futureRefundGas(value);
    }

    int getDepth() {
        return depth;
    }

    long getRequiredGas() {
        return requiredGas;
    }

    long getAvailableGas() {
        return availableGas;
    }

}
