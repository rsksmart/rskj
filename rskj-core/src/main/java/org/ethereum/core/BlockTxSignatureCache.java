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

public class BlockTxSignatureCache extends SignatureCache {

    private static final int MAX_CACHE_SIZE = 900;

    private final SignatureCache internalCache;

    public BlockTxSignatureCache(SignatureCache internalCache) {
        super(MAX_CACHE_SIZE, false);
        this.internalCache = internalCache;
    }

    @Override
    public synchronized RskAddress getSender(Transaction transaction) {

        if (transaction instanceof RemascTransaction) {
            return RemascTransaction.REMASC_ADDRESS;
        }

        RskAddress address = addressesCache.get(transaction.getHash());
        if (address != null) {
            return address;
        }

        RskAddress sender = internalCache.getSender(transaction);
        if (sender != null) {
            addressesCache.put(transaction.getHash(), sender);
            return sender;
        }

        return transaction.getSender();
    }

    @Override
    public synchronized void storeSender(Transaction transaction) {

        if (maySkipSenderStore(transaction)) {
            return;
        }

        RskAddress sender = internalCache.getSender(transaction);
        if (sender != null) {
            addressesCache.put(transaction.getHash(), sender);
        }

        addressesCache.put(transaction.getHash(), transaction.getSender());
    }
}
