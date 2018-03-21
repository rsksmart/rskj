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
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the logic to execute a transaction in an
 * isolated environment (e.g. no persistent state changes).
 */
@Component
public class ReversibleTransactionExecutor {

    private final RskSystemProperties config;
    private final Repository track;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final ProgramInvokeFactory programInvokeFactory;

    @Autowired
    public ReversibleTransactionExecutor(
            RskSystemProperties config,
            Repository track,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            ProgramInvokeFactory programInvokeFactory) {
        this.config = config;
        this.track = track;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.programInvokeFactory = programInvokeFactory;
    }

    public ProgramResult executeTransaction(
            Block executionBlock,
            RskAddress coinbase,
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            byte[] fromAddress) {
        Repository repository = track.getSnapshotTo(executionBlock.getStateRoot()).startTracking();

        byte[] nonce = repository.getNonce(new RskAddress(fromAddress)).toByteArray();
        UnsignedTransaction tx = new UnsignedTransaction(
                nonce,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                fromAddress
        );

        TransactionExecutor executor = new TransactionExecutor(
                config,
                tx,
                0,
                coinbase,
                repository,
                blockStore,
                receiptStore,
                programInvokeFactory,
                executionBlock
        ).setLocalCall(true);

        executor.init();
        executor.execute();
        return executor.getResult();
    }

    private static class UnsignedTransaction extends Transaction {

        private UnsignedTransaction(
                byte[] nonce,
                byte[] gasPrice,
                byte[] gasLimit,
                byte[] receiveAddress,
                byte[] value,
                byte[] data,
                byte[] fromAddress) {
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
