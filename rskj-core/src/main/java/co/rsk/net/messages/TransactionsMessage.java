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

package co.rsk.net.messages;

import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 7/22/2016.
 */
public class TransactionsMessage extends Message {
    private List<Transaction> transactions;

    public TransactionsMessage(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public List<Transaction> getTransactions() {
        return this.transactions;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.TRANSACTIONS;
    }

    @Override
    public byte[] getEncodedMessage() {
        List<byte[]> encodedElements = new ArrayList<>();

        for (Transaction tx : transactions) {
            encodedElements.add(tx.getEncoded());
        }

        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);

        return RLP.encodeList(encodedElementArray);
    }

    public String getMessageContentInfo() {
        int size = CollectionUtils.size(this.transactions);
        StringBuilder sb = new StringBuilder(size).append(" Received.");

        if(size > 0) {
            sb.append(": ");
            this.getTransactions().forEach(tx -> sb.append(tx.getHash()).append(", "));
        }

        return sb.toString();
    }
}
