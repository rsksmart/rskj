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
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;

import java.math.BigInteger;

/**
 * Encapsulates the logic to execute a transaction in an
 * isolated environment (e.g. no persistent state changes).
 */
public class ReversibleTransactionExecutor {
    private final RepositoryLocator repositoryLocator;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final PrecompiledContracts precompiledContracts;

    public ReversibleTransactionExecutor(
            RepositoryLocator repositoryLocator,
            TransactionExecutorFactory transactionExecutorFactory,
            PrecompiledContracts precompiledContracts) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.precompiledContracts = precompiledContracts;
    }

    public ReversibleTransactionExecutor(
            RepositoryLocator repositoryLocator,
            TransactionExecutorFactory transactionExecutorFactory) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.precompiledContracts = null;
    }

    public TransactionExecutor estimateGas(Block executionBlock, RskAddress coinbase, byte[] gasPrice, byte[] gasLimit,
                                           byte[] toAddress, byte[] value, byte[] data, RskAddress fromAddress,
                                           RepositorySnapshot snapshot) {
        Repository track = snapshot.startTracking();
        UnsignedTransaction tx = unsignedTransaction(track, gasPrice, gasLimit, toAddress, value, data, fromAddress);
        TransactionExecutor executor = localCallExecutor(tx, track, executionBlock, coinbase, precompiledContracts);
        // setLocalCall also marks the transaction itself, which native contracts read to tell a local call apart.
        // An estimate simulates the transaction as it would run once mined, so the transaction is not a local call.
        tx.setLocalCallTransaction(false);

        executor.executeTransaction();

        return executor;
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
        return executeTransaction(
                repositoryLocator.snapshotAt(executionBlock.getHeader()),
                executionBlock,
                coinbase,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                fromAddress,
                precompiledContracts
        );
    }

    public ProgramResult executeTransaction(
            RepositorySnapshot snapshot,
            Block executionBlock,
            RskAddress coinbase,
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            RskAddress fromAddress) {
        return localCall(snapshot, executionBlock, coinbase, gasPrice, gasLimit, toAddress, value, data, fromAddress, precompiledContracts);
    }

    public ProgramResult executeTransaction(
            RepositorySnapshot snapshot,
            Block executionBlock,
            RskAddress coinbase,
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            RskAddress fromAddress,
            PrecompiledContracts precompiledContracts) {
        return localCall(snapshot, executionBlock, coinbase, gasPrice, gasLimit, toAddress, value, data, fromAddress, precompiledContracts);
    }

    private ProgramResult localCall(RepositorySnapshot snapshot, Block executionBlock, RskAddress coinbase,
                                    byte[] gasPrice, byte[] gasLimit, byte[] toAddress, byte[] value,
                                    byte[] data, RskAddress fromAddress, PrecompiledContracts precompiledContracts) {
        Repository track = snapshot.startTracking();
        UnsignedTransaction tx = unsignedTransaction(track, gasPrice, gasLimit, toAddress, value, data, fromAddress);
        TransactionExecutor executor = localCallExecutor(tx, track, executionBlock, coinbase, precompiledContracts);

        executor.executeTransaction();

        return executor.getResult();
    }

    private static UnsignedTransaction unsignedTransaction(Repository track, byte[] gasPrice, byte[] gasLimit,
                                                           byte[] toAddress, byte[] value, byte[] data,
                                                           RskAddress fromAddress) {
        byte[] nonce = track.getNonce(fromAddress).toByteArray();
        return new UnsignedTransaction(
                nonce,
                gasPrice,
                localCallGasLimit(gasLimit),
                toAddress,
                value,
                data,
                fromAddress
        );
    }

    /**
     * Reads a local call's gas limit as an unsigned number of any width and returns it in at most 8 bytes, capped at
     * Long.MAX_VALUE. Execution reads an 8-byte gas limit as a signed long and any wider one as Long.MAX_VALUE, so
     * without this an 8-byte value of 2^63 or more would be rejected as negative gas, and a zero-padded value would run
     * with far more gas than it states.
     */
    private static byte[] localCallGasLimit(byte[] gasLimit) {
        BigInteger cappedGasLimit = new BigInteger(1, gasLimit).min(BigInteger.valueOf(GasCost.MAX_GAS));
        return ByteUtil.longToBytesNoLeadZeroes(cappedGasLimit.longValue());
    }

    private TransactionExecutor localCallExecutor(UnsignedTransaction tx, Repository track, Block executionBlock,
                                                  RskAddress coinbase, PrecompiledContracts precompiledContracts) {
        return transactionExecutorFactory
                .newInstance(tx, 0, coinbase, track, executionBlock, 0, precompiledContracts)
                .setLocalCall(true);
    }

    private static class UnsignedTransaction extends Transaction {

        /**
         * A fixed public-only key returned by {@link #getKey()}: the generator point G, which is the public key of
         * private key 1. A simulated peg-out therefore logs this key's BTC address, not the sender's.
         */
        private static final ECKey PLACEHOLDER_KEY = ECKey.fromPublicOnly(ECKey.CURVE.getG());

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

        /**
         * A simulated transaction carries no signature, so there is no public key to recover from it.
         * Native contracts that need the sender's public key still have to run, for example the Bridge
         * deriving the BTC destination of a peg-out from it. They get a fixed placeholder key instead.
         * Which key is used does not change the gas, and a fixed key keeps repeated simulations identical.
         */
        @Override
        public ECKey getKey() {
            return PLACEHOLDER_KEY;
        }

        @Override
        public boolean hasAcceptableSignatureComponents() {
            // Unsigned on purpose, so there are no signature components to check.
            return true;
        }
    }
}
