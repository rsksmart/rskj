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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class MessageTest {
    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    public void encodeDecodeGetBlockMessage() {
        Block block = new BlockGenerator().getBlock(1);
        GetBlockMessage message = new GetBlockMessage(block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.GET_BLOCK_MESSAGE, result.getMessageType());

        GetBlockMessage newmessage = (GetBlockMessage) result;

        assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBlockRequestMessage() {
        Block block = new BlockGenerator().getBlock(1);
        BlockRequestMessage message = new BlockRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_REQUEST_MESSAGE, result.getMessageType());

        BlockRequestMessage newmessage = (BlockRequestMessage) result;

        assertEquals(100, newmessage.getId());
        assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeStatusMessage() {
        Block block = new BlockGenerator().getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
        assertNull(newmessage.getStatus().getBestBlockParentHash());
        assertNull(newmessage.getStatus().getTotalDifficulty());
    }

    @Test
    public void encodeDecodeStatusMessageWithCompleteArguments() {
        Block block = new BlockGenerator().getBlock(1);
        Status status = new Status(block.getNumber(), block.getHash().getBytes(), block.getParentHash().getBytes(), new BlockDifficulty(BigInteger.TEN));
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeStatusMessageUsingGenesisBlock() {
        Block block = new BlockGenerator().getBlock(0);
        Status status = new Status(block.getNumber(), block.getHash().getBytes());
        StatusMessage message = new StatusMessage(status);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.STATUS_MESSAGE, result.getMessageType());

        StatusMessage newmessage = (StatusMessage) result;

        assertArrayEquals(block.getHash().getBytes(), newmessage.getStatus().getBestBlockHash());
        assertEquals(block.getNumber(), newmessage.getStatus().getBestBlockNumber());
    }

    @Test
    public void encodeDecodeBlockMessage() {
        Block block = new BlockGenerator().getBlock(1);
        BlockMessage message = new BlockMessage(block);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_MESSAGE, result.getMessageType());

        BlockMessage newmessage = (BlockMessage) result;

        assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        assertEquals(block.getHash(), newmessage.getBlock().getHash());
        assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    public void encodeDecodeBlockResponseMessage() {
        Block block = new BlockGenerator().getBlock(1);
        BlockResponseMessage message = new BlockResponseMessage(100, block);

        byte[] encoded = message.getEncoded();

        assertNotNull(encoded);

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, result.getMessageType());

        BlockResponseMessage newmessage = (BlockResponseMessage) result;

        assertEquals(100, newmessage.getId());
        assertEquals(block.getNumber(), newmessage.getBlock().getNumber());
        assertEquals(block.getHash(), newmessage.getBlock().getHash());
        assertArrayEquals(block.getEncoded(), newmessage.getBlock().getEncoded());
    }

    @Test
    public void encodeDecodeBlockHeadersResponseMessage() {
        List<BlockHeader> headers = new ArrayList<>();

        for (int k = 1; k <= 4; k++)
            headers.add(new BlockGenerator().getBlock(k).getHeader());

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(100, headers);

        byte[] encoded = message.getEncoded();

        assertNotNull(encoded);

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, result.getMessageType());

        BlockHeadersResponseMessage newmessage = (BlockHeadersResponseMessage) result;

        assertEquals(100, newmessage.getId());

        assertEquals(headers.size(), newmessage.getBlockHeaders().size());

        for (int k = 0; k < headers.size(); k++) {
            assertEquals(headers.get(k).getNumber(), newmessage.getBlockHeaders().get(k).getNumber());
            assertEquals(headers.get(k).getHash(), newmessage.getBlockHeaders().get(k).getHash());
            assertArrayEquals(headers.get(k).getFullEncoded(), newmessage.getBlockHeaders().get(k).getFullEncoded());
        }
    }

    @Test
    public void encodeDecodeNewBlockHashesMessage() {
        List<Block> blocks = new BlockGenerator().getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> identifiers = new LinkedList<>();

        identifiers.add(new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()));
        identifiers.add(new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()));

        NewBlockHashesMessage message = new NewBlockHashesMessage(identifiers);
        byte[] encoded = message.getEncoded();
        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.NEW_BLOCK_HASHES, result.getMessageType());

        NewBlockHashesMessage decodedMessage = (NewBlockHashesMessage) result;

        assertNotNull(decodedMessage.getBlockIdentifiers());

        List<BlockIdentifier> decodedIdentifiers = decodedMessage.getBlockIdentifiers();

        assertEquals(identifiers.size(), decodedIdentifiers.size());
        for (int i = 0; i < identifiers.size(); i ++) {
            assertEquals(identifiers.get(i).getNumber(), decodedIdentifiers.get(i).getNumber());
            assertArrayEquals(identifiers.get(i).getHash(), decodedIdentifiers.get(i).getHash());
        }
    }

    @Test
    public void encodeDecodeTransactionsMessage() {
        List<Transaction> txs = TransactionUtils.getTransactions(10);
        TransactionsMessage message = new TransactionsMessage(txs);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.TRANSACTIONS, result.getMessageType());

        TransactionsMessage newmessage = (TransactionsMessage) result;

        assertNotNull(newmessage.getTransactions());
        assertEquals(10, newmessage.getTransactions().size());

        for (int k = 0; k < 10; k++) {
            Transaction tx1 = txs.get(k);
            Transaction tx2 = newmessage.getTransactions().get(k);

            assertEquals(tx1.getHash(), tx2.getHash());
        }
    }

    @Test
    public void encodeDecodeBlockHashRequestMessage() {
        long someId = 42;
        long someHeight = 99;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        assertEquals(someId, newMessage.getId());
        assertEquals(someHeight, newMessage.getHeight());
    }

    @Test
    public void encodeDecodeBlockHashRequestMessageWithHighHeight() {
        long someId = 42;
        long someHeight = 200000;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, result.getMessageType());

        BlockHashRequestMessage newMessage = (BlockHashRequestMessage) result;

        assertEquals(someId, newMessage.getId());
        assertEquals(someHeight, newMessage.getHeight());
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

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_HASH_RESPONSE_MESSAGE, result.getMessageType());

        BlockHashResponseMessage newMessage = (BlockHashResponseMessage) result;

        assertEquals(id, newMessage.getId());
        assertArrayEquals(hash, newMessage.getHash());
    }

    @Test
    public void encodeDecodeBlockHeadersRequestMessage() {
        byte[] hash = HashUtil.randomHash();
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, result.getMessageType());

        BlockHeadersRequestMessage newmessage = (BlockHeadersRequestMessage) result;

        assertEquals(1, newmessage.getId());
        assertArrayEquals(hash, newmessage.getHash());
        assertEquals(100, newmessage.getCount());
    }

    @Test
    public void encodeDecodeSkeletonResponseMessage() {
        long someId = 42;
        List<Block> blocks = new BlockGenerator().getBlockChain(10);
        Block b1 = blocks.get(5);
        Block b2 = blocks.get(7);

        List<BlockIdentifier> ids = new LinkedList<>();

        ids.add(new BlockIdentifier(b1.getHash().getBytes(), b1.getNumber()));
        ids.add(new BlockIdentifier(b2.getHash().getBytes(), b2.getNumber()));
        SkeletonResponseMessage message = new SkeletonResponseMessage(someId, ids);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, result.getMessageType());

        SkeletonResponseMessage newMessage = (SkeletonResponseMessage) result;

        assertEquals(someId, newMessage.getId());

        List<BlockIdentifier> newIds = newMessage.getBlockIdentifiers();
        for (int i = 0; i < ids.size(); i++) {
            BlockIdentifier id = ids.get(i);
            BlockIdentifier newId = newIds.get(i);

            assertEquals(id.getNumber(), newId.getNumber());
            assertArrayEquals(id.getHash(), newId.getHash());
        }
    }

    @Test
    public void encodeDecodeBlockReceiptsResponseMessage() {
        long someId = 42;
        List<TransactionReceipt> receipts = new LinkedList<>();

        receipts.add(createReceipt());
        receipts.add(createReceipt());
        BlockReceiptsResponseMessage message = new BlockReceiptsResponseMessage(someId ,receipts);
        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BLOCK_RECEIPTS_RESPONSE_MESSAGE, result.getMessageType());

        BlockReceiptsResponseMessage newMessage = (BlockReceiptsResponseMessage) result;

        assertEquals(someId, newMessage.getId());
        List<TransactionReceipt> newReceipts = newMessage.getBlockReceipts();
        assertEquals(receipts.size(), newReceipts.size());

        for (int i = 0; i < receipts.size(); i++) {
            assertArrayEquals(receipts.get(i).getEncoded(), newReceipts.get(i).getEncoded());
        }
    }

