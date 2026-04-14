/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
 *
 * <h3>Encoding contexts for typed transactions (RSKIP-543 / EIP-2718)</h3>
 *
 * A typed transaction's <em>canonical</em> form is {@code type || payload} (one or
 * more prefix bytes concatenated with the RLP-encoded fields).  This is the form
 * returned by {@link Transaction#getEncoded()} and used for hashing and signing.
 *
 * <p>However, a block body's transaction list is itself an RLP list:
 * {@code rlp([tx0, tx1, …])}.  Legacy transactions are already RLP lists
 * (first byte &ge; {@code 0xc0}), so they nest naturally.  A typed transaction
 * starts with a byte &lt; {@code 0x80}, which the RLP decoder would misinterpret
 * as a short string rather than a list item.  To avoid ambiguity, EIP-2718
 * requires that typed transactions are <b>wrapped as RLP byte strings</b> when
 * stored inside an outer RLP list (block bodies, network messages).
 *
 * <p>In summary:
 * <ul>
 *   <li><b>Legacy tx in a list:</b> raw RLP list — included as-is.</li>
 *   <li><b>Typed tx in a list:</b> {@code rlp_string(type || payload)} — wrapped
 *       with {@link RLP#encodeElement(byte[])}.</li>
 * </ul>
 *
 * <p>On decode the reverse applies: {@link RLPElement#getRLPData()} unwraps the
 * byte-string envelope, returning the canonical {@code type || payload} bytes
 * that the {@link Transaction} constructor expects.
 */
public final class BlockBodyCodec {

    private BlockBodyCodec() {
    }

    public static byte[] encodeTransactions(List<Transaction> transactions) {
        byte[][] encoded = new byte[transactions.size()][];
        for (int i = 0; i < transactions.size(); i++) {
            encoded[i] = encodeTransaction(transactions.get(i));
        }
        return RLP.encodeList(encoded);
    }

    /**
     * Encodes a single transaction for inclusion in an RLP list context (block body
     * or network message).  Typed transactions are wrapped as RLP byte strings;
     * legacy transactions are returned as-is (they are already RLP lists).
     *
     * @see #decodeTransactions(RLPList) for the inverse operation
     */
    public static byte[] encodeTransaction(Transaction tx) {
        byte[] encoded = tx.getEncoded();
        if (tx.getTypePrefix().isTyped()) {
            return RLP.encodeElement(encoded);
        }
        return encoded;
    }

    /**
     * Decodes transactions from an RLP list (block body or network message).
     * Each element is unwrapped via {@link RLPElement#getRLPData()}, which strips
     * the RLP byte-string envelope for typed transactions, recovering the canonical
     * {@code type || payload} bytes.
     *
     * @see #encodeTransaction(Transaction) for the inverse operation
     */
    public static List<Transaction> decodeTransactions(RLPList transactionList) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < transactionList.size(); i++) {
            RLPElement transactionRaw = transactionList.get(i);
            byte[] rawData = transactionRaw.getRLPData();
            Transaction tx = new ImmutableTransaction(rawData);

            if (tx.isRemascTransaction(i, transactionList.size())) {
                validateRemascIsLegacy(tx);
                tx = new RemascTransaction(rawData);
            }
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
    }

    public static void validateRemascIsLegacy(Transaction tx) {
        if (tx.getTypePrefix().isTyped()) {
            throw new IllegalArgumentException("Remasc transaction must be legacy, but got typed transaction: " + tx.getTypePrefix().toFullString());
        }
    }

    public static byte[] encodeUncles(List<BlockHeader> uncleList) {
        byte[][] unclesEncoded = new byte[uncleList.size()][];
        for (int i = 0; i < uncleList.size(); i++) {
            unclesEncoded[i] = uncleList.get(i).getFullEncoded();
        }
        return RLP.encodeList(unclesEncoded);
    }
}
