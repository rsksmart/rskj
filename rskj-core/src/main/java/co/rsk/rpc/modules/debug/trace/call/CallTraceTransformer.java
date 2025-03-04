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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.InvokeData;
import org.ethereum.vm.trace.SummarizedProgramTrace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallTraceTransformer {

    private CallTraceTransformer() {
    }

    public static TransactionTrace toTrace(SummarizedProgramTrace trace, TransactionInfo txInfo, DataWord codeAddress, boolean onlyTopCall, boolean withLog) {
        boolean isContractCreation = txInfo.getReceipt().getTransaction().isContractCreation();
        CallType callType = isContractCreation ? CallType.NONE : CallType.CALL;

        ProgramResult programResult = ProgramResult.empty();
        programResult.spendGas(new BigInteger(1, txInfo.getReceipt().getGasUsed()).longValue());

        if (trace.getReverted()) {
            programResult.setRevert();
        }
        if (!StringUtils.isEmpty(trace.getError())) {
            programResult.setException(new Exception(trace.getError()));
        }

        if (withLog) {
            programResult.addLogInfos(txInfo.getReceipt().getLogInfoList());
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

        TxTraceResult traceOutput = toTrace(traceType, callType, invoke, codeAddress, programResult, creationData, withLog);
        if (!onlyTopCall) {
            for (ProgramSubtrace subtrace : trace.getSubtraces()) {
                traceOutput.addCall(toTrace(subtrace, withLog));
            }
        }

        return new TransactionTrace(txInfo.getReceipt().getTransaction().getHash().toJsonString(), traceOutput);
    }

    private static TxTraceResult toTrace(ProgramSubtrace programSubtrace, boolean withLog) {
        InvokeData invokeData = programSubtrace.getInvokeData();
        TxTraceResult subTrace = toTrace(programSubtrace.getTraceType(), programSubtrace.getCallType(), invokeData, programSubtrace.getCodeAddress(), programSubtrace.getProgramResult(), programSubtrace.getCreationData(), withLog);
        for (ProgramSubtrace call : programSubtrace.getSubtraces()) {
            subTrace.addCall(toTrace(call, withLog));
        }
        return subTrace;
    }

    private static TxTraceResult toTrace(TraceType traceType, CallType callType, InvokeData invoke, DataWord codeAddress, ProgramResult programResult, CreationData creationData, boolean withLog) {
        TxTraceResult.Builder builder = TxTraceResult.builder();

        builder.type(traceType == TraceType.CREATE ? "CREATE" : callType.name());
        builder.from(getFrom(callType, invoke));

        if (traceType == TraceType.CREATE) {
            buildForCreate(builder, invoke, creationData);
        } else if (traceType == TraceType.CALL) {
            buildForCall(builder, invoke, callType, codeAddress);
        }

        if (programResult != null) {
            buildForProgramResult(builder, traceType, programResult);
        }

        if (withLog) {
            List<LogInfoResult> logInfoResultList = getLogs(programResult);
            builder.logs(logInfoResultList);
        }

        return builder.build();
    }

    private static String getFrom(CallType callType, InvokeData invoke) {
        if (callType == CallType.DELEGATECALL) {
            return new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        } else {
            return new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        }
    }

    private static void buildForCreate(TxTraceResult.Builder builder, InvokeData invoke, CreationData creationData) {
        DataWord callValue = invoke.getCallValue();

        if (creationData != null) {
            builder.input(HexUtils.toUnformattedJsonHex(creationData.getCreationInput()));
            builder.output(creationData.getCreatedAddress().toJsonString());
        }
        builder.value(HexUtils.toQuantityJsonHex(callValue.getData()));
        builder.gas(HexUtils.toQuantityJsonHex(invoke.getGas()));
    }

    private static void buildForCall(TxTraceResult.Builder builder, InvokeData invoke, CallType callType, DataWord codeAddress) {
        DataWord callValue = invoke.getCallValue();

        builder.input(HexUtils.toUnformattedJsonHex(invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize())));
        builder.value(HexUtils.toQuantityJsonHex(callValue.getData()));

        if (callType == CallType.DELEGATECALL) {
            // The code address should not be null in a DELEGATECALL case
            // but handling the case here
            if (codeAddress != null) {
                builder.to(new RskAddress(codeAddress.getLast20Bytes()).toJsonString());
            }
        } else {
            builder.to(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString());
        }

        builder.gas(HexUtils.toQuantityJsonHex(invoke.getGas()));
    }

    private static void buildForProgramResult(TxTraceResult.Builder builder, TraceType traceType, ProgramResult programResult) {
        builder.gasUsed(HexUtils.toQuantityJsonHex(programResult.getGasUsed()));

        if (programResult.isRevert()) {
            Pair<String, byte[]> programRevert = EthModule.decodeProgramRevert(programResult);
            builder.revertReason(programRevert.getLeft());
            builder.output(HexUtils.toQuantityJsonHex(programRevert.getRight()));
            builder.error("execution reverted");
        } else if (traceType != TraceType.CREATE) {
            builder.output(HexUtils.toQuantityJsonHex(programResult.getHReturn()));
        }

        if (programResult.getException() != null) {
            builder.error(programResult.getException().getMessage());
        }
    }

    private static List<LogInfoResult> getLogs(ProgramResult programResult) {
        if (programResult == null) {
            return Collections.emptyList();
        }
        List<LogInfoResult> logInfoResultList = new ArrayList<>();
        List<LogInfo> logInfoList = programResult.getLogInfoList();
        if (logInfoList != null) {
            for (int i = 0; i < programResult.getLogInfoList().size(); i++) {
                LogInfo logInfo = programResult.getLogInfoList().get(i);
                LogInfoResult logInfoResult = fromLogInfo(logInfo);
                logInfoResultList.add(logInfoResult);
            }
        }
        return logInfoResultList;
    }

    private static LogInfoResult fromLogInfo(LogInfo logInfo, int index) {
        String address = HexUtils.toJsonHex(logInfo.getAddress());
        List<String> topics = logInfo.getTopics().stream().map(DataWord::getData).map(HexUtils::toJsonHex).toList();
        String data = HexUtils.toJsonHex(logInfo.getData());
        return LogInfoResult.builder()
                .index(index)
                .address(address)
                .topics(topics)
                .data(data)
                .build();
    }
    private static LogInfoResult fromLogInfo(LogInfo logInfo) {
        String address = HexUtils.toJsonHex(logInfo.getAddress());
        List<String> topics = logInfo.getTopics().stream().map(DataWord::getData).map(HexUtils::toJsonHex).toList();
        String data = HexUtils.toJsonHex(logInfo.getData());
        return LogInfoResult.builder()
                .index(logInfo.getLogIndex())
                .address(address)
                .topics(topics)
                .data(data)
                .build();
    }
}
