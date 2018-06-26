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
import org.ethereum.core.BlockIdentifier;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.LinkedList;
import java.util.List;

public class NewBlockHashesTest {
    @Test
    public void getMessageType() {
        Block block = new BlockGenerator().getBlock(1);

        List<BlockIdentifier> blockIdentifierList = new LinkedList<>();
        blockIdentifierList.add(new BlockIdentifier(block.getHash().getBytes(), block.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifierList);
        Assert.assertEquals(MessageType.NEW_BLOCK_HASHES, message.getMessageType());
    }

    @Test
    public void getBlockIdentifier() {
        Block block = new BlockGenerator().getBlock(1);
        List<BlockIdentifier> blockIdentifierList = new LinkedList<>();
        blockIdentifierList.add(new BlockIdentifier(block.getHash().getBytes(), block.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifierList);
        List<BlockIdentifier> identifiers = message.getBlockIdentifiers();
        Assert.assertEquals(1, identifiers.size());
        Assert.assertEquals(blockIdentifierList.get(0).getNumber(), identifiers.get(0).getNumber());
        Assert.assertArrayEquals(blockIdentifierList.get(0).getHash(),identifiers.get(0).getHash());
    }
}
