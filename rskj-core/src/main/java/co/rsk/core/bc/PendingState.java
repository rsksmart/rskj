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
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositorySnapshot;
import co.rsk.rpc.modules.eth.getProof.StorageProof;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionSet;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;
import static org.ethereum.util.BIUtil.toBI;

public class PendingState implements AccountInformationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingState.class);

    private final Repository pendingRepository;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final TransactionSet pendingTransactions;
    private boolean executed = false;


    public PendingState(RepositorySnapshot repository, TransactionSet pendingTransactions, TransactionExecutorFactory transactionExecutorFactory) {
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
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageBytes(addr, key));
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageKeys(addr));
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageKeysCount(addr));
    }

    @Override
    public byte[] getCode(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getCode(addr));
    }

    @Override
    public boolean isContract(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.isContract(addr));
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        BigInteger nextNonce = pendingRepository.getNonce(addr);
        Optional<BigInteger> maxNonce = this.pendingTransactions.getTransactionsWithSender(addr).stream()
                .map(Transaction::getNonceAsInteger)
                .max(BigInteger::compareTo)
                .map(nonce -> nonce.add(BigInteger.ONE))
                .filter(nonce -> nonce.compareTo(nextNonce) >= 0);

        return maxNonce.orElse(nextNonce);
    }

    @Override
    public Keccak256 getStorageHash(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageHash(addr));
    }

    @Override
    public List<String> getAccountProof(RskAddress addr) {
        return postExecutionReturn(executedRepository -> executedRepository.getAccountProof(addr));
    }

    @Override
    public List<StorageProof> getStorageProof(RskAddress addr, List<DataWord> storageKeys) {
        return postExecutionReturn(executedRepository -> executedRepository.getStorageProof(addr, storageKeys));
    }

    // sortByPriceTakingIntoAccountSenderAndNonce sorts the transactions by price, but
    // first clustering by sender and then each cluster is order by nonce.
    //
    // This method first sorts list of getPendingTransactions into individual
    // sender and sorts those lists by nonce. After the nonce ordering is
    // satisfied, the results are merged back together by price, always comparing only
    // the head transaction from each sender, in this way we keep the sender and nonce ordering
    // for each individual list.
    // To order the price we use a heap to keep it fast.

    // Note that this sort doesn't return the best solution, it is an approximation algorithm to find approximate
    // solution. (No trivial solution)
    public static List<Transaction> sortByPriceTakingIntoAccountSenderAndNonce(List<Transaction> transactions) {

        //Priority heap, and list of transactions are ordered by descending gas price.
        Comparator<Transaction> gasPriceComparator = reverseOrder(Comparator.comparing(Transaction::getGasPrice));

        //First create a map to separate txs by each sender.
        Map<RskAddress, List<Transaction>> senderTxs = transactions.stream().collect(Collectors.groupingBy(Transaction::getSender));

        //For each sender, order all txs by nonce and then by hash,
        //finally we order by price in cases where nonce are equal, and then by hash to disambiguate
        for (List<Transaction> transactionList : senderTxs.values()) {
            transactionList.sort(
                    Comparator.<Transaction>comparingLong(tx -> ByteUtil.byteArrayToLong(tx.getNonce()))
                            .thenComparing(gasPriceComparator)
                            .thenComparing(Transaction::getHash)
            );
        }

        PriorityQueue<Transaction> candidateTxs = new PriorityQueue<>(gasPriceComparator);

        //Add the first transaction from each sender to the heap.
        //Notice that we never push two transaction from the same sender
        //to avoid losing nonce ordering
        senderTxs.values().forEach(x -> {
            Transaction tx = x.remove(0);
            candidateTxs.add(tx);
        });

        long txsCount = transactions.size();
        List<Transaction> sortedTxs = new ArrayList<>();
        //In each iteration we get the tx with max price (head) from the heap.
        while (txsCount > 0) {
            Transaction nextTxToAdd = candidateTxs.remove();
            sortedTxs.add(nextTxToAdd);
            List<Transaction> txs = senderTxs.get(nextTxToAdd.getSender());
            if (!txs.isEmpty()) {
                Transaction tx = txs.remove(0);
                candidateTxs.add(tx);
            }

            txsCount--;
        }

        return sortedTxs;
    }

    private <T> T postExecutionReturn(PostExecutionAction<T> action) {
        if (!executed) {
            executeTransactions(pendingRepository, pendingTransactions.getTransactions());
            executed = true;
        }
        return action.execute(pendingRepository);
    }

    private void executeTransactions(Repository currentRepository, List<Transaction> pendingTransactions) {

        PendingState.sortByPriceTakingIntoAccountSenderAndNonce(pendingTransactions)
                .forEach(pendingTransaction -> executeTransaction(currentRepository, pendingTransaction));
    }

    private void executeTransaction(Repository currentRepository, Transaction tx) {
        LOGGER.trace("Apply pending state tx: {} {}", toBI(tx.getNonce()), tx.getHash());

        TransactionExecutor executor = transactionExecutorFactory.newInstance(currentRepository, tx);

        executor.executeTransaction();
    }

    private interface PostExecutionAction<T> {
        T execute(Repository executedRepository);
    }

    public interface TransactionExecutorFactory {
        TransactionExecutor newInstance(Repository repository, Transaction tx);
    }
}
