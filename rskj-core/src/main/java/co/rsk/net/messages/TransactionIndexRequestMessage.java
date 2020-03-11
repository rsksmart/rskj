/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import org.ethereum.util.RLP;

/**
 * Created by Sebastian Sicardi on 22/10/2019.
 */

public class TransactionIndexRequestMessage extends MessageWithId {
    private byte[] txHash;
    private long id;

    public TransactionIndexRequestMessage(long id, byte[] hash) {
        this.txHash = hash.clone();
        this.id = id;
    }

    public byte[] getTransactionHash() {
        return txHash.clone();
    }

    @Override
    public MessageType getMessageType() { return MessageType.TRANSACTION_INDEX_REQUEST_MESSAGE; }

    @Override
    public MessageType getResponseMessageType() { return MessageType.TRANSACTION_INDEX_RESPONSE_MESSAGE; }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpHash = RLP.encodeElement(this.txHash);
        return RLP.encodeList(rlpHash);
    }

    @Override
    public void accept(MessageVisitor v) {
//        v.apply(this);
    }
}