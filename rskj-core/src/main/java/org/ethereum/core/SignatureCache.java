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

import java.util.Map;

public abstract class SignatureCache {
    protected Map<Transaction, RskAddress> addressesCache;

    public abstract RskAddress getSender(Transaction transaction);

    public boolean containsTx(Transaction transaction) {
        return addressesCache.containsKey(transaction);
    }

    public abstract void storeSender(Transaction tx);

    protected boolean hasToComputeSender(Transaction transaction) {
        if (transaction instanceof RemascTransaction) {
            return false;
        }

        RskAddress sender = addressesCache.get(transaction);
        return sender == null;
    }
}
