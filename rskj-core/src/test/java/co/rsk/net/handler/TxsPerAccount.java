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
import org.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public class TxsPerAccount {

    private List<Transaction> txs = new LinkedList<>();
    private BigInteger nextNonce = null;

    boolean containsNonce(BigInteger nonce) {
        for (Transaction tx : txs) {
            if (new BigInteger(1, tx.getNonce()).equals(nonce)) {
                return true;
            }
        }
        return false;
    }

    public List<Transaction> getTransactions(){
        return txs;
    }

    public void setTransactions(List<Transaction> txs){
        this.txs = txs;
    }

    List<Transaction> readyToBeSent(BigInteger accountNonce) {
        if (nextNonce == null || nextNonce.compareTo(accountNonce) < 0) {
            nextNonce = accountNonce;
        }

        List<Transaction> ret = new LinkedList<>();
        for (Transaction tx : txs) {
            BigInteger nonce = new BigInteger(1, tx.getNonce());
            if (nextNonce.compareTo(nonce) == 0) {
                nextNonce = nonce.add(BigInteger.valueOf(1));
                ret.add(tx);
            }
        }

        return ret;
    }

    void removeNonce(BigInteger nonce) {
        List<Transaction> newlist = new LinkedList<>();

        for (Transaction tx : this.txs) {
            if (new BigInteger(1, tx.getNonce()).compareTo(nonce) == 0) {
                continue;
            }

            newlist.add(tx);
        }

        this.txs = newlist;

        if (newlist.isEmpty()) {
            this.nextNonce = null;
        }
    }

    @VisibleForTesting
    public BigInteger getNextNonce() {
        return this.nextNonce;
    }
}
