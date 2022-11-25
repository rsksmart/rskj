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

package co.rsk.net.eth;

import co.rsk.net.messages.Message;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class RskMessage extends EthMessage {
    private final int version;
    private Message message;

    public RskMessage(int version, Message message) {
        this.version = version;
        this.message = message;
        this.parsed = true;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.RSK_MESSAGE;
    }

    public Message getMessage() {
        return this.message;
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }

        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    protected void encode() {
        byte[] type = RLP.encodeByte(this.message.getMessageType().getTypeAsByte());
        byte[] body = RLP.encodeElement(this.message.getEncodedMessage());
        byte[] versionEncoded = RLP.encodeInt(this.version);
        byte[] msgEncoded = RLP.encodeList(type, body, versionEncoded);
        this.encoded = RLP.encodeList(msgEncoded);
    }

    @Override
    public String toString() {
        return "[" + this.getCommand().name() +
                " message=" + this.message +
                "]";
    }
}
