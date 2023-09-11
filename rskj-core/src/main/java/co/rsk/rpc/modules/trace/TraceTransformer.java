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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.InvokeData;
import org.ethereum.vm.trace.SummarizedProgramTrace;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;

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
        byte[] creationInput = isContractCreation ? txInfo.getReceipt().getTransaction().getData() : null;

        ProgramResult programResult = ProgramResult.empty();
        programResult.spendGas(new BigInteger(1, txInfo.getReceipt().getGasUsed()).longValue());

        if (trace.getReverted()) {
            programResult.setRevert();
        }

        CreationData creationData = null;
        TraceType traceType = TraceType.CALL;

        if (isContractCreation) {
            String outputText = trace.getResult();
            byte[] createdCode = outputText == null ? new byte[0] : Hex.decode(outputText);
            RskAddress createdAddress = txInfo.getReceipt().getTransaction().getContractAddress();
            creationData = new CreationData(creationInput, createdCode, createdAddress);
            traceType = TraceType.CREATE;
        }

        int nsubtraces = trace.getSubtraces().size();

        traces.add(toTrace(traceType, trace.getInvokeData(), programResult, txInfo, blockNumber, traceAddress, callType, creationData, null, trace.getError(), nsubtraces, null));

        for (int k = 0; k < nsubtraces; k++) {
            addTrace(traces, trace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
        }
    }

    private static void addTrace(List<TransactionTrace> traces, ProgramSubtrace subtrace, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress) {
        traces.add(toTrace(subtrace.getTraceType(), subtrace.getInvokeData(), subtrace.getProgramResult(), txInfo, blockNumber, traceAddress, subtrace.getCallType(), subtrace.getCreationData(), subtrace.getCreationMethod(), null, subtrace.getSubtraces().size(), subtrace.getCodeAddress()));

        int nsubtraces = subtrace.getSubtraces().size();

        for (int k = 0; k < nsubtraces; k++) {
            addTrace(traces, subtrace.getSubtraces().get(k), txInfo, blockNumber, new TraceAddress(traceAddress, k));
        }
    }

    public static TransactionTrace toTrace(TraceType traceType, InvokeData invoke, ProgramResult programResult, TransactionInfo txInfo, long blockNumber, TraceAddress traceAddress, CallType callType, CreationData creationData, String creationMethod, String err, int nsubtraces, DataWord codeAddress) {
        TraceAction action = toAction(traceType, invoke, callType, creationData == null ? null : creationData.getCreationInput(), creationMethod, codeAddress);
        TraceResult result = null;
        String error = null;

        if (traceType != TraceType.SUICIDE) {
            result = toResult(programResult, creationData == null ? null : creationData.getCreatedCode(), creationData == null ? null : creationData.getCreatedAddress());

            error = err != null && err.isEmpty() ? null : err;

            if (programResult.getException() != null) {
                error = programResult.getException().toString();
            }
            else if (programResult.isRevert()) {
                error = "Reverted";
            }

            if (error != null) {
                result = null;
            }
        }

        String blockHash = HexUtils.toUnformattedJsonHex(txInfo.getBlockHash());
        String transactionHash = txInfo.getReceipt().getTransaction().getHash().toJsonString();
        int transactionPosition = txInfo.getIndex();
        String type = traceType.name().toLowerCase();

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
        String gasUsed = HexUtils.toQuantityJsonHex(programResult.getGasUsed());
        String output = null;
        String address = null;
        String code = null;

        if (createdCode != null && createdAddress != null) {
            code = HexUtils.toUnformattedJsonHex(createdCode);
            address = createdAddress.toJsonString();
        }
        else {
            output = HexUtils.toUnformattedJsonHex(programResult.getHReturn());
        }

        return new TraceResult(gasUsed, output, code, address);
    }

    public static TraceAction toAction(TraceType traceType, InvokeData invoke, CallType callType, byte[] creationInput, String creationMethod, DataWord codeAddress) {
        String from;

        if (callType == CallType.DELEGATECALL || callType == CallType.DELEGATECALL2) {
            from = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        }
        else {
            from = new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        }

        String to = null;
        String gas = null;

        DataWord callValue = invoke.getCallValue();

        String input = null;
        String value = null;
        String address = null;
        String refundAddress = null;
        String balance = null;
        String init = null;

        if (traceType == TraceType.CREATE) {
            init = HexUtils.toUnformattedJsonHex(creationInput);
            value = HexUtils.toQuantityJsonHex(callValue.getData());
            gas = HexUtils.toQuantityJsonHex(invoke.getGas());
        }

        if (traceType == TraceType.CALL) {
            input = HexUtils.toUnformattedJsonHex(invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize()));;
            value = HexUtils.toQuantityJsonHex(callValue.getData());

            if (callType == CallType.DELEGATECALL || callType == CallType.DELEGATECALL2) {
                // The code address should not be null in a DELEGATECALL case
                // but handling the case here
                if (codeAddress != null) {
                    to = new RskAddress(codeAddress.getLast20Bytes()).toJsonString();
                }
            }
            else {
                to = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
            }

            gas = HexUtils.toQuantityJsonHex(invoke.getGas());
        }

        if (traceType == TraceType.SUICIDE) {
            address = from;
            from = null;
            balance = HexUtils.toQuantityJsonHex(callValue.getData());
            refundAddress = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        }

        return new TraceAction(
                callType,
                from,
                to,
                gas,
                input,
                init,
                creationMethod,
                value,
                address,
                refundAddress,
                balance
        );
    }
}
