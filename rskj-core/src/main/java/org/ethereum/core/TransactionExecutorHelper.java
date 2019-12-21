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

package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

import java.util.List;
import java.util.Set;

public class TransactionExecutorHelper {
    private final VmConfig vmConfig;
    private final PrecompiledContracts precompiledContracts;
    private final Block executionBlock;
    private final BlockFactory blockFactory;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final ProgramInvokeFactory programInvokeFactory;
    private final ActivationConfig.ForBlock activations;
    private final Transaction tx;
    private final int txindex;
    private final Set<DataWord> deletedAccounts;

    public TransactionExecutorHelper(
            VmConfig vmConfig,
            PrecompiledContracts precompiledContracts,
            Block executionBlock,
            BlockFactory blockFactory,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            ProgramInvokeFactory programInvokeFactory,
            ActivationConfig.ForBlock activations,
            Transaction tx,
            int txindex,
            Set<DataWord> deletedAccounts) {
        this.vmConfig = vmConfig;
        this.precompiledContracts = precompiledContracts;
        this.executionBlock = executionBlock;
        this.blockFactory = blockFactory;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.programInvokeFactory = programInvokeFactory;
        this.activations = activations;
        this.tx = tx;
        this.txindex = txindex;
        this.deletedAccounts = deletedAccounts;
    }

    public PrecompiledContracts.PrecompiledContract getPrecompiledContract(RskAddress address) {
        return precompiledContracts.getContractForAddress(activations, DataWord.valueOf(address.getBytes()));
    }

    public void initializedPrecompiledContract(PrecompiledContracts.PrecompiledContract precompiledContract, List<LogInfo> logInfoList, Repository repository) {
        precompiledContract.init(this.tx, this.executionBlock, repository, this.blockStore, this.receiptStore, logInfoList);
    }

    public VM createVM() {
        return new VM(vmConfig, precompiledContracts);
    }

    public Program createProgram(byte[] code, Repository repository) {
        ProgramInvoke programInvoke = this.programInvokeFactory.createProgramInvoke(this.tx, this.txindex, this.executionBlock, repository, this.blockStore);
        return new Program(this.vmConfig, this.precompiledContracts, this.blockFactory, activations, code, programInvoke, this.tx, this.deletedAccounts);
    }
}
