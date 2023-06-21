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
package org.ethereum.vm.program;

import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.vm.CallCreate;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;

import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 07.06.2014
 */
public class ProgramResult {


    private byte[] hReturn = EMPTY_BYTE_ARRAY;
    private Exception exception;
    private boolean revert;

    // Important:
    // DataWord is used as a ByteArrayWrapper, because Java data Maps/Sets cannot distiguish duplicate
    // keys if the key is of type byte[].
    private Map<DataWord, byte[]> codeChanges;

    private Set<DataWord> deleteAccounts;
    private List<InternalTransaction> internalTransactions;
    private List<LogInfo> logInfoList;

    protected long gasUsed;
    private long futureRefund = 0;
    protected long deductedRefund = 0;

    private Map<Integer, Long> consumedAtCallDepth;

    /*
     * for testing runs ,
     * call/create is not executed
     * but dummy recorded
     */
    private List<CallCreate> callCreateList;

    // estimateGas fields
    private long maxGasUsed = 0; // sometimes the estimatedGas matches the maximum gasUsed
    private boolean movedRemainingGasToChild; // this will happen when there's no more gas left than expected from the child call
    private boolean callWithValuePerformed; // this will happen for VT CALLs
    private long gasUsedBeforeRefunds = 0; // this field it's useful to test if the deductedRefund value is less than the half of the gasUsed

    public void movedRemainingGasToChild(boolean moved) {
        this.movedRemainingGasToChild = moved;
    }

    public boolean getMovedRemainingGasToChild() {
        return movedRemainingGasToChild;
    }

    public void setCallWithValuePerformed(boolean callWithValuePerformed) {
        this.callWithValuePerformed = callWithValuePerformed;
    }

    public boolean isCallWithValuePerformed() {
        return callWithValuePerformed;
    }

    public void clearUsedGas() {
        gasUsed = 0;
    }

    public long getMaxGasUsed() {
        return maxGasUsed;
    }

    public void spendGas(long gas) {
        gasUsed = GasCost.add(gasUsed, gas);
        maxGasUsed = Math.max(gasUsed, maxGasUsed);
    }

    public void setRevert() {
        this.revert = true;
    }

    public boolean isRevert() {
        return revert;
    }

    public void refundGas(long gas) {
        gasUsed = GasCost.subtract(gasUsed, gas);
    }

    public void setHReturn(byte[] hReturn) {
        this.hReturn = hReturn;

    }

    public byte[] getHReturn() {
        return hReturn;
    }

    public Exception getException() {
        return exception;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }


    public Set<DataWord> getDeleteAccounts() {
        if (deleteAccounts == null) {
            deleteAccounts = new HashSet<>();
        }
        return deleteAccounts;
    }

    public Map<DataWord, byte[]> getCodeChanges() {
        if (codeChanges == null) {
            codeChanges = new HashMap<>();
        }
        return codeChanges;
    }

    public void addCodeChange(DataWord addr, byte[] newCode) {
        getCodeChanges().put(addr, newCode);
    }


    public void addDeleteAccount(DataWord address) {
        getDeleteAccounts().add(address);
    }

    public void addDeleteAccounts(Set<DataWord> accounts) {
        getDeleteAccounts().addAll(accounts);
    }

    public void clearFieldsOnException() {
        if (deleteAccounts!=null) {
            deleteAccounts.clear();
        }
        if (logInfoList!=null) {
            logInfoList.clear();
        }
        if (codeChanges!=null) {
            codeChanges.clear();
        }
        resetFutureRefund();
        resetDeductedRefund();
    }


    public List<LogInfo> getLogInfoList() {
        if (logInfoList == null) {
            logInfoList = new ArrayList<>();
        }
        return logInfoList;
    }

    public void addLogInfo(LogInfo logInfo) {
        getLogInfoList().add(logInfo);
    }

    public void addLogInfos(List<LogInfo> logInfos) {
        getLogInfoList().addAll(logInfos);
    }

    public List<CallCreate> getCallCreateList() {
        if (callCreateList == null) {
            callCreateList = new ArrayList<>();
        }
        return callCreateList;
    }

    public void addCallCreate(byte[] data, byte[] destination, long gasLimit, byte[] value) {
        getCallCreateList().add(new CallCreate(data, destination, gasLimit, value));
    }

    public List<InternalTransaction> getInternalTransactions() {
        if (internalTransactions == null) {
            internalTransactions = new ArrayList<>();
        }
        return internalTransactions;
    }

