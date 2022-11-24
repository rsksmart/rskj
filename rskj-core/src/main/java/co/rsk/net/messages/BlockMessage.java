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
import org.ethereum.core.Block;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockMessage extends MessageVersionAware {
    private final int version;
    private Block block;

    public BlockMessage(int version, Block block) {
        this.version = version;
        this.block = block;
    }

    @VisibleForTesting
    public BlockMessage(Block block) {
        this(MessageVersionValidator.DISABLED_VERSION, block);
    }

    public Block getBlock() {
        return this.block;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_MESSAGE;
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public byte[] encodeWithoutVersion() {
        byte[] block = RLP.encode(this.block.getEncoded());

        return RLP.encodeList(block);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
