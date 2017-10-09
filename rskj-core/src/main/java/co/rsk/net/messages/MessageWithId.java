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

import java.math.BigInteger;

public abstract class MessageWithId extends Message {

    @Override
    public byte[] getEncodedMessage() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        return RLP.encodeList(rlpId, getEncodedMessageWithoutId());
    }

    public abstract long getId();

    protected abstract byte[] getEncodedMessageWithoutId();

    public MessageType getResponseMessageType(){
        throw new AbstractMethodError("This method should be implemented by children (request Messages)");
    }

}
