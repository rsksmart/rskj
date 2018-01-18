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

import co.rsk.config.RskSystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

public final class ReversibleTransactionExecutor extends TransactionExecutor {

    private ReversibleTransactionExecutor(RskSystemProperties config, Transaction tx, byte[] coinbase, Repository track, BlockStore blockStore, ReceiptStore receiptStore, ProgramInvokeFactory programInvokeFactory, Block executionBlock) {
        super(config, tx, 0, coinbase, track, blockStore, receiptStore, programInvokeFactory, executionBlock);
        setLocalCall(true);
    }

    public static TransactionExecutor executeTransaction(RskSystemProperties config,
                                                         Repository track,
                                                         BlockStore blockStore,
                                                         ReceiptStore receiptStore,
                                                         ProgramInvokeFactory programInvokeFactory,
                                                         Block executionBlock,
                                                         byte[] coinbase,
                                                         byte[] gasPrice,
                                                         byte[] gasLimit,
                                                         byte[] toAddress,
                                                         byte[] value,
                                                         byte[] data,
                                                         byte[] fromAddress) {
        Repository repository = track.getSnapshotTo(executionBlock.getStateRoot()).startTracking();

        byte[] nonce = repository.getNonce(new RskAddress(fromAddress)).toByteArray();
        UnsignedTransaction tx = new UnsignedTransaction(nonce, gasPrice, gasLimit, toAddress, value, data, fromAddress);

        ReversibleTransactionExecutor executor = new ReversibleTransactionExecutor(config, tx, coinbase, repository, blockStore, receiptStore, programInvokeFactory, executionBlock);
        return executor.executeTransaction();
    }

    public static TransactionExecutor executeTransaction(RskSystemProperties config,
                                                         Repository track,
                                                         BlockStore blockStore,
                                                         ReceiptStore receiptStore,
                                                         ProgramInvokeFactory programInvokeFactory,
                                                         Block executionBlock,
                                                         byte[] coinbase,
                                                         Web3.CallArguments args) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);

        return executeTransaction(config, track, blockStore, receiptStore, programInvokeFactory, executionBlock, coinbase,
                hexArgs.getGasPrice(), hexArgs.getGasLimit(), hexArgs.getToAddress(), hexArgs.getValue(), hexArgs.getData(),
                hexArgs.getFromAddress());
    }

    private TransactionExecutor executeTransaction() {
        init();
        execute();
        go();
        finalization();
        return this;
    }

    private static class UnsignedTransaction extends Transaction {

        private UnsignedTransaction(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] fromAddress) {
            super(nonce, gasPrice, gasLimit, receiveAddress, value, data);
            this.sender = new RskAddress(fromAddress);
        }

        @Override
        public boolean acceptTransactionSignature(byte chainId) {
            // We only allow executing unsigned transactions
            // in the context of a reversible transaction execution.
            return true;
        }
    }
}
