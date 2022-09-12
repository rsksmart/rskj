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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;

class NewBlockHashesTest {
    @Test
    void getMessageType() {
        Block block = new BlockGenerator().getBlock(1);

        List<BlockIdentifier> blockIdentifierList = new LinkedList<>();
        blockIdentifierList.add(new BlockIdentifier(block.getHash().getBytes(), block.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifierList);
        Assertions.assertEquals(MessageType.NEW_BLOCK_HASHES, message.getMessageType());
    }

    @Test
    void getBlockIdentifier() {
        Block block = new BlockGenerator().getBlock(1);
        List<BlockIdentifier> blockIdentifierList = new LinkedList<>();
        blockIdentifierList.add(new BlockIdentifier(block.getHash().getBytes(), block.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifierList);
        List<BlockIdentifier> identifiers = message.getBlockIdentifiers();
        Assertions.assertEquals(1, identifiers.size());
        Assertions.assertEquals(blockIdentifierList.get(0).getNumber(), identifiers.get(0).getNumber());
        Assertions.assertArrayEquals(blockIdentifierList.get(0).getHash(),identifiers.get(0).getHash());
    }

    @Test
    void accept() {
        List<BlockIdentifier> blockIdentifiers = new LinkedList<>();
        NewBlockHashesMessage message = new NewBlockHashesMessage(blockIdentifiers);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
