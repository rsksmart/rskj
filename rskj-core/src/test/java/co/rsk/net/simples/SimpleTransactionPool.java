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

package co.rsk.net.simples;

import org.ethereum.core.Block;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by usuario on 25/07/2016.
 */

public class SimpleTransactionPool implements TransactionPool {
    private List<Transaction> wireTransactions = new ArrayList<>();

    @Override
    public void start(Block initialBestBlock) {

    }

    @Override
    public List<Transaction> addTransactions(List<Transaction> transactions) {
        List<Transaction> newTxs = new ArrayList<>();

        for (Transaction tx : transactions)
            if (!this.wireTransactions.contains(tx)) {
                this.wireTransactions.add(tx);
                newTxs.add(tx);
            }

        return newTxs;
    }

    @Override
    public boolean addTransaction(Transaction tx) {
        return true;
    }

    @Override
    public void processBest(Block block) {

    }

    @Override
    public void clearPendingState(List<Transaction> txs) {

    }

    @Override
    public List<Transaction> getPendingTransactions() {
        return null;
    }

    @Override
    public List<Transaction> getQueuedTransactions() {
        return null;
    }

    @Override
    public Repository getRepository() {
        return null;
    }
}