    public InternalTransaction addInternalTransaction(
        Transaction parentTransaction,
        int deep,
        byte[] nonce,
        DataWord gasPrice,
        DataWord gasLimit,
        byte[] senderAddress,
        byte[] receiveAddress,
        byte[] value,
        byte[] data,
        String note,
        SignatureCache signatureCache
    ) {
        byte[] parentHash = parentTransaction.getHash().getBytes();
        byte[] originHash = parentHash;
        if (parentTransaction instanceof InternalTransaction) {
            originHash = ((InternalTransaction) parentTransaction).getOriginHash();
        }
        InternalTransaction transaction = new InternalTransaction(
            originHash,
            parentHash,
            deep,
            getInternalTransactions().size(),
            nonce,
            gasPrice,
            gasLimit,
            senderAddress,
            receiveAddress,
            value,
            data,
            note,
            signatureCache
        );
        getInternalTransactions().add(transaction);
        return transaction;
    }

    public void addInternalTransactions(List<InternalTransaction> internalTransactions) {
        getInternalTransactions().addAll(internalTransactions);
    }

    public void rejectInternalTransactions() {
        for (InternalTransaction internalTx : getInternalTransactions()) {
            internalTx.reject();
        }
    }

    public void rejectLogInfos() {
        for (LogInfo logInfo : getLogInfoList()) {
            logInfo.reject();
        }
    }

    // This is the actual refunded amount of gas.
    // It should never be higher than half of the amount of gas consumed.
    public void addDeductedRefund(long gasValue) {
        deductedRefund = GasCost.add(deductedRefund,gasValue);
    }

    public long getDeductedRefund() {
        return deductedRefund;
    }

    public void resetDeductedRefund() {
        deductedRefund = 0;
    }

    // This is the maximum possible future Refund. This is NOT the actual amount
    // deducted, because this value is restricted by half of the consumed gas.
    public void addFutureRefund(long gasValue) {
        futureRefund = GasCost.add(futureRefund, gasValue);
    }

    public long getFutureRefund() {
        return futureRefund;
    }

    public void resetFutureRefund() {
        futureRefund = 0;
    }

    public void merge(ProgramResult another) {
        addInternalTransactions(another.getInternalTransactions());
        if (another.getException() == null && !another.isRevert()) {
            addDeleteAccounts(another.getDeleteAccounts());
            addLogInfos(another.getLogInfoList());
            addFutureRefund(another.getFutureRefund());
            addDeductedRefund(another.getDeductedRefund());
            this.maxGasUsed = Math.max(this.maxGasUsed, another.getMaxGasUsed());
            this.movedRemainingGasToChild = this.movedRemainingGasToChild || another.movedRemainingGasToChild;
            this.callWithValuePerformed = this.callWithValuePerformed || another.callWithValuePerformed;
        }
    }

    public static ProgramResult empty() {
        ProgramResult result = new ProgramResult();
        result.setHReturn(EMPTY_BYTE_ARRAY);
        return result;
    }

    public void setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
    }

    public List<LogInfo> logsFromNonRejectedTransactions() {
        return getLogInfoList().stream()
                .filter(logInfo -> !logInfo.isRejected())
                .collect(Collectors.toList());
    }

    public void setGasUsedBeforeRefunds(long gasUsedBeforeRefunds) {
        this.gasUsedBeforeRefunds = gasUsedBeforeRefunds;
    }

    public long getGasUsedBeforeRefunds() {
        return gasUsedBeforeRefunds;
    }

    Map<Integer, Long> getConsumedAtCallDepth() {
        return consumedAtCallDepth; // NOSONAR
    }

    public void initializeConsumedAtCallDepth() {
        this.consumedAtCallDepth = new MaxSizeHashMap<>(Program.MAX_CALL_DEPTH_RSKIP209, false);
    }

    void inheritConsumedAtCallDepth(Map<Integer, Long> consumedAtCallDepth) {
        this.consumedAtCallDepth = consumedAtCallDepth; // NOSONAR
    }

    public void updateCallDepthConsumption(int depth, long consumedGas) {
        Long oldValue = this.consumedAtCallDepth.get(depth);
        if (oldValue == null) {
            oldValue = 0L;
        }
        long newValue = GasCost.add(oldValue, consumedGas);
        this.consumedAtCallDepth.put(depth, newValue);
    }

    public long getEstimationIncrement() {
        if (this.consumedAtCallDepth == null) { // ie. RSKIP209 disabled
            return 0L;
        }

        final double f = 64.0 / 63;

        long increment = 0;
        double fLevel = f;
        for (int i = 1; i < this.consumedAtCallDepth.size(); i++) {
            long consumed = this.consumedAtCallDepth.get(i);
            long required = (long) Math.ceil(consumed * fLevel);
            long diff = required - consumed;
            increment += diff;
            fLevel *= f;
        }

        return increment;
    }
}
