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

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockHeadersMessageTest {
    @Test
    public void getMessageType() {
        BlockHeadersMessage message = new BlockHeadersMessage((byte[]) null);
        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());
    }

    @Test
    public void getBlockHeader() {
        Block block = new BlockGenerator().getBlock(1);
        BlockHeadersMessage message = new BlockHeadersMessage(block.getHeader());
        Assert.assertSame(block.getHeader(), message.getBlockHeaders().get(0));
    }

    @Test
    public void getBlockHeaders() {
        List<BlockHeader> blocks = new ArrayList<>();
        BlockGenerator blockGenerator = new BlockGenerator();

        for (int k = 0; k < 5; k++) {
            Block b = blockGenerator.getBlock(k);
            blocks.add(b.getHeader());
        }

        BlockHeadersMessage message = new BlockHeadersMessage(blocks);
        List<BlockHeader> mblocks = message.getBlockHeaders();

        Assert.assertEquals(mblocks.size(), blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            Assert.assertEquals(blocks.get(1).getHash(), mblocks.get(1).getHash());
        }
    }
}
