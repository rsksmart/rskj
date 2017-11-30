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

package co.rsk.util;

import co.rsk.core.TouchedAccountsTracker;
import co.rsk.core.bc.BlockChainImpl;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

public class RskTransactionExecutor {
    private final Repository repository;
    private final BlockChainImpl blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    public RskTransactionExecutor(RskTestFactory factory) {
        this(factory.getRepository(), factory.getBlockchain(), factory.getBlockStore(), factory.getReceiptStore());
    }

    public RskTransactionExecutor(Repository repository,
                                  BlockChainImpl blockchain,
                                  BlockStore blockStore,
                                  ReceiptStore receiptStore) {
        this.blockchain = blockchain;
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
    }

    public TransactionExecutor executeTransaction(TouchedAccountsTracker touchedAccounts, Transaction transaction) {
        Repository track = repository.startTracking();
        TransactionExecutor executor = buildTransactionExecutor(touchedAccounts, transaction);
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
        return executor;
    }

    private TransactionExecutor buildTransactionExecutor(TouchedAccountsTracker touchedAccounts, Transaction transaction) {
        return new TransactionExecutor(transaction, new byte[32],
                repository, blockStore, receiptStore,
                new ProgramInvokeFactoryImpl(), blockchain.getBestBlock(), touchedAccounts);
    }
}
