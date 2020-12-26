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

import co.rsk.core.types.ints.Uint24;

import org.ethereum.vm.CallCreate;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;
import org.ethereum.db.ByteArrayWrapper;

import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 07.06.2014
 */
public class ProgramResult {

    private long gasUsed = 0L;
    /** #mish rent gas is collected at end of transaction. 
     * so rentgas "used" is more like an "estimate" of eventual cost, rather than definite spending. 
     * However, for clarity of thought, use the same terminology as for regular execution gas.
     * And even though it is not collected until EOT, it cannot go over the rentGas limit.. that's a OOrentG exception  
    */
    private long rentGasUsed = 0L;
    // #mish data returned from memory, program. can be empty, can be output from func call, even contract code to be written to trie
    private byte[] hReturn = EMPTY_BYTE_ARRAY;
    private RuntimeException exception;
    private boolean revert;

    // Important:
    // DataWord is used as a ByteArrayWrapper, because Java data Maps/Sets cannot distiguish duplicate
    // keys if the key is of type byte[].
    private Map<DataWord, byte[]> codeChanges; // #mish: used in TX exec finalization

    // Map of NEW trie node keys to the amount of rent collected.
    // For New nodes accounts, contract, storage cell (SET_SSTORE). 
    // rent paid timestamp for this group (end of TX) will be block execution timestamp + 6 months (advance)
    private Map<ByteArrayWrapper, Long> createdNodes;

    // map of trie node key to the amount of rent collected for nodes accessed 
    // by the transaction (value may or may not be modified by TX, e.g. SLOAD, RESET_SSTORE)
    // rent paid timestamp for this group (end of TX) will be block execution timestamp
    private Map<ByteArrayWrapper, Long> accessedNodes;     
    // #mish Set of selfdestruct i.e. suicide accounts, i.e. contracts (and all associated nodes)
    // todo: compute rent for deleted nodes?
    private Set<DataWord> deleteAccounts;
    private List<InternalTransaction> internalTransactions;
    private List<LogInfo> logInfoList;
    private long futureRefund = 0;  // e.g. for contract suicide refund // "future" is really just end of transaction
    
    // #mish: There is no "refund" policy in place for pre-paid rent gas (deletions/SSTORE clear already get refund)
    //private long futureRentGasRefund = 0;

    /*
     * for testing runs ,
     * call/create is not executed
     * but dummy recorded
     */
    private List<CallCreate> callCreateList;

    public void clearUsedGas() {
        gasUsed = 0;
    }

    public void clearUsedRentGas() {
        rentGasUsed = 0;
    }
  
    public void spendGas(long gas) {
        gasUsed = GasCost.add(gasUsed, gas);
    }

    public void spendRentGas(long rentGas) {
        rentGasUsed = GasCost.add(rentGasUsed, rentGas);
        //System.out.println("\nprogresult.spendrentgas ->" + rentGasUsed);
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

    public void refundRentGas(long rentGas) {
        rentGasUsed = GasCost.subtract(rentGasUsed, rentGas);
    }

    public void setHReturn(byte[] hReturn) {
        this.hReturn = hReturn;

    }

    public byte[] getHReturn() {
        return hReturn;
    }

    public RuntimeException getException() {
        return exception;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public long getRentGasUsed() {
        return rentGasUsed;
    }

    public void setException(RuntimeException exception) {
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

    // #mish tracking additions, updates and storage rent due status for trie ndoes
    public Map<ByteArrayWrapper, Long> getCreatedNodes() {
        if (createdNodes == null) {
            createdNodes = new HashMap<>();
        }
        return createdNodes;
    }

    public void addCreatedNode(ByteArrayWrapper nodeKey, long rentDue) {
        getCreatedNodes().put(nodeKey, rentDue);   //putifabsent not needed, just created
    }

    // add a set of new trie nodes 
    public void addCreatedNodes(Map<ByteArrayWrapper, Long> newNodes) {
        getCreatedNodes().putAll(newNodes);
    }

    // #mish nodes accessed (may or may not be modified)
    public Map<ByteArrayWrapper, Long> getAccessedNodes() {
        if (accessedNodes == null) {
            accessedNodes = new HashMap<>();
        }
        return accessedNodes;
    }

    public void addAccessedNode(ByteArrayWrapper nodeKey, long rentDue) {
        // #mish: for accessed, keep the FIRST read value, since outstanding rent computations 
        // are based on that. 
        getAccessedNodes().putIfAbsent(nodeKey, rentDue); //the if absent should not be needed, we should check beforehand
    }

    public void addAccessedNodes(Map<ByteArrayWrapper, Long> nodesAcc) {
        nodesAcc.forEach(getAccessedNodes()::putIfAbsent);
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
        if (createdNodes!=null) {
            createdNodes.clear();
        }
        if (accessedNodes!=null) {
            accessedNodes.clear();
        }
        resetFutureRefund();
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

    public void addCallCreate(byte[] data, byte[] destination, long gasLimit, long rentGasLimit, byte[] value) {
        getCallCreateList().add(new CallCreate(data, destination, gasLimit, rentGasLimit, value));
    }

    public List<InternalTransaction> getInternalTransactions() {
        if (internalTransactions == null) {
            internalTransactions = new ArrayList<>();
        }
        return internalTransactions;
    }
    
    // #mish returns an internalTX and also adds it to the set of progResult.internalTx 
    // * see program.addInternalTx references for instances of use (e.g. SUICIDE)
    public InternalTransaction addInternalTransaction(byte[] parentHash, int deep, byte[] nonce, DataWord gasPrice, DataWord gasLimit,
                                                      byte[] senderAddress, byte[] receiveAddress, byte[] value, byte[] data, String note) {
        InternalTransaction transaction = new InternalTransaction(parentHash, deep, getInternalTransactions().size(),
                                        nonce, gasPrice, gasLimit, senderAddress, receiveAddress, value, data, note);
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

    public void addFutureRefund(long gasValue) {
        futureRefund += gasValue;
    }

    public long getFutureRefund() {
        return futureRefund;
    }

    public void resetFutureRefund() {
        futureRefund = 0;
    }

    public void merge(ProgramResult another) {
        addInternalTransactions(another.getInternalTransactions());
        //merge the following only if TX not reverted and no expections
        if (another.getException() == null && !another.isRevert()) {
            addDeleteAccounts(another.getDeleteAccounts());
            addCreatedNodes(another.getCreatedNodes());
            addAccessedNodes(another.getAccessedNodes());
            addLogInfos(another.getLogInfoList());
            addFutureRefund(another.getFutureRefund());
        }
    }
    
    public static ProgramResult empty() {
        ProgramResult result = new ProgramResult();
        result.setHReturn(EMPTY_BYTE_ARRAY);
        return result;
    }
}
