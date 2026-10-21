/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

import java.util.List;

/**
 * Test-only precompiled contract. The first input byte selects the effects produced before it throws:
 * {@link #WRITE_STORAGE} stores {@link #STORAGE_VALUE} under {@link #STORAGE_KEY}, {@link #EMIT_LOG}
 * appends one log.
 * The remaining flags cover what is not a precompile failure: {@link #RETURN_ERROR_CODE} returns
 * {@link #ERROR_CODE_OUTPUT} instead of throwing, {@link #GAS_FOR_DATA_THROWS} makes the gas calculation
 * throw, and {@link #THROW_JVM_ERROR} throws a JVM error instead of an exception.
 */
class FailingTestPrecompile extends PrecompiledContracts.PrecompiledContract {

    static final RskAddress FAILING_PRECOMPILE_ADDR = new RskAddress("00000000000000000000000000000000010000ff");

    static final byte WRITE_STORAGE = 0x01;
    static final byte EMIT_LOG = 0x02;
    static final byte RETURN_ERROR_CODE = 0x04;
    static final byte GAS_FOR_DATA_THROWS = 0x08;
    static final byte THROW_JVM_ERROR = 0x10;

    static final byte[] ERROR_CODE_OUTPUT = DataWord.valueOf(-1).getData();

    static final DataWord STORAGE_KEY = DataWord.valueOf(7);
    static final DataWord STORAGE_VALUE = DataWord.valueOf(42);

    private Repository repository;
    private List<LogInfo> logs;

    FailingTestPrecompile() {
        super(FAILING_PRECOMPILE_ADDR);
    }

    @Override
    public void init(PrecompiledContractArgs args) {
        this.repository = args.getRepository();
        this.logs = args.getLogs();
    }

    @Override
    public long getGasForData(byte[] data) {
        if ((data[0] & GAS_FOR_DATA_THROWS) != 0) {
            throw new IllegalStateException("test gas for data failure");
        }
        return 1_000L;
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        byte flags = data[0];
        if ((flags & WRITE_STORAGE) != 0) {
            repository.addStorageRow(FAILING_PRECOMPILE_ADDR, STORAGE_KEY, STORAGE_VALUE);
        }
        if ((flags & EMIT_LOG) != 0) {
            logs.add(new LogInfo(FAILING_PRECOMPILE_ADDR.getBytes(), List.of(DataWord.valueOf(1)), new byte[]{0x2a}));
        }
        if ((flags & RETURN_ERROR_CODE) != 0) {
            return ERROR_CODE_OUTPUT;
        }
        if ((flags & THROW_JVM_ERROR) != 0) {
            throw new StackOverflowError("test precompile error");
        }
        throw new VMException("test precompile failure");
    }

    /**
     * The standard precompiled contracts plus a {@link FailingTestPrecompile} at {@link #FAILING_PRECOMPILE_ADDR}.
     */
    static class PrecompiledContractsWithFailingContract extends PrecompiledContracts {
        private final FailingTestPrecompile failingContract = new FailingTestPrecompile();

        PrecompiledContractsWithFailingContract(TestSystemProperties config, BlockTxSignatureCache signatureCache) {
            super(config, null, signatureCache);
        }

        @Override
        public PrecompiledContract getContractForAddress(ActivationConfig.ForBlock activations, DataWord address) {
            if (DataWord.valueOf(FAILING_PRECOMPILE_ADDR.getBytes()).equals(address)) {
                return failingContract;
            }
            return super.getContractForAddress(activations, address);
        }
    }
}
