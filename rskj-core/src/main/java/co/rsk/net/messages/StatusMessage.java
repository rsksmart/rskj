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

import co.rsk.net.Status;
import org.ethereum.util.RLP;

import java.math.BigInteger;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class StatusMessage extends Message {
    private Status status;

    public StatusMessage(Status status) {
        this.status = status;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.STATUS_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(status.getBestBlockNumber()));
        byte[] hash = RLP.encodeElement(status.getBestBlockHash());

        if (status.getBestBlockParentHash() == null)
            return RLP.encodeList(number, hash);

        byte[] parentHash = RLP.encodeElement(status.getBestBlockParentHash());
        byte[] totalDifficulty = RLP.encodeBigInteger(status.getTotalDifficulty());

        return RLP.encodeList(number, hash, parentHash, totalDifficulty);
    }

    public Status getStatus() {
        return this.status;
    }
}
