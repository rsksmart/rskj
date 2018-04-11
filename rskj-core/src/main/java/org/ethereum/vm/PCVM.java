package org.ethereum.vm;

import org.ethereum.vm.program.Program;

import static org.ethereum.util.BIUtil.toBI;

public class PCVM implements VM {

    PrecompiledContracts.PrecompiledContract contract;
    byte[] data;

    public PCVM(PrecompiledContracts.PrecompiledContract contract, byte[] data) {
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
