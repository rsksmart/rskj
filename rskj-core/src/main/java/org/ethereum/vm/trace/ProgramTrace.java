package org.ethereum.vm.trace;

import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.Storage;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.util.List;

public interface ProgramTrace {
    ProgramInvoke getProgramInvoke();

    void saveGasCost(long gasCost);

    Op addOp(byte code, int pc, int deep, long gas, Memory memory, Stack stack, Storage storage);

    void addSubTrace(ProgramSubtrace programSubTrace);

    List<ProgramSubtrace> getSubtraces();

    void merge(ProgramTrace programTrace);

    ProgramTrace result(byte[] result);

    ProgramTrace error(Exception error);

    ProgramTrace revert(boolean reverted);

    boolean isEmpty();
}
