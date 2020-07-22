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

package org.ethereum.vm.trace;

import co.rsk.config.VmConfig;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.Storage;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.toHexStringOrEmpty;
import static org.ethereum.vm.trace.Serializers.serializeFieldsOnly;

public class DetailedProgramTrace implements ProgramTrace {

    private static final Logger logger = LoggerFactory.getLogger("vm");

    private String contractAddress;
    private Map<String, String> initStorage = new HashMap<>();

    private List<Op> structLogs = new LinkedList<>();
    private String result;
    private String error;
    private boolean reverted;

    private int storageSize;

    private Map<String, String> currentStorage = new HashMap<>();

    @JsonIgnore
    private boolean fullStorage;

    @JsonIgnore
    private DataWord storageKey;

    @JsonIgnore
    private final ProgramInvoke programInvoke;

    @JsonIgnore
    private final List<ProgramSubtrace> subtraces = new ArrayList<>();

    public DetailedProgramTrace(VmConfig config, ProgramInvoke programInvoke) {
        this.programInvoke = programInvoke;

        if (config.vmTrace() && programInvoke != null) {
            contractAddress = ByteUtil.toHexString(programInvoke.getOwnerAddress().getLast20Bytes());

            AccountInformationProvider informationProvider = getInformationProvider(programInvoke);
            RskAddress ownerAddress = new RskAddress(programInvoke.getOwnerAddress());
            if (!informationProvider.isContract(ownerAddress)) {
                storageSize = 0;
                fullStorage = true;
            } else {
                storageSize = informationProvider.getStorageKeysCount(ownerAddress);
                if (storageSize <= config.vmTraceInitStorageLimit()) {
                    fullStorage = true;

                    String address = toHexStringOrEmpty(programInvoke.getOwnerAddress().getLast20Bytes());
                    Iterator<DataWord> keysIterator = informationProvider.getStorageKeys(ownerAddress);
                    while (keysIterator.hasNext()) {
                        // TODO: solve NULL key/value storage problem
                        DataWord key = keysIterator.next();
                        byte[] value = informationProvider.getStorageBytes(ownerAddress, key);
                        if (key == null || value == null) {
                            logger.info("Null storage key/value: address[{}]", address);
                            continue;
                        }

                        initStorage.put(key.toString(), DataWord.valueOf(value).toString());
                    }

                    if (!initStorage.isEmpty()) {
                        logger.info("{} entries loaded to transaction's initStorage", initStorage.size());
                    }
                }
            }

            saveCurrentStorage(initStorage);
        }
    }

    @Override
    public ProgramInvoke getProgramInvoke() {
        return this.programInvoke;
    }

    private void saveCurrentStorage(Map<String, String> storage) {
        this.currentStorage = new HashMap<>(storage);
    }
    
    public boolean isEmpty() {
        return contractAddress == null;
    }

    private static AccountInformationProvider getInformationProvider(ProgramInvoke programInvoke) {
        return programInvoke.getRepository();
    }

    public List<Op> getStructLogs() {
        return structLogs == null ? null : Collections.unmodifiableList(structLogs);
    }

    public void setStructLogs(List<Op> structLogs) {
        this.structLogs = structLogs == null ? null : Collections.unmodifiableList(structLogs);
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean getReverted() { return reverted; }

    public boolean isFullStorage() {
        return fullStorage;
    }

    public void setFullStorage(boolean fullStorage) {
        this.fullStorage = fullStorage;
    }

    public int getStorageSize() {
        return storageSize;
    }

    public void setStorageSize(int storageSize) {
        this.storageSize = storageSize;
    }

    public Map<String, String> getInitStorage() {
        return initStorage;
    }

    public void setInitStorage(Map<String, String> initStorage) {
        this.initStorage = initStorage;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public ProgramTrace result(byte[] result) {
        setResult(toHexStringOrEmpty(result));
        return this;
    }

    public ProgramTrace error(Exception error) {
        setError(error == null ? "" : format("%s: %s", error.getClass(), error.getMessage()));
        return this;
    }

    @Override
    public ProgramTrace revert(boolean reverted) {
        this.reverted = reverted;
        return this;
    }

    @Override
    public void saveGasCost(long gasCost) {
        structLogs.get(structLogs.size() - 1).setGasCost(gasCost);
    }

    @Override
    public Op addOp(byte code, int pc, int deep, long gas, Memory memory, Stack stack, Storage storage) {
        Op op = new Op();
        OpCode opcode = OpCode.code(code);
        op.setOp(opcode);
        op.setDepth(deep);
        op.setGas(gas);
        op.setPc(pc);

        op.setMemory(memory);
        op.setStack(stack);

        if (this.storageKey != null) {
            RskAddress currentAddress = new RskAddress(this.contractAddress);
            DataWord value = storage.getStorageValue(currentAddress, this.storageKey);

            if (value != null) {
                this.currentStorage = new HashMap<>(this.currentStorage);
                this.currentStorage.put(this.storageKey.toString(), value.toString());
            }
            else {
                this.currentStorage.remove(this.storageKey.toString());
            }

            this.storageSize = this.currentStorage.size();
            this.storageKey = null;
        }

        if (opcode == OpCode.SSTORE || opcode == OpCode.SLOAD) {
            this.storageKey = stack.peek();
        }

        op.setStorage(this.currentStorage);

        structLogs.add(op);

        return op;
    }

    @Override
    public void addSubTrace(ProgramSubtrace programSubTrace) {
        this.subtraces.add(programSubTrace);
    }

    @Override
    public List<ProgramSubtrace> getSubtraces() {
        return Collections.unmodifiableList(this.subtraces);
    }

    /**
     * Used for merging sub calls execution.
     */
    @Override
    public void merge(ProgramTrace programTrace) {
        this.structLogs.addAll(((DetailedProgramTrace)programTrace).structLogs);
    }

    public String asJsonString(boolean formatted) {
        return serializeFieldsOnly(this, formatted);
    }

    @Override
    public String toString() {
        return asJsonString(true);
    }
}