//    @Test
//    public void encodeDecodeBlockReceiptsRequestMessage() {
//        long someId = 42;
//        byte[] hash = HashUtil.randomHash();
//        BlockReceiptsRequestMessage message = new BlockReceiptsRequestMessage(someId, hash);
//
//        byte[] encoded = message.getEncoded();
//
//        Message result = Message.create(blockFactory, encoded);
//
//        assertNotNull(result);
//        assertArrayEquals(encoded, result.getEncoded());
//        assertEquals(MessageType.BLOCK_RECEIPTS_REQUEST_MESSAGE, result.getMessageType());
//
//        BlockReceiptsRequestMessage newMessage = (BlockReceiptsRequestMessage) result;
//
//        assertEquals(someId, newMessage.getId());
//        assertArrayEquals(hash, newMessage.getBlockHash());
//    }

    @Test
    public void encodeDecodeSkeletonRequestMessage() {
        long someId = 42;
        long someStartNumber = 99;
        SkeletonRequestMessage message = new SkeletonRequestMessage(someId, someStartNumber);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.SKELETON_REQUEST_MESSAGE, result.getMessageType());

        SkeletonRequestMessage newMessage = (SkeletonRequestMessage) result;

        assertEquals(someId, newMessage.getId());
        assertEquals(someStartNumber, newMessage.getStartNumber());
    }

    @Test
    public void encodeDecodeNewBlockHashMessage() {
        byte[] hash = HashUtil.randomHash();
        NewBlockHashMessage message = new NewBlockHashMessage(hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.NEW_BLOCK_HASH_MESSAGE, result.getMessageType());

        NewBlockHashMessage newMessage = (NewBlockHashMessage) result;

        assertArrayEquals(hash, newMessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBodyRequestMessage() {
        Block block = new BlockGenerator().getBlock(1);
        BodyRequestMessage message = new BodyRequestMessage(100, block.getHash().getBytes());

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BODY_REQUEST_MESSAGE, result.getMessageType());

        BodyRequestMessage newmessage = (BodyRequestMessage) result;

        assertEquals(100, newmessage.getId());
        assertArrayEquals(block.getHash().getBytes(), newmessage.getBlockHash());
    }

    @Test
    public void encodeDecodeBodyResponseMessage() {
        List<Transaction> transactions = new ArrayList<>();

        for (int k = 1; k <= 10; k++)
            transactions.add(createTransaction(k));

        List<BlockHeader> uncles = new ArrayList<>();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block parent = blockGenerator.getGenesisBlock();

        for (int k = 1; k < 10; k++) {
            Block block = blockGenerator.createChildBlock(parent);
            uncles.add(block.getHeader());
            parent = block;
        }

        BodyResponseMessage message = new BodyResponseMessage(100, transactions, uncles);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.BODY_RESPONSE_MESSAGE, result.getMessageType());

        BodyResponseMessage newmessage = (BodyResponseMessage)result;

        assertNotNull(newmessage);

        assertEquals(100, newmessage.getId());

        assertNotNull(newmessage.getTransactions());
        assertEquals(transactions.size(), newmessage.getTransactions().size());

        assertEquals(transactions, newmessage.getTransactions());

        assertNotNull(newmessage.getUncles());
        assertEquals(uncles.size(), newmessage.getUncles().size());

        for (int k = 0; k < uncles.size(); k++)
            assertArrayEquals(uncles.get(k).getFullEncoded(), newmessage.getUncles().get(k).getFullEncoded());
    }

    @Test
    public void encodeDecodeTransactionIndexResponseMessage() {
        long someId = 42;
        long someBlockNumber = 43;
        long someTxIndex = 44;

        byte[] someBlockHash = HashUtil.randomHash();

        TransactionIndexResponseMessage message = new TransactionIndexResponseMessage(someId,someBlockNumber,someBlockHash,someTxIndex);
        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertThat(result.getEncoded(), is(encoded));
        assertThat(result.getMessageType(), is(MessageType.TRANSACTION_INDEX_RESPONSE_MESSAGE));

        TransactionIndexResponseMessage newMessage = (TransactionIndexResponseMessage) result;

        assertThat(newMessage.getId(),is(someId));
        assertThat(newMessage.getBlockNumber(),is(someBlockNumber));
        assertThat(newMessage.getTransactionIndex(),is(someTxIndex));
        assertThat(newMessage.getBlockHash(),is(someBlockHash));
    }

    @Test
    public void encodeDecodeTransactionIndexRequestMessage() {
        long someId = 42;
        byte[] hash = HashUtil.randomHash();
        TransactionIndexRequestMessage message = new TransactionIndexRequestMessage(someId, hash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.TRANSACTION_INDEX_REQUEST_MESSAGE, result.getMessageType());

        TransactionIndexRequestMessage newMessage = (TransactionIndexRequestMessage) result;

        assertEquals(someId, newMessage.getId());
        assertArrayEquals(hash, newMessage.getTransactionHash());
    }

    @Test
    public void encodeDecodeCodeRequestMessage() {
        long someId = 42;
        byte[] blockHash = HashUtil.randomHash();
        byte[] address = HashUtil.randomHash();
        CodeRequestMessage message = new CodeRequestMessage(someId, blockHash, address);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.CODE_REQUEST_MESSAGE, result.getMessageType());

        CodeRequestMessage newMessage = (CodeRequestMessage) result;

        assertEquals(someId, newMessage.getId());
        assertArrayEquals(blockHash, newMessage.getBlockHash());
        assertArrayEquals(address, newMessage.getAddress());
    }

    @Test
    public void encodeDecodeCodeResponseMessage() {
        long someId = 42;
        byte[] codeHash = HashUtil.randomHash();
        CodeResponseMessage message = new CodeResponseMessage(someId, codeHash);

        byte[] encoded = message.getEncoded();

        Message result = Message.create(blockFactory, encoded);

        assertNotNull(result);
        assertArrayEquals(encoded, result.getEncoded());
        assertEquals(MessageType.CODE_RESPONSE_MESSAGE, result.getMessageType());

        CodeResponseMessage newMessage = (CodeResponseMessage) result;

        assertEquals(someId, newMessage.getId());
        assertArrayEquals(codeHash, newMessage.getCodeHash());
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

    private static TransactionReceipt createReceipt() {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});
        receipt.setTransaction(new Transaction((byte[]) null, null, null, null, null, null));
        return receipt;
    }
}
