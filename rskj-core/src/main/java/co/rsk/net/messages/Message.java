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

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

/**
 * Created by ajlopez on 5/10/2016.
 */
public abstract class Message {

    public abstract MessageType getMessageType();

    public abstract byte[] getEncodedMessage();

    public abstract void accept(MessageVisitor v);

    public final byte[] getEncoded() {
        byte[] type = RLP.encodeByte(getMessageType().getTypeAsByte());
        byte[] body = RLP.encodeElement(this.getEncodedMessage());
        return RLP.encodeList(type, body);
    }

    @VisibleForTesting
    static Message create(BlockFactory blockFactory, byte[] encoded) {
        return create(blockFactory, (RLPList) RLP.decode2(encoded).get(0), false);
    }

    public static Message create(BlockFactory blockFactory, RLPList paramsList, boolean isVersioningEnabled) {
        byte[] body = paramsList.get(1).getRLPData();

        if (body != null) {
            int type = paramsList.get(0).getRLPData()[0];
            MessageType messageType = MessageType.valueOfType(type);
            RLPList list = (RLPList) RLP.decode2(body).get(0);

            if (isVersioningEnabled) {
                return messageType.createVersionedMessage(blockFactory, list);
            }

            return messageType.createMessage(blockFactory, list);
        }

        return null;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + getMessageType() +
                '}';
    }
}
