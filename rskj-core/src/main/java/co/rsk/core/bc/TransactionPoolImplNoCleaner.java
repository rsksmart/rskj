/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.config.RskSystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

public class TransactionPoolImplNoCleaner extends TransactionPoolImpl {
    public TransactionPoolImplNoCleaner(RskSystemProperties config,
                                        Repository repository,
                                        BlockStore blockStore,
                                        ReceiptStore receiptStore,
                                        ProgramInvokeFactory programInvokeFactory,
                                        EthereumListener listener,
                                        int outdatedThreshold,
                                        int outdatedTimeout) {
        super(config, repository, blockStore, receiptStore, programInvokeFactory, listener, outdatedThreshold, outdatedTimeout);
    }

    @Override
    public void start(Block initialBestBlock) {}

    @Override
    public void stop() {}
}
