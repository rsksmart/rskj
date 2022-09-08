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
import co.rsk.core.BlockDifficulty;
import co.rsk.net.Status;
import org.ethereum.core.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.*;

public class StatusMessageTest {
    @Test
    public void createWithBestBlockNumberAndHash() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());

        StatusMessage message = new StatusMessage(status);

        Assertions.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assertions.assertSame(status, message.getStatus());
        Assertions.assertEquals(1, message.getStatus().getBestBlockNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), message.getStatus().getBestBlockHash());
        Assertions.assertNull(message.getStatus().getBestBlockParentHash());
        Assertions.assertNull(message.getStatus().getTotalDifficulty());
    }

    @Test
    public void createWithCompleteArguments() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);
        Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));

        StatusMessage message = new StatusMessage(status);

        Assertions.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assertions.assertSame(status, message.getStatus());
        Assertions.assertEquals(1, message.getStatus().getBestBlockNumber());
        Assertions.assertArrayEquals(block.getHash().getBytes(), message.getStatus().getBestBlockHash());
        Assertions.assertNotNull(message.getStatus().getBestBlockParentHash());
        Assertions.assertArrayEquals(block.getParentHash().getBytes(), message.getStatus().getBestBlockParentHash());
        Assertions.assertNotNull(message.getStatus().getTotalDifficulty());
        Assertions.assertEquals(new BlockDifficulty(BigInteger.TEN), message.getStatus().getTotalDifficulty());
    }

    @Test
    public void createWithGenesisBestBlockNumberAndHash() {
        Block genesis = new BlockGenerator().getGenesisBlock();
        Status status = new Status(genesis.getNumber(), genesis.getHash().getBytes());

        StatusMessage message = new StatusMessage(status);

        Assertions.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assertions.assertSame(status, message.getStatus());
        Assertions.assertEquals(0, message.getStatus().getBestBlockNumber());
        Assertions.assertArrayEquals(genesis.getHash().getBytes(), message.getStatus().getBestBlockHash());
    }

    @Test
    public void accept() {
        StatusMessage message = new StatusMessage(mock(Status.class));

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
