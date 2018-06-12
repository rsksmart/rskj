/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.util.BIUtil.toBI;

public class PendingState implements AccountInformationProvider {

    public static final Comparator<Transaction> TRANSACTION_COMPARATOR =
            Comparator.<Transaction>comparingLong(tx -> ByteUtil.byteArrayToLong(tx.getNonce()))
                    .thenComparing(Transaction::getHash);

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingState.class);

    private final Repository pendingRepository;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final TransactionSet pendingTransactions;
    private boolean executed = false;


    public PendingState(Repository repository, TransactionSet pendingTransactions, TransactionExecutorFactory transactionExecutorFactory) {
        this.pendingRepository = repository.startTracking();
        this.pendingTransactions = pendingTransactions;
        this.transactionExecutorFactory = transactionExecutorFactory;
    }

    @Override
    public Coin getBalance(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getBalance(addr));
    }

    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageValue(addr, key));
    }

    @Override
    public byte[] getCode(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getCode(addr));
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        BigInteger nextNonce = pendingRepository.getNonce(addr);

        for (Transaction tx : this.pendingTransactions.getTransactionsWithSender(addr)) {
            BigInteger txNonce = tx.getNonceAsInteger();

            if (txNonce.compareTo(nextNonce) >= 0) {
                nextNonce = txNonce.add(BigInteger.ONE);
            }
        }

        return nextNonce;
    }


    private <T> T postExecutionReturn(PostExecutionAction<T> action) {
        if (!executed) {
            executeTransactions(pendingRepository, pendingTransactions.getTransactions());
            executed = true;
        }
        return action.execute(pendingRepository);
    }

    private void executeTransactions(Repository currentRepository, List<Transaction> pendingTransactions) {
        pendingTransactions.stream()
            .sorted(TRANSACTION_COMPARATOR)
            .forEach(pendingTransaction -> executeTransaction(currentRepository, pendingTransaction));
    }

    private void executeTransaction(Repository currentRepository, Transaction tx) {
        LOGGER.trace("Apply pending state tx: {} {}", toBI(tx.getNonce()), tx.getHash());
        TransactionExecutor executor = transactionExecutorFactory.newInstance(currentRepository, tx);
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
    }

    private interface PostExecutionAction<T> {
        T execute(Repository executedRepository);
    }
}
