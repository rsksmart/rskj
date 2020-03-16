/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.net;

import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.BlockReceiptsMessage;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.MessageQueue;
import org.ethereum.vm.LogInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentCaptor.*;
import static org.mockito.Mockito.*;

/**
 * Created by Julian Len and Sebastian Sicardi on 20/10/19.
 */
public class LightProcessorTest {

    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});

    private Blockchain blockchain;
    private BlockStore blockStore;
    private LightProcessor lightProcessor;
    private MessageQueue msgQueue;
    private Keccak256 blockHash;

    @Before
    public void setup(){
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, mock(RepositoryLocator.class));
        msgQueue = spy(MessageQueue.class);
        blockHash = new Keccak256(HASH_1);
    }

    @Test
    public void processGetBlockReceiptMessageAndShouldReturnsReceiptsCorrectly() {
        List<Transaction> txs = new LinkedList<>();
        int requestId = 0;
        List<TransactionReceipt> receipts = new LinkedList<>();
        TransactionReceipt receipt = createReceipt();
        receipts.add(receipt);
        final Block block = mock(Block.class);
        Transaction tx = mock(Transaction.class);
        txs.add(tx);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);

        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(txs);
        when(tx.getHash()).thenReturn(new Keccak256(TestUtils.randomBytes(32)));
        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(transactionInfo.getReceipt()).thenReturn(receipt);

        BlockReceiptsMessage expectedMessage = new BlockReceiptsMessage(0, receipts);

        ArgumentCaptor<BlockReceiptsMessage> argument = forClass(BlockReceiptsMessage.class);
        lightProcessor.processGetBlockReceiptsMessage(requestId, block.getHash().getBytes(), msgQueue);
        verify(msgQueue).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetBlockReceiptMessageWithNotContainedBlockHash() {
        lightProcessor.processGetBlockReceiptsMessage(0, blockHash.getBytes(), msgQueue);
        verify(spy(msgQueue), times(0)).sendMessage(any());
    }

    @Test
    public void processTransactionIndexRequestMessageAndReturnsTransactionIndexCorrectly() {
//        final Block block = mock(Block.class);
//        Transaction tx = mock(Transaction.class);
//        TransactionInfo transactionInfo = mock(TransactionInfo.class);
//
//        Keccak256 txHash = new Keccak256(TestUtils.randomBytes(32));
//
//        long id = 100;
//        long blockNumber = 101;
//        int txIndex = 42069;
//        Keccak256 blockHash = new Keccak256(HASH_1);
//
//        when(block.getHash()).thenReturn(blockHash);
//        when(tx.getHash()).thenReturn(txHash);
//        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);
//
//        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());
//        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
//        when(block.getNumber()).thenReturn(blockNumber);
//        when(transactionInfo.getIndex()).thenReturn(txIndex);
//
//        lightProcessor.processTransactionIndexRequest(sender, id, txHash.getBytes());
//
//        assertThat(sender.getMessages().size(), is(1));
//
//        final Message message = sender.getMessages().get(0);
//
//        assertEquals(MessageType.TRANSACTION_INDEX_RESPONSE_MESSAGE, message.getMessageType());
//
//        final TransactionIndexResponseMessage response = (TransactionIndexResponseMessage) message;
//
//        assertThat(response.getId(), is(id));
//        assertThat(response.getBlockHash(), is(blockHash.getBytes()));
//        assertThat(response.getTransactionIndex(), is((long)txIndex));
//        assertThat(response.getBlockNumber(), is(blockNumber));
    }

    @Test
    public void processTransactionIndexRequestMessageWithIncorrectBlockHash() {
//        lightProcessor.processTransactionIndexRequest(sender, 100, new Keccak256(HASH_1).getBytes());
//        assertEquals(0, sender.getMessages().size());
    }

    @Test
    public void processCodeRequestMessageAndReturnsCodeCorrectly() {
//        final Block block = mock(Block.class);
//        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
//
//        byte[] codeHash = TestUtils.randomBytes(32);
//        RskAddress address = new RskAddress(TestUtils.randomBytes(20));
//        Keccak256 blockHash = new Keccak256(HASH_1);
//        long id = 100;
//
//        when(block.getHash()).thenReturn(blockHash);
//        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
//        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
//        when(repositorySnapshot.getCodeHash(address)).thenReturn(new Keccak256(codeHash));
//
//        lightProcessor.processCodeRequest(sender, id, blockHash.getBytes(), address.getBytes());
//
//        assertEquals(1, sender.getMessages().size());
//
//        final Message message = sender.getMessages().get(0);
//
//        assertEquals(MessageType.CODE_RESPONSE_MESSAGE, message.getMessageType());
//
//        final CodeResponseMessage response = (CodeResponseMessage) message;
//
//        assertEquals(id, response.getId());
//        assertArrayEquals(codeHash, response.getCodeHash());
    }

    @Test
    public void processCodeRequestMessageAndReturnsCodeWithIncorrectBlockHash() {
//        long id = 100;
//        RskAddress rskAddress = new RskAddress(TestUtils.randomBytes(20));
//        Keccak256 blockHash = new Keccak256(HASH_1);
//
//        lightProcessor.processCodeRequest(sender, id, blockHash.getBytes(), rskAddress.getBytes());
//        assertEquals(0, sender.getMessages().size());
    }

    // from TransactionTest
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

        receipt.setTransaction(new Transaction(null, null, null, null, null, null));

        return receipt;
    }
}