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

        if (transaction instanceof RemascTransaction) {
            return RemascTransaction.REMASC_ADDRESS;
        }

        RskAddress address = addressesCache.get(transaction);
        if (address != null) {
            return address;
        }

        if (internalCache.containsTx(transaction)) {
            RskAddress sender = internalCache.getSender(transaction);
            addressesCache.put(transaction, sender);
            return sender;
        }

        return transaction.getSender();
    }

    @Override
    public boolean containsTx(Transaction transaction) {
        return addressesCache.containsKey(transaction);
    }

    @Override
    public void storeSender(Transaction transaction) {

        if (transaction instanceof RemascTransaction) {
            return;
        }

        RskAddress sender = addressesCache.get(transaction);

        if (sender != null) {
            return;
        }

        if (internalCache.containsTx(transaction)) {
            sender = internalCache.getSender(transaction);
            addressesCache.put(transaction, sender);
        }

        addressesCache.put(transaction, transaction.getSender());
    }
}
