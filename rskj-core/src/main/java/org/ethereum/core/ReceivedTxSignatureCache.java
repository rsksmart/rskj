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

public class ReceivedTxSignatureCache extends SignatureCache {

    private static final int MAX_CACHE_SIZE = 6000; //Txs in three blocks

    public ReceivedTxSignatureCache() {
        super(MAX_CACHE_SIZE, true);
    }

    public ReceivedTxSignatureCache(int size) {
        super(size, true);
    }

    @Override
    public synchronized RskAddress getSender(Transaction transaction) {

        if (transaction instanceof RemascTransaction) {
            return RemascTransaction.REMASC_ADDRESS;
        }

        RskAddress address = addressesCache.get(transaction.getHash());

        if (address == null) {
            return transaction.getSender();
        }

        return address;
    }

    @Override
    public synchronized void storeSender(Transaction transaction) {

        if (maySkipSenderStore(transaction)) {
            return;
        }

        addressesCache.put(transaction.getHash(), transaction.getSender());
    }
}
