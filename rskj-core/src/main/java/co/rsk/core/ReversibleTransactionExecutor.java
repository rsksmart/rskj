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
import org.ethereum.core.TransactionBuilder;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;

import java.util.List;

/**
 * Encapsulates the logic to execute a transaction in an
 * isolated environment (e.g. no persistent state changes).
 */
public class ReversibleTransactionExecutor {

    private final RepositoryLocator repositoryLocator;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final PrecompiledContracts precompiledContracts;

    public ReversibleTransactionExecutor(RepositoryLocator repositoryLocator, TransactionExecutorFactory transactionExecutorFactory, PrecompiledContracts precompiledContracts) {
        this.repositoryLocator = repositoryLocator;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.precompiledContracts = precompiledContracts;
    }

    /**
     * Estimates gas against the provided snapshot using the executor's
     * configured precompiled contracts.
     */
    public TransactionExecutor estimateGas(Block executionBlock, RskAddress coinbase, RepositorySnapshot snapshot,
                                           ReversibleTransactionParams params) {
        return reversibleExecution(snapshot, executionBlock, coinbase, precompiledContracts, params);
    }

    public ProgramResult executeTransactionAtBlock(
            Block executionBlock,
            RskAddress coinbase,
            ReversibleTransactionParams params) {
        RepositorySnapshot snapshot = repositoryLocator.snapshotAt(executionBlock.getHeader());
        return executeTransactionOnSnapshot(snapshot, executionBlock, coinbase, precompiledContracts, params);
    }

    public ProgramResult executeTransactionOnSnapshot(
            RepositorySnapshot snapshot,
            Block executionBlock,
            RskAddress coinbase,
            PrecompiledContracts precompiledContracts,
            ReversibleTransactionParams params) {
        return reversibleExecution(snapshot, executionBlock, coinbase, precompiledContracts, params).getResult();
    }

    public record ReversibleTransactionParams(
            byte[] gasPrice,
            byte[] gasLimit,
            byte[] toAddress,
            byte[] value,
            byte[] data,
            RskAddress fromAddress,
            List<SetCodeAuthorization> authorizationList,
            byte chainId,
            TransactionType type,
            byte[] accessListBytes,
            byte[] maxPriorityFeePerGas,
            byte[] maxFeePerGas
    ) {}

    private TransactionExecutor reversibleExecution(RepositorySnapshot snapshot, Block executionBlock, RskAddress coinbase,
                                                    PrecompiledContracts precompiledContracts,
                                                    ReversibleTransactionParams params) {
        Repository track = snapshot.startTracking();

        ReversibleTransaction tx = new ReversibleTransaction(track.getNonce(params.fromAddress()).toByteArray(), params);

        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(tx, 0, coinbase, track, executionBlock, 0, precompiledContracts)
                .setLocalCall(true);

        executor.executeTransaction();

        return executor;
    }

    private static class ReversibleTransaction extends Transaction {

        private ReversibleTransaction(byte[] nonce, ReversibleTransactionParams params) {
            this(buildReversibleTransaction(nonce, params), params.fromAddress());
        }

        /**
         * We don't call Transaction.fromCallArguments(...) directly because it would
         * reject a Type 2/4 call that omits the fee-cap fields, and it picks the transaction type
         * from the RPC call's type field, which callers often leave out or set wrong. So we
         * resolve the type ourselves and fill in the missing fee-cap defaults (see #applyFees)
         * before building.
         */
        private static Transaction buildReversibleTransaction(byte[] nonce, ReversibleTransactionParams params) {
            TransactionType type = params.type();
            Coin gasPriceCoin = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(params.gasPrice()));

            TransactionBuilder builder = Transaction.builder()
                    .type(type)
                    .isLocalCall(true)
                    .nonce(nonce)
                    .gasLimit(params.gasLimit())
                    .receiveAddress(params.toAddress())
                    .value(RLP.parseCoinNullZero(ByteUtil.cloneBytes(params.value())))
                    .data(params.data())
                    .chainId(params.chainId());

            applyAccessList(builder, type, params.accessListBytes());
            applyFees(builder, type, gasPriceCoin, params.maxPriorityFeePerGas(), params.maxFeePerGas());
            applyAuthorizationList(builder, type, params.authorizationList());

            return builder.build();
        }

        private static void applyAccessList(TransactionBuilder builder, TransactionType type, byte[] accessListBytes) {
            if (accessListBytes != null && (type == TransactionType.TYPE_1 || type == TransactionType.TYPE_2 || type == TransactionType.TYPE_4)) {
                builder.accessList(accessListBytes);
            }
        }

        /**
         * Type 2/4 transactions need both maxPriorityFeePerGas and maxFeePerGas set. If the caller
         * gave us one or both we use those; if not, the default is to reuse gasPrice for the
         * missing one.
         */
        private static void applyFees(TransactionBuilder builder, TransactionType type, Coin gasPriceCoin,
                                       byte[] maxPriorityFeePerGas, byte[] maxFeePerGas) {
            if (type == TransactionType.TYPE_2 || type == TransactionType.TYPE_4) {
                Coin priority = maxPriorityFeePerGas != null ? RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(maxPriorityFeePerGas)) : gasPriceCoin;
                Coin maxFee = maxFeePerGas != null ? RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(maxFeePerGas)) : gasPriceCoin;
                builder.maxPriorityFeePerGas(priority).maxFeePerGas(maxFee);
            } else {
                builder.gasPrice(gasPriceCoin);
            }
        }

        private static void applyAuthorizationList(TransactionBuilder builder, TransactionType type,
                                                     List<SetCodeAuthorization> authorizationList) {
            if (type == TransactionType.TYPE_4) {
                builder.authorizationList(authorizationList);
            }
        }

        private ReversibleTransaction(Transaction transaction, RskAddress fromAddress) {
            super(
                    transaction.getNonce(),
                    transaction.getGasPrice(),
                    transaction.getGasLimit(),
                    transaction.getReceiveAddress(),
                    transaction.getValue(),
                    transaction.getData(),
                    transaction.getChainId(),
                    transaction.isLocalCallTransaction(),
                    transaction.getTypePrefix(),
                    transaction.getAccessListBytes(),
                    transaction.getMaxPriorityFeePerGas(),
                    transaction.getMaxFeePerGas(),
                    transaction.getAuthorizationList()
            );
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
