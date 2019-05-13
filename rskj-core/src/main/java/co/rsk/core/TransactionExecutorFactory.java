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
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

public class TransactionExecutorFactory {
    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final BlockFactory blockFactory;
    private final ProgramInvokeFactory programInvokeFactory;
    private final EthereumListener listener;
    private final PrecompiledContracts precompiledContracts;

    public TransactionExecutorFactory(
            RskSystemProperties config,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory,
            EthereumListener listener) {
        this.config = config;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockFactory = blockFactory;
        this.programInvokeFactory = programInvokeFactory;
        this.listener = listener;
        this.precompiledContracts = new PrecompiledContracts(config);
    }

    /**
     * Returns a clone of this factory with the specified listener,
     * which is used in classes that do not impact the blockchain.
     */
    public TransactionExecutorFactory withFakeListener() {
        return new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                programInvokeFactory,
                new EthereumListenerAdapter()
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
                listener,
                totalGasUsed,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                precompiledContracts,
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        );
    }
}
