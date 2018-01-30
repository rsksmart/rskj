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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.commons.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.RepositoryTrack;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.ethereum.vm.trace.Serializers.serializeFieldsOnly;

public class ProgramTrace {

    private static final Logger LOGGER = LoggerFactory.getLogger("vm");

    private List<Op> ops = new LinkedList<>();
    private String result;
    private String error;
    private Map<String, String> initStorage = new HashMap<>();
    private boolean fullStorage;
    private int storageSize;
    private String contractAddress;

    public ProgramTrace(RskSystemProperties config) {
        this(config, null);
    }

    public ProgramTrace(RskSystemProperties config, ProgramInvoke programInvoke) {
        if (config.vmTrace() && programInvoke != null) {
            contractAddress = Hex.toHexString(programInvoke.getOwnerAddress().getLast20Bytes());

            ContractDetails contractDetails = getContractDetails(programInvoke);
            if (contractDetails == null) {
                storageSize = 0;
                fullStorage = true;
            } else {
                storageSize = contractDetails.getStorageSize();
                if (storageSize <= config.vmTraceInitStorageLimit()) {
                    fullStorage = true;

                    String address = toHexString(programInvoke.getOwnerAddress().getLast20Bytes());
                    for (Map.Entry<DataWord, DataWord> entry : contractDetails.getStorage().entrySet()) {
                        // TODO: solve NULL key/value storage problem
                        DataWord key = entry.getKey();
                        DataWord value = entry.getValue();
                        if (key == null || value == null) {
                            LOGGER.info("Null storage key/value: address[{}]" ,address);
                            continue;
                        }

                        initStorage.put(key.toString(), value.toString());
                    }

                    if (!initStorage.isEmpty()) {
                        LOGGER.info("{} entries loaded to transaction's initStorage", initStorage.size());
                    }
                }
            }
        }
    }

    private static ContractDetails getContractDetails(ProgramInvoke programInvoke) {
        Repository repository = programInvoke.getRepository();
        if (repository instanceof RepositoryTrack) {
            repository = ((RepositoryTrack) repository).getOriginRepository();
        }

        RskAddress address = new RskAddress(programInvoke.getOwnerAddress());
        return repository.getContractDetails(address);
    }

    public List<Op> getOps() {
        return ops;
    }

    public void setOps(List<Op> ops) {
        this.ops = ops;
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

    public Op addOp(byte code, int pc, int deep, long gas, OpActions actions) {
        Op op = new Op();
        op.setActions(actions);
        op.setCode(OpCode.code(code));
        op.setDeep(deep);
        op.setGas(gas);
        op.setPc(pc);

        ops.add(op);

        return op;
    }

    /**
     * Used for merging sub calls execution.
     */
    public void merge(ProgramTrace programTrace) {
        this.ops.addAll(programTrace.ops);
    }

    public String asJsonString(boolean formatted) {
        return serializeFieldsOnly(this, formatted);
    }

    @Override
    public String toString() {
        return asJsonString(true);
    }
}
