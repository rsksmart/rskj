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
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ethereum.core.Repository;
import org.ethereum.db.RepositoryTrack;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.Memory;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.Storage;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.util.*;

import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.ethereum.vm.trace.Serializers.serializeFieldsOnly;

public class ProgramTrace {

    private static final Logger LOGGER = LoggerFactory.getLogger("vm");

    private String contractAddress;
    private Map<String, String> initStorage = new HashMap<>();

    private List<Op> structLogs = new LinkedList<>();
    private String result;
    private String error;

    private int storageSize;

    private Map<String, String> currentStorage = new HashMap<>();

    @JsonIgnore
    private boolean fullStorage;

    @JsonIgnore
    private DataWord storageKey;

    public ProgramTrace(VmConfig config, ProgramInvoke programInvoke) {
        if (config.vmTrace() && programInvoke != null) {
            contractAddress = Hex.toHexString(programInvoke.getOwnerAddress().getLast20Bytes());

            AccountInformationProvider informationProvider = getInformationProvider(programInvoke);
            RskAddress ownerAddress = new RskAddress(programInvoke.getOwnerAddress());
            if (!informationProvider.isContract(ownerAddress)) {
                storageSize = 0;
                fullStorage = true;
            } else {
                storageSize = informationProvider.getStorageKeysCount(ownerAddress);
                if (storageSize <= config.vmTraceInitStorageLimit()) {
                    fullStorage = true;

                    String address = toHexString(programInvoke.getOwnerAddress().getLast20Bytes());
                    Iterator<DataWord> keysIterator = informationProvider.getStorageKeys(ownerAddress);
                    while (keysIterator.hasNext()) {
                        // TODO: solve NULL key/value storage problem
                        DataWord key = keysIterator.next();
                        DataWord value = informationProvider.getStorageValue(ownerAddress, key);
                        if (key == null || value == null) {
                            LOGGER.info("Null storage key/value: address[{}]", address);
                            continue;
                        }

                        initStorage.put(key.toString(), value.toString());
                    }

                    if (!initStorage.isEmpty()) {
                        LOGGER.info("{} entries loaded to transaction's initStorage", initStorage.size());
                    }
                }
            }

            saveCurrentStorage(initStorage);
        }
    }

    private void saveCurrentStorage(Map<String, String> storage) {
        this.currentStorage = new HashMap<>(storage);
    }

    private static AccountInformationProvider getInformationProvider(ProgramInvoke programInvoke) {
        Repository repository = programInvoke.getRepository();
        if (repository instanceof RepositoryTrack) {
            repository = ((RepositoryTrack) repository).getOriginRepository();
        }
        return repository;
    }

    public List<Op> getStructLogs() {
        return structLogs;
    }

    public void setStructLogs(List<Op> structLogs) {
        this.structLogs = structLogs;
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
        setResult(toHexString(result));
        return this;
    }

    public ProgramTrace error(Exception error) {
        setError(error == null ? "" : format("%s: %s", error.getClass(), error.getMessage()));
        return this;
    }

    public void saveGasCost(long gasCost) {
        structLogs.get(structLogs.size() - 1).setGasCost(gasCost);
    }

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

    /**
     * Used for merging sub calls execution.
     */
    public void merge(ProgramTrace programTrace) {
        this.structLogs.addAll(programTrace.structLogs);
    }

    public String asJsonString(boolean formatted) {
        return serializeFieldsOnly(this, formatted);
    }

    @Override
    public String toString() {
        return asJsonString(true);
    }
}
