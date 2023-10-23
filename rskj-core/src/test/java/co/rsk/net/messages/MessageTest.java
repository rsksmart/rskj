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
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
class MessageTest {
    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());
    private BlockGenerator blockGenerator;

    @BeforeEach
    void setUp() {
        blockGenerator = new BlockGenerator(Constants.regtest(), ActivationConfigsForTest.all());
    }

    @Test
    void encodeDecodeGetBlockMessage() {
        Block block = blockGenerator.getBlock(1);
        GetBlockMessage message = new GetBlockMessage(block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.GET_BLOCK_MESSAGE, result.getMessageType());

        GetBlockMessage newmessage = (GetBlockMessage) result;

        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    void encodeDecodeBlockRequestMessage() {
        Block block = blockGenerator.getBlock(1);
        BlockRequestMessage message = new BlockRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_REQUEST_MESSAGE, result.getMessageType());

        BlockRequestMessage newmessage = (BlockRequestMessage) result;

        Assertions.assertEquals(100, newmessage.getId());
        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    void encodeDecodeStatusMessage() {
        Block block = blockGenerator.getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assertions.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
        Assertions.assertNull(newmessage.getStatus().getBestBlockParentHash());
        Assertions.assertNull(newmessage.getStatus().getTotalDifficulty());
    }

    @Test
    void encodeDecodeStatusMessageWithCompleteArguments() {
        Block block = blockGenerator.getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assertions.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    void encodeDecodeStatusMessageUsingGenesisBlock() {
        Block block = blockGenerator.getBlock(0);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        Assertions.assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    void encodeDecodeBlockMessage() {
        Block block = blockGenerator.getBlock(1);
        BlockMessage message = new BlockMessage(block);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_MESSAGE, result.getMessageType());

        BlockMessage newmessage = (BlockMessage) result;

        Assertions.assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        Assertions.assertEquals(block.getHash(), newmessage.getBlock().getHash());
        Assertions.assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    void encodeDecodeBlockResponseMessage() {
        Block block = blockGenerator.getBlock(1);
        BlockResponseMessage message = new BlockResponseMessage(100, block);

        byte[] encoded = message.getEncoded();

        Assertions.assertNotNull(encoded);

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, result.getMessageType());

        BlockResponseMessage newmessage = (BlockResponseMessage) result;

        Assertions.assertEquals(100, newmessage.getId());
        Assertions.assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        Assertions.assertEquals(block.getHash(), newmessage.getBlock().getHash());
        Assertions.assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    void encodeDecodeBlockHeadersResponseMessage() {
        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 1; k <= 4; k++)
            headers.add(blockGenerator.getBlock(k).getHeader());

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(100, headers);

        byte[] encoded = message.getEncoded();

        Assertions.assertNotNull(encoded);

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, result.getMessageType());

        BlockHeadersResponseMessage newmessage = (BlockHeadersResponseMessage) result;

        Assertions.assertEquals(100, newmessage.getId());

        Assertions.assertEquals(headers.size(), newmessage.getBlockHeaders().size());

        for (int k = 0; k < headers.size(); k++) {
            Assertions.assertEquals(headers.get(k).getNumber(), newmessage.getBlockHeaders().get(k).getNumber());
            Assertions.assertEquals(headers.get(k).getHash(), newmessage.getBlockHeaders().get(k).getHash());
            Assertions.assertArrayEquals(headers.get(k).getFullEncoded(), newmessage.getBlockHeaders().get(k).getFullEncoded());
        }
    }

    @Test
    void encodeDecodeNewBlockHashesMessage() {
        List<Block> blocks = blockGenerator.getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> identifiers = new LinkedList<>();

        identifiers.add(new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()));
        identifiers.add(new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(identifiers);
        byte[] encoded = message.getEncoded();
        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.NEW_BLOCK_HASHES, result.getMessageType());

        NewBlockHashesMessage decodedMessage = (NewBlockHashesMessage) result;

        Assertions.assertNotNull(decodedMessage.getBlockIdentifiers());

        List<BlockIdentifier> decodedIdentifiers = decodedMessage.getBlockIdentifiers();

        Assertions.assertEquals(identifiers.size(), decodedIdentifiers.size());
        for (int i = 0; i < identifiers.size(); i ++) {
            Assertions.assertEquals(identifiers.get(i).getNumber(), decodedIdentifiers.get(i).getNumber());
            Assertions.assertArrayEquals(identifiers.get(i).getHash(), decodedIdentifiers.get(i).getHash());
        }
    }

    @Test
    void encodeDecodeTransactionsMessage() {
        List<Transaction> txs = TransactionUtils.getTransactions(10);
        TransactionsMessage message = new TransactionsMessage(txs);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.TRANSACTIONS, result.getMessageType());

        TransactionsMessage newmessage = (TransactionsMessage) result;

        Assertions.assertNotNull(newmessage.getTransactions());
        Assertions.assertEquals(10, newmessage.getTransactions().size());

        for (int k = 0; k < 10; k++) {
            Transaction tx1 = txs.get(k);
            Transaction tx2 = newmessage.getTransactions().get(k);

            Assertions.assertEquals(tx1.getHash(), tx2.getHash());
        }
    }

    @Test
    void encodeDecodeBlockHashRequestMessage() {
        long someId = 42;
        long someHeight = 99;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        Assertions.assertEquals(someId, newMessage.getId());
        Assertions.assertEquals(someHeight, newMessage.getHeight());
    }

    @Test
    void encodeDecodeBlockHashRequestMessageWithHighHeight() {
        long someId = 42;
        long someHeight = 200000;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        Assertions.assertEquals(someId, newMessage.getId());
        Assertions.assertEquals(someHeight, newMessage.getHeight());
    }

    @Test
    void encodeDecodeStateChunkRequestMessage() {
        long someId = 42;

        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(someId, 0L, 0L, 100L);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.STATE_CHUNK_REQUEST_MESSAGE, result.getMessageType());

        SnapStateChunkRequestMessage newMessage = (SnapStateChunkRequestMessage) result;

        Assertions.assertEquals(someId, newMessage.getId());
    }

    @Test
    void encodeDecodeBlockHashResponseMessage() {
        long id = 42;
        byte[] hash = TestUtils.generateBytes("msg",32);

        BlockHashResponseMessage message = new BlockHashResponseMessage(id, hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_HASH_RESPONSE_MESSAGE, result.getMessageType());

        BlockHashResponseMessage newMessage = (BlockHashResponseMessage) result;

        Assertions.assertEquals(id, newMessage.getId());
        Assertions.assertArrayEquals(hash, newMessage.getHash());
    }

    @Test
    void encodeDecodeBlockHeadersRequestMessage() {
        byte[] hash = TestUtils.generateBytes("hash",32);
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, result.getMessageType());

        BlockHeadersRequestMessage newmessage = (BlockHeadersRequestMessage) result;

        Assertions.assertEquals(1, newmessage.getId());
        Assertions.assertArrayEquals(hash, newmessage.getHash());
        Assertions.assertEquals(100, newmessage.getCount());
    }

    @Test
    void encodeDecodeSkeletonResponseMessage() {
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

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, result.getMessageType());

        SkeletonResponseMessage newMessage = (SkeletonResponseMessage) result;

        Assertions.assertEquals(someId, newMessage.getId());

        List<BlockIdentifier> newIds = newMessage.getBlockIdentifiers();
        for (int i = 0; i < ids.size(); i++) {
            BlockIdentifier id = ids.get(i);
            BlockIdentifier newId = newIds.get(i);

            Assertions.assertEquals(id.getNumber(), newId.getNumber());
            Assertions.assertArrayEquals(id.getHash(), newId.getHash());
        }
    }

    @Test
    void encodeDecodeSkeletonRequestMessage() {
        long someId = 42;
        long someStartNumber = 99;
        SkeletonRequestMessage message = new SkeletonRequestMessage(someId, someStartNumber);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, result.getMessageType());

        SkeletonRequestMessage newMessage = (SkeletonRequestMessage) result;

        Assertions.assertEquals(someId, newMessage.getId());
        Assertions.assertEquals(someStartNumber, newMessage.getStartNumber());
    }

    @Test
    void encodeDecodeNewBlockHashMessage() {
        byte[] hash = TestUtils.generateBytes("hash",32);
        NewBlockHashMessage message = new NewBlockHashMessage(hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.NEW_BLOCK_HASH_MESSAGE, result.getMessageType());

        NewBlockHashMessage newMessage = (NewBlockHashMessage) result;

        Assertions.assertArrayEquals(hash, newMessage.getBlockHash());
    }

    @Test
    void encodeDecodeBodyRequestMessage() {
        Block block = blockGenerator.getBlock(1);
        BodyRequestMessage message = new BodyRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BODY_REQUEST_MESSAGE, result.getMessageType());

        BodyRequestMessage newmessage = (BodyRequestMessage) result;

        Assertions.assertEquals(100, newmessage.getId());
        Assertions.assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    void encodeDecodeBodyResponseMessage() {
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

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(encoded, result.getEncoded());
        Assertions.assertEquals(MessageType.BODY_RESPONSE_MESSAGE, result.getMessageType());

        BodyResponseMessage newmessage = (BodyResponseMessage)result;

        Assertions.assertNotNull(newmessage);

        Assertions.assertEquals(100, newmessage.getId());

        Assertions.assertNotNull(newmessage.getTransactions());
        Assertions.assertEquals(transactions.size(), newmessage.getTransactions().size());

        Assertions.assertEquals(transactions, newmessage.getTransactions());

        Assertions.assertNotNull(newmessage.getUncles());
        Assertions.assertEquals(uncles.size(), newmessage.getUncles().size());

        for (int k = 0; k < uncles.size(); k++)
            Assertions.assertArrayEquals(uncles.get(k).getFullEncoded(), newmessage.getUncles().get(k).getFullEncoded());
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
