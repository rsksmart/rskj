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

package org.ethereum.net.eth.message;

import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around an Ethereum Transactions message on the network
 *
 * @see EthMessageCodes#TRANSACTIONS
 */
public class TransactionsMessage extends EthMessage {

    private List<Transaction> transactions;

    public TransactionsMessage(byte[] encoded) {
        super(encoded);
    }

    public TransactionsMessage(Transaction transaction) {
        this(new ArrayList<>());
        transactions.add(transaction);
    }

    public TransactionsMessage(List<Transaction> transactionList) {
        this.transactions = transactionList;
        parsed = true;
    }

    private void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        transactions = new ArrayList<>();
        for (int i = 0; i < paramsList.size(); ++i) {
            RLPList rlpTxData = (RLPList) paramsList.get(i);
            Transaction tx = new ImmutableTransaction(rlpTxData.getRLPData());
            transactions.add(tx);
        }
        parsed = true;
    }

    private void encode() {
        List<byte[]> encodedElements = new ArrayList<>();
        for (Transaction tx : transactions) {
            encodedElements.add(tx.getEncoded());
        }
        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);
        this.encoded = RLP.encodeList(encodedElementArray);
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }


    public List<Transaction> getTransactions() {
        if (!parsed) {
            parse();
        }
        return transactions;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.TRANSACTIONS;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    public String toString() {
        if (!parsed) {
            parse();
        }
        final StringBuilder sb = new StringBuilder();
        if (transactions.size() < 8) {
            for (Transaction transaction : transactions) {
                sb.append("\n   ").append(transaction);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                sb.append("\n   ").append(transactions.get(i));
            }
            sb.append("\n   ").append("[Skipped ").append(transactions.size() - 6).append(" transactions]");
            for (int i = transactions.size() - 3; i < transactions.size(); i++) {
                sb.append("\n   ").append(transactions.get(i));
            }
        }
        return "[" + getCommand().name() + " num:"
                + transactions.size() + " " + sb.toString() + "]";
    }
}