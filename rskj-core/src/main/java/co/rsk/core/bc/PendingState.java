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
import java.util.stream.Collectors;

import static org.ethereum.util.BIUtil.toBI;

public class PendingState implements AccountInformationProvider {

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

    // sortByPriceTakingIntoAccountSenderAndNonce sorts the transactions by price, but
    // first clustering by sender and then each cluster is order by nonce.
    //
    // This method first sorts list of getPendingTransactions into individual
    // sender and sorts those lists by nonce. After the nonce ordering is
    // satisfied, the results are merged back together by price, always comparing only
    // the head transaction from each sender, in this way we keep the sender and nonce ordering
    // for each individual list.
    // To order the price we use a heap to keep it fast.

    //Note that this sort doesn't return the best solution, it is an approximation algorithm to find approximate
    // solution. (No trivial solution)
    public static final List<Transaction> sortByPriceTakingIntoAccountSenderAndNonce(List<Transaction> transactions) {

        long txsCount = transactions.size();

        Map<RskAddress, List<Transaction>> mapSenderToTxs = new HashMap();

        //First create a map to separate txs by each sender.
        mapSenderToTxs = transactions.stream().collect(Collectors.groupingBy(Transaction::getSender));

        //For each sender list order all txs by nonce and then by hash
        for (Map.Entry<RskAddress, List<Transaction>> entry : mapSenderToTxs.entrySet()) {
            entry.getValue().sort(Comparator.<Transaction>comparingLong(tx -> ByteUtil.byteArrayToLong(tx.getNonce()))
                    .thenComparing(Transaction::getHash));
        }

        //Priority heap is ordered according this comparator.
        Comparator<Transaction> comp = (Transaction tx1, Transaction tx2) -> (tx2.getGasPrice().compareTo(tx1.getGasPrice()));

        PriorityQueue<Transaction> treeTxs = new PriorityQueue(comp);

        List<Transaction> retOrderTxs = new ArrayList();

        //Add every head of each list of sender's transactions to the heap
        for (Map.Entry<RskAddress, List<Transaction>> entry : mapSenderToTxs.entrySet())
        {
            treeTxs.add(entry.getValue().get(0));
            entry.getValue().remove(0);
        }

        //Get in each iteration the max price (head) from the heap.
        while (txsCount > 0) {
            Transaction nextTxToAdd = treeTxs.poll();
            retOrderTxs.add(nextTxToAdd);
            if(!mapSenderToTxs.get(nextTxToAdd.getSender()).isEmpty()){
                treeTxs.add(mapSenderToTxs.get(nextTxToAdd.getSender()).get(0));
                mapSenderToTxs.get(nextTxToAdd.getSender()).remove(0);
            }
            txsCount--;
        }

        return retOrderTxs;
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
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
    }

    private interface PostExecutionAction<T> {
        T execute(Repository executedRepository);
    }
}
