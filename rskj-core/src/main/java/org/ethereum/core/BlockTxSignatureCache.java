package org.ethereum.core;

import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.MaxSizeHashMap;

import java.util.Map;

public class BlockTxSignatureCache implements ISignatureCache {

    private static final int MAX_CACHE_SIZE = 900;

    private final Map<Transaction, RskAddress> addressesCache;
    private ISignatureCache internalCache;

    public BlockTxSignatureCache(ISignatureCache internalCache) {
        this.internalCache = internalCache;
        addressesCache = new MaxSizeHashMap<>(MAX_CACHE_SIZE,false);
    }

    public RskAddress getSender(Transaction transaction) {

        RskAddress sender;

        if (transaction instanceof RemascTransaction) {
            return RemascTransaction.REMASC_ADDRESS;
        }

        RskAddress address = addressesCache.get(transaction);
        if (address != null) {
            return address;
        }

        if (internalCache.containsTx(transaction)) {
            sender = internalCache.getSender(transaction);
            addressesCache.put(transaction, sender);
        } else {
            sender = addressesCache.computeIfAbsent(transaction, Transaction::getSender);
        }

        return sender;
    }

     public boolean containsTx(Transaction transaction) {
        return addressesCache.containsKey(transaction);
    }
}
