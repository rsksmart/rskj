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

import java.math.BigInteger;

/**
 * Created by Sebastian Sicardi on 22/10/2019.
 */

public class TransactionIndexResponseMessage extends MessageWithId {
    private long id;
    private long blockNumber;
    private byte[] blockHash;
    private long txIndex;

    public TransactionIndexResponseMessage(long id, long blockNumber, byte[] blockHash, long txIndex) {
        this.id = id;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash.clone();
        this.txIndex = txIndex;
    }

    @Override
    public MessageType getMessageType() { return MessageType.TRANSACTION_INDEX_RESPONSE_MESSAGE; }

    @Override
    public long getId() { return this.id; }

    public long getBlockNumber() { return blockNumber; }

    public byte[] getBlockHash() { return blockHash.clone(); }

    public long getTransactionIndex() { return txIndex; }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpTxIndex = RLP.encodeBigInteger((BigInteger.valueOf(this.txIndex)));

        return RLP.encodeList(rlpBlockNumber, rlpBlockHash, rlpTxIndex);
    }

    @Override
    public void accept(MessageVisitor v) {
//        v.apply(this);
    }
}
