/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace.call;

import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.CreationData;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import co.rsk.rpc.modules.trace.TraceType;
import co.rsk.util.HexUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.InvokeData;
import org.ethereum.vm.trace.SummarizedProgramTrace;

import java.math.BigInteger;

public class CallTraceTransformer {

    private CallTraceTransformer() {
    }

    public static TransactionTrace toTrace(SummarizedProgramTrace trace, TransactionInfo txInfo, DataWord codeAddress, boolean onlyTopCall) {
        boolean isContractCreation = txInfo.getReceipt().getTransaction().isContractCreation();
        CallType callType = isContractCreation ? CallType.NONE : CallType.CALL;

        ProgramResult programResult = ProgramResult.empty();
        programResult.spendGas(new BigInteger(1, txInfo.getReceipt().getGasUsed()).longValue());

        if (trace.getReverted()) {
            programResult.setRevert();
        }
        InvokeData invoke = trace.getInvokeData();

        CreationData creationData = null;

        TraceType traceType = TraceType.CALL;

        if (isContractCreation) {
            String outputText = trace.getResult();
            byte[] createdCode = outputText == null ? new byte[0] : Hex.decode(outputText);
            RskAddress createdAddress = txInfo.getReceipt().getTransaction().getContractAddress();
            byte[] input = txInfo.getReceipt().getTransaction().getData();
            creationData = new CreationData(input, createdCode, createdAddress);
            traceType = TraceType.CREATE;
        }

        TxTraceResult traceOutput = toTrace(traceType, callType, invoke, codeAddress, programResult, creationData);
        if(!onlyTopCall) {
            for (ProgramSubtrace subtrace : trace.getSubtraces()) {
                traceOutput.addCall(toTrace(subtrace));
            }
        }

        return new TransactionTrace(txInfo.getReceipt().getTransaction().getHash().toHexString(), traceOutput);
    }

    private static TxTraceResult toTrace(ProgramSubtrace programSubtrace) {
        InvokeData invokeData = programSubtrace.getInvokeData();
        TxTraceResult subTrace = toTrace(programSubtrace.getTraceType(), programSubtrace.getCallType(), invokeData, programSubtrace.getCodeAddress(), programSubtrace.getProgramResult(), programSubtrace.getCreationData());
        for (ProgramSubtrace call : programSubtrace.getSubtraces()) {
            subTrace.addCall(toTrace(call));
        }
        return subTrace;
    }

    private static TxTraceResult toTrace(TraceType traceType, CallType callType, InvokeData invoke, DataWord codeAddress, ProgramResult programResult, CreationData creationData) {
        String type = traceType == TraceType.CREATE ? "CREATE" : callType.name();
        String from;
        if (callType == CallType.DELEGATECALL) {
            from = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        } else {
            from = new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        }
        String to = null;
        String gas = null;

        DataWord callValue = invoke.getCallValue();
        //TODO input data is missing in TransferInvoke
        String input = null;
        String value = null;
        String output = null;


        if (traceType == TraceType.CREATE) {
            if (creationData != null) {
                input = HexUtils.toJsonHex(creationData.getCreationInput());
                output = creationData.getCreatedAddress().toJsonString();
            }
            value = HexUtils.toQuantityJsonHex(callValue.getData());
            gas = HexUtils.toQuantityJsonHex(invoke.getGas());
        }

        if (traceType == TraceType.CALL) {
            input = HexUtils.toJsonHex(invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize()));
            value = HexUtils.toQuantityJsonHex(callValue.getData());

            if (callType == CallType.DELEGATECALL) {
                // The code address should not be null in a DELEGATECALL case
                // but handling the case here
                if (codeAddress != null) {
                    to = new RskAddress(codeAddress.getLast20Bytes()).toJsonString();
                }
            } else {
                to = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
            }

            gas = HexUtils.toQuantityJsonHex(invoke.getGas());

        }

        String gasUsed = null;

        String revertReason = null;


        String error = null;

        if (programResult != null) {
            gasUsed = HexUtils.toQuantityJsonHex(programResult.getGasUsed());

            if (programResult.isRevert()) {
                Pair<String, byte[]> programRevert = EthModule.decodeProgramRevert(programResult);
                revertReason = programRevert.getLeft();
                output = HexUtils.toJsonHex(programRevert.getRight());

                error = "execution reverted";
            } else if (traceType != TraceType.CREATE) {
                output = HexUtils.toJsonHex(programResult.getHReturn());
            }
            if (programResult.getException() != null) {
                error = programResult.getException().toString();
            }
        }

        return TxTraceResult.builder()
                .type(type)
                .from(from)
                .to(to)
                .value(value)
                .gas(gas)
                .input(input)
                .gasUsed(gasUsed)
                .output(output)
                .revertReason(revertReason)
                .error(error)
                .build();

    }

}
