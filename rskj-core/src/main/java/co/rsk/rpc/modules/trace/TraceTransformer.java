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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.trace.SummarizedProgramTrace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class TraceTransformer {
    private TraceTransformer() {

    }

    public static List<TransactionTrace> toTraces(SummarizedProgramTrace trace, TransactionInfo txInfo, long blockNumber) {
        List<TransactionTrace> traces = new ArrayList<>();

        addTrace(traces, trace, txInfo, blockNumber, new TraceAddress());

        return traces;
    }

    private static void addTrace(List<TransactionTrace> traces, SummarizedProgramTrace trace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        boolean isContractCreation = txInfo.getReceipt().getTransaction().isContractCreation();
        CallType callType = isContractCreation ? CallType.NONE : CallType.CALL;
        byte[] creationData = isContractCreation ? txInfo.getReceipt().getTransaction().getData() : null;

        ProgramResult programResult = ProgramResult.empty();
        programResult.spendGas(new BigInteger(1, txInfo.getReceipt().getGasUsed()).longValue());

        byte[] createdCode = null;
        RskAddress createdAddress = null;

        if (isContractCreation) {
            String outputText = trace.getResult();
            createdCode = Hex.decode(outputText);
            createdAddress = txInfo.getReceipt().getTransaction().getContractAddress();
        }

        int nsubtraces = trace.getSubtraces().size();

        traces.add(toTrace(trace.getProgramInvoke(), programResult, txInfo, blockNumber, traceAddress, callType, creationData, createdCode, createdAddress, trace.getError(), nsubtraces));

        for (int k = 0; k < nsubtraces; k++)
            addTrace(traces, trace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
    }

    private static void addTrace(List<TransactionTrace> traces, ProgramSubtrace subtrace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        traces.add(toTrace(subtrace.getProgramInvoke(), subtrace.getProgramResult(), txInfo, blockNumber, traceAddress, subtrace.getCallType(), subtrace.getCreationData(), subtrace.getCreatedCode(), subtrace.getCreatedAddress(), null, subtrace.getSubtraces().size()));

        int nsubtraces = subtrace.getSubtraces().size();

        for (int k = 0; k < nsubtraces; k++)
            addTrace(traces, subtrace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
    }

    public static TransactionTrace toTrace(ProgramInvoke invoke, ProgramResult programResult, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress, CallType callType, byte[] creationData, byte[] createdCode, RskAddress createdAddress, String err, int nsubtraces) {
        TraceAction action = toAction(invoke, callType, creationData);
        TraceResult result = toResult(programResult, createdCode, createdAddress);
        String blockHash = TypeConverter.toUnformattedJsonHex(txInfo.getBlockHash());
        String transactionHash = txInfo.getReceipt().getTransaction().getHash().toJsonString();
        int transactionPosition = txInfo.getIndex();
        String type = creationData == null ? "call" : "create";
        String error = err != null && err.isEmpty() ? null : err;

        if (programResult.getException() != null) {
            error = programResult.getException().toString();
        }
        else if (programResult.isRevert()) {
            error = "transaction reverted";
        }

        if (error != null) {
            result = null;
        }

        return new TransactionTrace(
                action,
                blockHash,
                blockNumber,
                transactionHash,
                transactionPosition,
                type,
                nsubtraces,
                traceAddress,
                result,
                error
        );
    }

    public static TraceResult toResult(ProgramResult programResult, byte[] createdCode, RskAddress createdAddress) {
        String gasUsed = TypeConverter.toQuantityJsonHex(programResult.getGasUsed());
        String output = null;
        String address = null;
        String code = null;

        if (createdCode != null && createdAddress != null) {
            code = TypeConverter.toUnformattedJsonHex(createdCode);
            address = createdAddress.toJsonString();
        }
        else {
            output = TypeConverter.toUnformattedJsonHex(programResult.getHReturn());
        }

        return new TraceResult(gasUsed, output, code, address);
    }

    public static TraceAction toAction(ProgramInvoke invoke, CallType callType, byte[] creationData) {
        String from = new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        String to = creationData == null ? new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString() : null;
        String gas = TypeConverter.toQuantityJsonHex(invoke.getGas());
        String input = TypeConverter.toUnformattedJsonHex(creationData == null ?  invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize()) : creationData);
        String value;

        DataWord callValue = invoke.getCallValue();

        value = TypeConverter.toQuantityJsonHex(callValue.getData());

        return new TraceAction(
                callType,
                from,
                to,
                gas,
                input,
                value
        );
    }
}
