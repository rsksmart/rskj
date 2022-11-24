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
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class GetBlockMessage extends MessageVersionAware {
    private int version;
    private byte[] hash;

    public GetBlockMessage(int version, byte[] hash) {
        this.version = version;
        this.hash = hash;
    }

    @VisibleForTesting
    public GetBlockMessage(byte[] hash) {
        this(MessageVersionValidator.DISABLED_VERSION, hash);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_BLOCK_MESSAGE;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public byte[] encodeWithoutVersion() {
        byte[] hash = RLP.encodeElement(this.hash);

        return RLP.encodeList(hash);
    }

    public byte[] getBlockHash() {
        return this.hash;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
