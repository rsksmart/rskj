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

package co.rsk.net.handler;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.rpc.TypeConverter;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multiple TxHandlerImpl instances may cause inconsistencies and unexpected
 * behaviors.
 */
public class TxHandlerImpl implements TxHandler {

    private Repository repository;
    private Blockchain blockchain;
    private Map<String, TxTimestamp> knownTxs = new HashMap<>();
    private Lock knownTxsLock = new ReentrantLock();
    private Map<String, TxsPerAccount> txsPerAccounts = new HashMap<>();

    /**
     * This method will fork two `threads` and should not be instanced more than
     * once.
     *
     * As TxHandler will hold txs that weren't relayed or similar stuff, this
     * threads will help to keep memory low and consistency through all the
     * life of the application
     *
     * @param worldManager strongly depends on the worldManager
     * @param repository
     * @param blockchain
     */
    public TxHandlerImpl(WorldManager worldManager, org.ethereum.facade.Repository repository, Blockchain blockchain) {
        this.blockchain = blockchain;
        this.repository = (Repository) repository;

        // Clean old transactions every so seconds
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::cleanOldTxs, 120, 120, TimeUnit.SECONDS);

        // Clean txs on new block
        worldManager.addListener(new TxHandlerImpl.Listener());
    }

    @VisibleForTesting TxHandlerImpl() {
        // Only for testing
    }

    @Override
    public List<Transaction> retrieveValidTxs(List<Transaction> txs) {
        try {
            knownTxsLock.lock();
            return new TxValidator().filterTxs(repository, blockchain, txs, knownTxs, txsPerAccounts);
        } finally {
            knownTxsLock.unlock();
        }
    }

    @VisibleForTesting
    void cleanOldTxs() {
        try {
            knownTxsLock.lock();
            cleanTxs();
        } finally {
            knownTxsLock.unlock();
        }
    }

    private void cleanTxs() {
        final long oldTxThresholdInMS = (long)1000 * 60 * 5;
        Map<String, TxTimestamp> newKnownTxs = new HashMap<>();

        for (Map.Entry<String, TxTimestamp> entry : knownTxs.entrySet()) {
            long time = System.currentTimeMillis();
            TxTimestamp txt = entry.getValue();

            if (time - txt.timestamp > oldTxThresholdInMS) {
                String accountId = TypeConverter.toJsonHex(txt.tx.getSender());
                TxsPerAccount txsPerAccount = txsPerAccounts.get(accountId);

                if (txsPerAccount != null)
                {
                    txsPerAccount.removeNonce(new BigInteger(1, txt.tx.getNonce()));
                    if (txsPerAccount.getTransactions().isEmpty())
                        txsPerAccounts.remove(accountId);
                }

                continue;
            }

            newKnownTxs.put(entry.getKey(), entry.getValue());
        }

        knownTxs = newKnownTxs;
    }

    private class Listener extends EthereumListenerAdapter {

        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            try {
                knownTxsLock.lock();
                for (TransactionReceipt txReceipt : receipts) {
                    Transaction tx = txReceipt.getTransaction();
                    String txHash = TypeConverter.toJsonHex(tx.getHash());

                    if (!knownTxs.containsKey(txHash)) {
                        continue;
                    }

                    String accountId = TypeConverter.toJsonHex(tx.getSender());

                    TxsPerAccount txsPerAccount = txsPerAccounts.get(accountId);

                    if (txsPerAccount == null)
                    {
                        knownTxs.remove(txHash);
                        continue;
                    }

                    txsPerAccount.removeNonce(new BigInteger(1, tx.getNonce()));
                    if (txsPerAccount.getTransactions().isEmpty()) {
                        txsPerAccounts.remove(accountId);
                    }

                    knownTxs.remove(txHash);
                }
            }
            finally {
                knownTxsLock.unlock();
            }
        }
    }

    @VisibleForTesting void setKnownTxs(Map<String, TxTimestamp> knownTxs) { this.knownTxs = knownTxs; }
    @VisibleForTesting void setTxsPerAccounts(Map<String, TxsPerAccount> txsPerAccounts) { this.txsPerAccounts = txsPerAccounts; }
    @VisibleForTesting Map<String, TxTimestamp> getKnownTxs() { return knownTxs; }
    @VisibleForTesting Map<String, TxsPerAccount> getTxsPerAccounts() { return txsPerAccounts; }
    @VisibleForTesting public void onBlock(Block block, List<TransactionReceipt> receiptList) { new Listener().onBlock(block, receiptList); }

}
