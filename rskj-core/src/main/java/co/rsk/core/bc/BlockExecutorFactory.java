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

package co.rsk.core.bc;

import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.StateRootHandler;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.vm.trace.MemoryProgramTraceProcessor;

public class BlockExecutorFactory {
    private final ActivationConfig activationConfig;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final Repository repository;
    private final StateRootHandler stateRootHandler;

    public BlockExecutorFactory(
            ActivationConfig activationConfig,
            TransactionExecutorFactory transactionExecutorFactory,
            Repository repository,
            StateRootHandler stateRootHandler) {
        this.activationConfig = activationConfig;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.repository = repository;
        this.stateRootHandler = stateRootHandler;
    }

    public BlockExecutor build() {
        return new BlockExecutor(
                repository,
                transactionExecutorFactory,
                stateRootHandler,
                activationConfig
        );
    }

    public BlockExecutor buildForTrace(MemoryProgramTraceProcessor programTraceProcessor) {
        return new BlockExecutor(
                repository,
                transactionExecutorFactory.forTrace(programTraceProcessor),
                stateRootHandler,
                activationConfig
        );
    }
}
