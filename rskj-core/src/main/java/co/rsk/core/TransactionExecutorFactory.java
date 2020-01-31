/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.core;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.VmConfig;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionExecutorFactory {

    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final BlockFactory blockFactory;
    private final ProgramInvokeFactory programInvokeFactory;
    private final PrecompiledContracts precompiledContracts;
    private final ExecutorService vmExecutorService;
    private BlockTxSignatureCache blockTxSignatureCache;

    public TransactionExecutorFactory(
            RskSystemProperties config,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory,
            PrecompiledContracts precompiledContracts,
            BlockTxSignatureCache blockTxSignatureCache) {
        this.config = config;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockFactory = blockFactory;
        this.programInvokeFactory = programInvokeFactory;
        this.precompiledContracts = precompiledContracts;
        this.vmExecutorService = Executors.newCachedThreadPool(threadFactory -> new Thread(
            Thread.currentThread().getThreadGroup(),
            threadFactory,
            "vmExecution",
            config.getVmExecutionStackSize()
        ));
        this.blockTxSignatureCache = blockTxSignatureCache;
    }

    public TransactionExecutor newInstance(
            Transaction tx,
            int txindex,
            RskAddress coinbase,
            Repository track,
            Block block,
            long totalGasUsed) {
        return newInstance(tx, txindex, coinbase, track, block, totalGasUsed, false, 0, new HashSet<>());
    }

    public TransactionExecutor newInstance(
            Transaction tx,
            int txindex,
            RskAddress coinbase,
            Repository track,
            Block block,
            long totalGasUsed,
            boolean vmTrace,
            int vmTraceOptions,
            Set<DataWord> deletedAccounts) {
        // Tracing configuration is scattered across different files (VM, DetailedProgramTrace, etc.) and
        // TransactionExecutor#extractTrace doesn't work when called independently.
        // It would be great to decouple from VmConfig#vmTrace, but sadly that's a major refactor we can't do now.
        VmConfig vmConfig = config.getVmConfig();
        if (vmTrace) {
            vmConfig = new VmConfig(
                    true,
                    vmTraceOptions,
                    vmConfig.vmTraceInitStorageLimit(),
                    vmConfig.dumpBlock(),
                    vmConfig.dumpStyle(),
                    vmConfig.getChainId()
            );
        }

        return new TransactionExecutor(
                config.getNetworkConstants(),
                config.getActivationConfig(),
                tx,
                txindex,
                coinbase,
                track,
                blockStore,
                receiptStore,
                blockFactory,
                programInvokeFactory,
                block,
                totalGasUsed,
                vmConfig,
                config.playVM(),
                config.isRemascEnabled(),
                precompiledContracts,
                deletedAccounts,
                vmExecutorService,
                blockTxSignatureCache
        );
    }
}
