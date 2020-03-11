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

import org.ethereum.util.RLP;

public class CodeRequestMessage extends MessageWithId {
    private long id;
    private byte[] blockHash;
    private byte[] address;

    public CodeRequestMessage(long id, byte[] blockHash, byte[] address) {
        this.id = id;
        this.blockHash = blockHash.clone();
        this.address = address.clone();
    }

    @Override
    public long getId() {
        return this.id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public byte[] getAddress() {
        return address.clone();
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpCodeHash = RLP.encodeElement(this.address);
        return RLP.encodeList(rlpBlockHash, rlpCodeHash);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CODE_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.CODE_RESPONSE_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) {
//        v.apply(this);
    }
}
