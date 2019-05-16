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
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.trace.MemoryProgramTraceProcessor;
import org.ethereum.vm.trace.ProgramTraceProcessor;

public class TransactionExecutorFactory {
    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final BlockFactory blockFactory;
    private final ProgramInvokeFactory programInvokeFactory;
    private final PrecompiledContracts precompiledContracts;
    private final ProgramTraceProcessor programTraceProcessor;

    public TransactionExecutorFactory(
            RskSystemProperties config,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory,
            ProgramTraceProcessor programTraceProcessor) {
        this.config = config;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockFactory = blockFactory;
        this.programInvokeFactory = programInvokeFactory;
        this.programTraceProcessor = programTraceProcessor;
        this.precompiledContracts = new PrecompiledContracts(config);
    }

    /**
     * Returns a clone of this factory with the specified program trace processor,
     * which is used to debug transactions.
     */
    public TransactionExecutorFactory forTrace(MemoryProgramTraceProcessor programTraceProcessor) {
        return new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                programInvokeFactory,
                programTraceProcessor
        );
    }

    public TransactionExecutor newInstance(
            Transaction tx,
            int txindex,
            RskAddress coinbase,
            Repository track,
            Block block,
            long totalGasUsed) {
        return new TransactionExecutor(
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
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                precompiledContracts,
                programTraceProcessor
        );
    }
}
