package org.ethereum.core;

import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.MaxSizeHashMap;

import java.util.Map;

public class ReceivedTxSignatureCache {

    private static final int MAX_CACHE_SIZE = 6000; //Txs in three blocks

    private final Map<Transaction, RskAddress> addressesCache;

    public ReceivedTxSignatureCache() {
        addressesCache = new MaxSizeHashMap<>(MAX_CACHE_SIZE,true);
    }

    public RskAddress getSender(Transaction transaction) {

        if (transaction instanceof RemascTransaction) {
            return RemascTransaction.REMASC_ADDRESS;
        }

        return addressesCache.computeIfAbsent(transaction, Transaction::getSender);
    }
}
