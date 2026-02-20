/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import co.rsk.remasc.RemascTransaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encodes and decodes block-body transactions and uncle headers.
 * Typed transactions are wrapped as RLP elements when stored inside the
 * outer transaction list and unwrapped with {@code getRLPData()} on decode.
 */
public final class BlockTxCodec {

    private BlockTxCodec() {
    }
    public static byte[] encodeTransactions(List<Transaction> transactions) {
        byte[][] encoded = new byte[transactions.size()][];
        for (int i = 0; i < transactions.size(); i++) {
            encoded[i] = encodeTransaction(transactions.get(i));
        }
        return RLP.encodeList(encoded);
    }

    public static byte[] encodeTransaction(Transaction tx) {
        byte[] encoded = tx.getEncoded();
        if (tx.getTypePrefix().isTyped()) {
            return RLP.encodeElement(encoded);
        }
        return encoded;
    }

    public static List<Transaction> decodeTransactions(RLPList transactionList) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < transactionList.size(); i++) {
            RLPElement transactionRaw = transactionList.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (tx.isRemascTransaction(i, transactionList.size())) {
                tx = new RemascTransaction(transactionRaw.getRLPData());
            }
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
    }

    public static byte[] encodeUncles(List<BlockHeader> uncleList) {
        byte[][] unclesEncoded = new byte[uncleList.size()][];
        for (int i = 0; i < uncleList.size(); i++) {
            unclesEncoded[i] = uncleList.get(i).getFullEncoded();
        }
        return RLP.encodeList(unclesEncoded);
    }
}
