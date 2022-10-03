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
import co.rsk.net.utils.TransactionUtils;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class MessageTest {
    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.allBut(ConsensusRule.RSKIP351));
    private BlockGenerator blockGenerator;

    @Before
    public void setUp() {
        blockGenerator = new BlockGenerator(Constants.regtest(), ActivationConfigsForTest.allBut(ConsensusRule.RSKIP351));
    }

    @Test
    public void encodeDecodeGetBlockMessage() {
        Block block = blockGenerator.getBlock(1);
        GetBlockMessage message = new GetBlockMessage(block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, result.getMessageType());

        GetBlockMessage newmessage = (GetBlockMessage) result;

        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBlockRequestMessage() {
        Block block = blockGenerator.getBlock(1);
        BlockRequestMessage message = new BlockRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_REQUEST_MESSAGE, result.getMessageType());

        BlockRequestMessage newmessage = (BlockRequestMessage) result;

        Assert.assertEquals(100, newmessage.getId());
        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeStatusMessage() {
        Block block = blockGenerator.getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assert.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
        Assert.assertNull(newmessage.getStatus().getBestBlockParentHash());
        Assert.assertNull(newmessage.getStatus().getTotalDifficulty());
    }

    @Test
    public void encodeDecodeStatusMessageWithCompleteArguments() {
        Block block = blockGenerator.getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assert.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeStatusMessageUsingGenesisBlock() {
        Block block = blockGenerator.getBlock(0);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assert.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeBlockMessage() {
        Block block = blockGenerator.getBlock(1);
        BlockMessage message = new BlockMessage(block);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_MESSAGE, result.getMessageType());

        BlockMessage newmessage = (BlockMessage) result;

        Assert.assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        Assert.assertEquals(block.getHash(), newmessage.getBlock().getHash());
        Assert.assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    public void encodeDecodeBlockResponseMessage() {
        Block block = blockGenerator.getBlock(1);

        BlockResponseMessage message = new BlockResponseMessage(100, block);

        byte[] encoded = message.getEncoded();

        Assert.assertNotNull(encoded);

        System.out.println("here");

        Message result = Message.create(blockFactory, encoded);

        System.out.println("there");

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, result.getMessageType());

        BlockResponseMessage newmessage = (BlockResponseMessage) result;

        Assert.assertEquals(100, newmessage.getId());
        Assert.assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        Assert.assertEquals(block.getHash(), newmessage.getBlock().getHash());
        Assert.assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    public void encodeDecodeBlockHeadersResponseMessage() {
        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 1; k <= 4; k++)
            headers.add(blockGenerator.getBlock(k).getHeader());

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(100, headers);

        byte[] encoded = message.getEncoded();

        Assert.assertNotNull(encoded);

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, result.getMessageType());

        BlockHeadersResponseMessage newmessage = (BlockHeadersResponseMessage) result;

        Assert.assertEquals(100, newmessage.getId());

        Assert.assertEquals(headers.size(), newmessage.getBlockHeaders().size());

        for (int k = 0; k < headers.size(); k++) {
            Assert.assertEquals(headers.get(k).getNumber(), newmessage.getBlockHeaders().get(k).getNumber());
            Assert.assertEquals(headers.get(k).getHash(), newmessage.getBlockHeaders().get(k).getHash());
            Assert.assertArrayEquals(headers.get(k).getFullEncoded(), newmessage.getBlockHeaders().get(k).getFullEncoded());
        }
    }

    @Test
    public void encodeDecodeNewBlockHashesMessage() {
        List<Block> blocks = blockGenerator.getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> identifiers = new LinkedList<>();

        identifiers.add(new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()));
        identifiers.add(new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(identifiers);
        byte[] encoded = message.getEncoded();
        Message result = Message.create(blockFactory, encoded);

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

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.TRANSACTIONS, result.getMessageType());

        TransactionsMessage newmessage = (TransactionsMessage) result;

        Assert.assertNotNull(newmessage.getTransactions());
        Assert.assertEquals(10, newmessage.getTransactions().size());

        for (int k = 0; k < 10; k++) {
            Transaction tx1 = txs.get(k);
            Transaction tx2 = newmessage.getTransactions().get(k);

            Assert.assertEquals(tx1.getHash(), tx2.getHash());
        }
    }

    @Test
    public void encodeDecodeBlockHashRequestMessage() {
        long someId = 42;
        long someHeight = 99;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        Assert.assertEquals(someId, newMessage.getId());
        Assert.assertEquals(someHeight, newMessage.getHeight());
    }

    @Test
    public void encodeDecodeBlockHashRequestMessageWithHighHeight() {
        long someId = 42;
        long someHeight = 200000;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        Assert.assertEquals(someId, newMessage.getId());
        Assert.assertEquals(someHeight, newMessage.getHeight());
    }

    @Test
    public void encodeDecodeBlockHashResponseMessage() {
        long id = 42;
        byte[] hash = new byte[32];
        Random random = new Random();
        random.nextBytes(hash);

        BlockHashResponseMessage message = new BlockHashResponseMessage(id, hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HASH_RESPONSE_MESSAGE, result.getMessageType());

        BlockHashResponseMessage newMessage = (BlockHashResponseMessage) result;

        Assert.assertEquals(id, newMessage.getId());
        Assert.assertArrayEquals(hash, newMessage.getHash());
    }

    @Test
    public void encodeDecodeBlockHeadersRequestMessage() {
        byte[] hash = HashUtil.randomHash();
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, result.getMessageType());

        BlockHeadersRequestMessage newmessage = (BlockHeadersRequestMessage) result;

        Assert.assertEquals(1, newmessage.getId());
        Assert.assertArrayEquals(hash, newmessage.getHash());
        Assert.assertEquals(100, newmessage.getCount());
    }

    @Test
    public void encodeDecodeSkeletonResponseMessage() {
        long someId = 42;
        List<Block> blocks = blockGenerator.getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> ids = new LinkedList<>();

        ids.add(new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()));
        ids.add(new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()));
        SkeletonResponseMessage message = new SkeletonResponseMessage(someId, ids);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, result.getMessageType());

        SkeletonResponseMessage newMessage = (SkeletonResponseMessage) result;

        Assert.assertEquals(someId, newMessage.getId());

        List<BlockIdentifier> newIds = newMessage.getBlockIdentifiers();
        for (int i = 0; i < ids.size(); i++) {
            BlockIdentifier id = ids.get(i);
            BlockIdentifier newId = newIds.get(i);

            Assert.assertEquals(id.getNumber(), newId.getNumber());
            Assert.assertArrayEquals(id.getHash(), newId.getHash());
        }
    }

    @Test
    public void encodeDecodeSkeletonRequestMessage() {
        long someId = 42;
        long someStartNumber = 99;
        SkeletonRequestMessage message = new SkeletonRequestMessage(someId, someStartNumber);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, result.getMessageType());

        SkeletonRequestMessage newMessage = (SkeletonRequestMessage) result;

        Assert.assertEquals(someId, newMessage.getId());
        Assert.assertEquals(someStartNumber, newMessage.getStartNumber());
    }

    @Test
    public void encodeDecodeNewBlockHashMessage() {
        byte[] hash = HashUtil.randomHash();
        NewBlockHashMessage message = new NewBlockHashMessage(hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.NEW_BLOCK_HASH_MESSAGE, result.getMessageType());

        NewBlockHashMessage newMessage = (NewBlockHashMessage) result;

        Assert.assertArrayEquals(hash, newMessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBodyRequestMessage() {
        Block block = blockGenerator.getBlock(1);
        BodyRequestMessage message = new BodyRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BODY_REQUEST_MESSAGE, result.getMessageType());

        BodyRequestMessage newmessage = (BodyRequestMessage) result;

        Assert.assertEquals(100, newmessage.getId());
        Assert.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBodyResponseMessage() {
        List<Transaction> transactions = new ArrayList<>();

        for (int k = 1; k <= 10; k++)
            transactions.add(createTransaction(k));

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = this.blockGenerator;
        Block parent = blockGenerator.getGenesisBlock();

        for (int k = 1; k < 10; k++) {
            Block block = blockGenerator.createChildBlock(parent);
            uncles.add(block.getHeader());
            parent = block;
        }

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles, null);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(encoded, result.getEncoded());
        Assert.assertEquals(MessageType.BODY_RESPONSE_MESSAGE, result.getMessageType());

        BodyResponseMessage newmessage = (BodyResponseMessage)result;

        Assert.assertNotNull(newmessage);

        Assert.assertEquals(100, newmessage.getId());

        Assert.assertNotNull(newmessage.getTransactions());
        Assert.assertEquals(transactions.size(), newmessage.getTransactions().size());

        Assert.assertEquals(transactions, newmessage.getTransactions());

        Assert.assertNotNull(newmessage.getUncles());
        Assert.assertEquals(uncles.size(), newmessage.getUncles().size());

        for (int k = 0; k < uncles.size(); k++)
            Assert.assertArrayEquals(uncles.get(k).getFullEncoded(), newmessage.getUncles().get(k).getFullEncoded());
    }

    private static Transaction createTransaction(int number) {
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }
}
