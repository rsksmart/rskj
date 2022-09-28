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

import org.ethereum.vm.GasCost;

import java.util.HashMap;
import java.util.Map;

public class CallDepthGasLocker {

    private final Map<Integer, Long> lockedForDepth;

    public CallDepthGasLocker() {
        this.lockedForDepth = new HashMap<>();
    }

    public long lock(CallSubProgram callProg) {
        long lockedGas = GasCost.toGas(callProg.getAvailableGas()) / 64;
        long gasForCallee = GasCost.subtract(callProg.getAvailableGas(), lockedGas);

        if (callProg.getRequiredGas() <= gasForCallee) {
            return 0;
        }

        long prevGasLock = this.lockedForDepth.getOrDefault(callProg.getDepth(), 0L);
        long newGasLock = GasCost.subtract(callProg.getRequiredGas(), gasForCallee);

        // locked for depth can only increase
        if (newGasLock <= prevGasLock) {
            return 0;
        }

        long diffGasLock = GasCost.subtract(newGasLock, prevGasLock);
        callProg.consumeGas(diffGasLock); // TODO:I aren't we missing consuming gasForCallee too? before we were consuming gasCost+calleeGas, now just gasCost+this small lock
        callProg.futureRefund(diffGasLock);

        this.lockedForDepth.put(callProg.getDepth(), newGasLock);

        return diffGasLock;
    }

}
