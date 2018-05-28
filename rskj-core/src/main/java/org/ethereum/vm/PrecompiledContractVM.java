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

package org.ethereum.vm;

import org.ethereum.vm.program.Program;

public class PrecompiledContractVM implements VM {

    private PrecompiledContracts.PrecompiledContract contract;
    private byte[] data;

    public PrecompiledContractVM(PrecompiledContracts.PrecompiledContract contract, byte[] data) {
        this.contract = contract;
        this.data = data;
    }

    public void play(Program program) {
        long requiredGas = contract.getGasForData(data);

        program.spendGas(requiredGas, "Call precompiled contract");

        byte[] out = contract.execute(data);
        program.setHReturn(out);
    }
}
