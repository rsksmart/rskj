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

package co.rsk.core;

import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.vm.program.ProgramResult;

/**
 * Encapsulates the logic to execute a transaction in an
 * isolated environment (e.g. no persistent state changes).
 */
public class ReversibleTransactionExecutor {
    private final RepositoryLocator repositoryLocator;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public ReversibleTransactionExecutor(
            RepositoryLocator repositoryLocator,
            TransactionExecutorFactory transactionExecutorFactory) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
    }

    public TransactionExecutor estimateGas(Block executionBlock, RskAddress coinbase, byte[] gasPrice, byte[] gasLimit,
                                                                byte[] toAddress, byte[] value, byte[] data, RskAddress fromAddress) {
        return reversibleExecution(
                repositoryLocator.snapshotAt(executionBlock.getHeader()),
                executionBlock,
                coinbase,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                fromAddress
        );
    }

    public ProgramResult executeTransaction(
            Block executionBlock,
            RskAddress coinbase,
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            RskAddress fromAddress) {
        return executeTransaction_workaround(
                repositoryLocator.snapshotAt(executionBlock.getHeader()),
                executionBlock,
                coinbase,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                fromAddress
        );
    }



    @Deprecated
    public ProgramResult executeTransaction_workaround(
            RepositorySnapshot snapshot,
            Block executionBlock,
            RskAddress coinbase,
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            RskAddress fromAddress) {
        return reversibleExecution(snapshot, executionBlock, coinbase, gasPrice, gasLimit, toAddress, value, data, fromAddress).getResult();
    }

    private TransactionExecutor reversibleExecution(RepositorySnapshot snapshot, Block executionBlock, RskAddress coinbase,
                                                    byte[] gasPrice, byte[] gasLimit, byte[] toAddress, byte[] value,
                                                    byte[] data, RskAddress fromAddress) {
        Repository track = snapshot.startTracking();

        byte[] nonce = track.getNonce(fromAddress).toByteArray();
        UnsignedTransaction tx = new UnsignedTransaction(
                nonce,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                fromAddress
        );

        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(tx, 0, coinbase, track, executionBlock, 0)
                .setLocalCall(true);

        executor.executeTransaction();

        return executor;
    }

    private static class UnsignedTransaction extends Transaction {

        private UnsignedTransaction(
                byte[] nonce,
                byte[] gasPrice,
                byte[] gasLimit,
                byte[] receiveAddress,
                byte[] value,
                byte[] data,
                RskAddress fromAddress) {
            super(nonce, gasPrice, gasLimit, receiveAddress, value, data);
            this.sender = fromAddress;
        }

        @Override
        public boolean acceptTransactionSignature(byte chainId) {
            // We only allow executing unsigned transactions
            // in the context of a reversible transaction execution.
            return true;
        }
    }
}
