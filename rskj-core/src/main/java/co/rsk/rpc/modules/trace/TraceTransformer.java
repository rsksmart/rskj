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

import co.rsk.core.RskAddress;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.trace.ProgramSubTrace;
import org.ethereum.vm.trace.ProgramTrace;

import java.util.ArrayList;
import java.util.List;

public class TraceTransformer {
    private TraceTransformer() {

    }

    public static List<TransactionTrace> toTraces(ProgramTrace trace, TransactionInfo txInfo, long blockNumber) {
        List<TransactionTrace> traces = new ArrayList<>();

        addTrace(traces, trace, txInfo, blockNumber, new TraceAddress());

        return traces;
    }

    private static void addTrace(List<TransactionTrace> traces, ProgramTrace trace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        traces.add(toTrace(trace, txInfo, blockNumber, traceAddress));

        int nsubtraces = trace.getSubtraces().size();

        for (int k = 0; k < nsubtraces; k++)
            addTrace(traces, trace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
    }

    private static void addTrace(List<TransactionTrace> traces, ProgramSubTrace subtrace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        traces.add(toTrace(subtrace.getProgramInvoke(), txInfo, blockNumber, traceAddress));

        int nsubtraces = subtrace.getSubtraces().size();

        for (int k = 0; k < nsubtraces; k++)
            addTrace(traces, subtrace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
    }

    public static TransactionTrace toTrace(ProgramTrace trace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        ActionTransactionTrace action = toAction(trace);
        String blockHash = TypeConverter.toUnformattedJsonHex(txInfo.getBlockHash());
        String transactionHash = txInfo.getReceipt().getTransaction().getHash().toJsonString();
        int transactionPosition = txInfo.getIndex();
        String type = "call";
        int subtraces = trace.getSubtraces().size();

        return new TransactionTrace(
            action,
            blockHash,
            blockNumber,
            transactionHash,
            transactionPosition,
            type,
            subtraces,
            traceAddress
        );
    }

    public static TransactionTrace toTrace(ProgramInvoke invoke, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        ActionTransactionTrace action = toAction(invoke);
        String blockHash = TypeConverter.toUnformattedJsonHex(txInfo.getBlockHash());
        String transactionHash = txInfo.getReceipt().getTransaction().getHash().toJsonString();
        int transactionPosition = txInfo.getIndex();
        String type = "call";
        int subtraces = 0;

        return new TransactionTrace(
                action,
                blockHash,
                blockNumber,
                transactionHash,
                transactionPosition,
                type,
                subtraces,
                traceAddress
        );
    }

    public static ActionTransactionTrace toAction(ProgramTrace trace) {
        ProgramInvoke invoke = trace.getProgramInvoke();

        return toAction(invoke);
    }

    public static ActionTransactionTrace toAction(ProgramInvoke invoke) {
        String from = new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        String to = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        String gas = TypeConverter.toQuantityJsonHex(invoke.getGas());
        String input = TypeConverter.toUnformattedJsonHex(invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize()));
        String callType = "call";
        String value;

        DataWord callValue = invoke.getCallValue();

        if (callValue.isZero())
            value = "0x";
        else
            value = TypeConverter.toQuantityJsonHex(callValue.getData());

        return new ActionTransactionTrace(
                callType,
                from,
                to,
                gas,
                input,
                value
        );
    }
}
