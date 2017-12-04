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
import co.rsk.net.Status;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class StatusMessageTest {
    @Test
    public void createWithBestBlockNumberAndHash() {
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block = BlockGenerator.getInstance().createChildBlock(genesis);
        Status status = new Status(block.getNumber(), block.getHash());

        StatusMessage message = new StatusMessage(status);

        Assert.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assert.assertSame(status, message.getStatus());
        Assert.assertEquals(1, message.getStatus().getBestBlockNumber());
        Assert.assertArrayEquals(block.getHash(), message.getStatus().getBestBlockHash());
        Assert.assertNull(message.getStatus().getBestBlockParentHash());
        Assert.assertNull(message.getStatus().getTotalDifficulty());
    }

    @Test
    public void createWithCompleteArguments() {
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Block block = BlockGenerator.getInstance().createChildBlock(genesis);
        Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), BigInteger.TEN);

        StatusMessage message = new StatusMessage(status);

        Assert.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assert.assertSame(status, message.getStatus());
        Assert.assertEquals(1, message.getStatus().getBestBlockNumber());
        Assert.assertArrayEquals(block.getHash(), message.getStatus().getBestBlockHash());
        Assert.assertNotNull(message.getStatus().getBestBlockParentHash());
        Assert.assertArrayEquals(block.getParentHash(), message.getStatus().getBestBlockParentHash());
        Assert.assertNotNull(message.getStatus().getTotalDifficulty());
        Assert.assertEquals(BigInteger.TEN, message.getStatus().getTotalDifficulty());
    }

    @Test
    public void createWithGenesisBestBlockNumberAndHash() {
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Status status = new Status(genesis.getNumber(), genesis.getHash());

        StatusMessage message = new StatusMessage(status);

        Assert.assertEquals(MessageType.STATUS_MESSAGE, message.getMessageType());
        Assert.assertSame(status, message.getStatus());
        Assert.assertEquals(0, message.getStatus().getBestBlockNumber());
        Assert.assertArrayEquals(genesis.getHash(), message.getStatus().getBestBlockHash());
    }
}
