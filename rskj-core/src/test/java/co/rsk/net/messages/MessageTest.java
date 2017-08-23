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
import co.rsk.net.utils.TransactionUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.rlpx.Node;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class MessageTest {
    @Test
    public void encodeDecodeGetBlockMessage() {
        Block block = BlockGenerator.getBlock(1);
        GetBlockMessage message = new GetBlockMessage(block.getHash());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, result.getMessageType());

        GetBlockMessage newmessage = (GetBlockMessage) result;

        Assert.assertArrayEquals(block.getHash(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeGetBlockHeaderMessage() {
        Block block = BlockGenerator.getBlock(1);
        GetBlockHeadersMessage message = new GetBlockHeadersMessage(block.getHash(), 1);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.GET_BLOCK_HEADERS_MESSAGE, result.getMessageType());

        GetBlockHeadersMessage newmessage = (GetBlockHeadersMessage) result;

        Assert.assertArrayEquals(block.getHash(), newmessage.getBlockHash());
        Assert.assertEquals(newmessage.getMaxHeaders(), 1);

        message = new GetBlockHeadersMessage(0, block.getHash(), 10, 5, true);
        encoded = message.getEncoded();
        result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.GET_BLOCK_HEADERS_MESSAGE, result.getMessageType());
        newmessage = (GetBlockHeadersMessage) result;

        Assert.assertArrayEquals(block.getHash(), newmessage.getBlockHash());
        Assert.assertEquals(newmessage.getMaxHeaders(), 10);
        Assert.assertEquals(newmessage.getSkipBlocks(), 5);
        Assert.assertTrue(newmessage.isReverse());
    }

    @Test
    public void encodeDecodeStatusMessage() {
        Block block = BlockGenerator.getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assert.assertArrayEquals(block.getHash(), newmessage.getStatus().getBestBlockHash());
        Assert.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeStatusMessageUsingGenesisBlock() {
        Block block = BlockGenerator.getBlock(0);
        Status status = new Status(block.getNumber(), block.getHash());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assert.assertArrayEquals(block.getHash(), newmessage.getStatus().getBestBlockHash());
        Assert.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeBlockMessage() {
        Block block = BlockGenerator.getBlock(1);
        BlockMessage message = new BlockMessage(block);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_MESSAGE, result.getMessageType());

        BlockMessage newmessage = (BlockMessage) result;

        Assert.assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), newmessage.getBlock().getHash());
        Assert.assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    public void encodeDecodeBlockHeaderMessage() {
        BlockHeader header = BlockGenerator.getBlock(1).getHeader();
        BlockHeadersMessage message = new BlockHeadersMessage(header);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, result.getMessageType());

        BlockHeadersMessage newmessage = (BlockHeadersMessage) result;

        Assert.assertEquals(newmessage.getBlockHeaders().size(), 1);
        BlockHeader newheader = newmessage.getBlockHeaders().get(0);
        Assert.assertEquals(header.getNumber(), newheader.getNumber());
        Assert.assertArrayEquals(header.getHash(), newheader.getHash());
        Assert.assertArrayEquals(header.getEncoded(), newheader.getEncoded());
    }

    @Test
    public void encodeDecodeNewBlockHashesMessage() {
        List<Block> blocks = BlockGenerator.getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> identifiers = new LinkedList<>();

        identifiers.add(new BlockIdentifier(b1.getHash(), b1.getNumber()));
        identifiers.add(new BlockIdentifier(b2.getHash(), b2.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(identifiers);
        byte[] encoded = message.getEncoded();
        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.NEW_BLOCK_HASHES, result.getMessageType());

        NewBlockHashesMessage decodedMessage = (NewBlockHashesMessage) result;

        Assert.assertNotNull(decodedMessage.getBlockIdentifiers());

        List<BlockIdentifier> decodedIdentifiers = decodedMessage.getBlockIdentifiers();

        Assert.assertEquals(identifiers.size(), decodedIdentifiers.size());
        for (int i = 0; i < identifiers.size(); i ++) {
            Assert.assertEquals(identifiers.get(i).getNumber(), decodedIdentifiers.get(i).getNumber());
            Assert.assertArrayEquals(identifiers.get(i).getHash(), decodedIdentifiers.get(i).getHash());
        }
    }

    @Test
    public void encodeDecodeTransactionsMessage() {
        List<Transaction> txs = TransactionUtils.getTransactions(10);
        TransactionsMessage message = new TransactionsMessage(txs);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.TRANSACTIONS, result.getMessageType());

        TransactionsMessage newmessage = (TransactionsMessage) result;

        Assert.assertNotNull(newmessage.getTransactions());
        Assert.assertEquals(10, newmessage.getTransactions().size());

        for (int k = 0; k < 10; k++) {
            Transaction tx1 = txs.get(k);
            Transaction tx2 = newmessage.getTransactions().get(k);

            Assert.assertArrayEquals(tx1.getHash(), tx2.getHash());
        }
    }

    @Test
    public void encodeDecodeGetBlockHeadersByHashMessage() {
        byte[] hash = HashUtil.randomHash();
        GetBlockHeadersByHashMessage message = new GetBlockHeadersByHashMessage(1, hash, 100);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.GET_BLOCK_HEADERS_BY_HASH_MESSAGE, result.getMessageType());

        GetBlockHeadersByHashMessage newmessage = (GetBlockHeadersByHashMessage) result;

        Assert.assertEquals(1, newmessage.getId());
        Assert.assertArrayEquals(hash, newmessage.getHash());
        Assert.assertEquals(100, newmessage.getCount());
    }
}
