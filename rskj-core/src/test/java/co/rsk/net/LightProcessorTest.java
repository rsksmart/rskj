/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.MessageQueue;
import org.ethereum.vm.LogInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.ethereum.TestUtils.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
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
    private RepositoryLocator repositoryLocator;

    @Before
    public void setup(){
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        repositoryLocator = mock(RepositoryLocator.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        msgQueue = spy(MessageQueue.class);
        blockHash = new Keccak256(HASH_1);
    }

    @Test
    public void processGetBlockReceiptMessageAndShouldReturnsReceiptsCorrectly() {
        List<Transaction> txs = new LinkedList<>();
        long requestId = 0;
        List<TransactionReceipt> receipts = new LinkedList<>();
        TransactionReceipt receipt = createReceipt();
        receipts.add(receipt);
        final Block block = mock(Block.class);
        Transaction tx = mock(Transaction.class);
        txs.add(tx);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);

        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(txs);
        when(tx.getHash()).thenReturn(new Keccak256(randomBytes(32)));
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
    public void processGetBlockReceiptMessageWithInvalidBlockHash() {
        lightProcessor.processGetBlockReceiptsMessage(0, blockHash.getBytes(), msgQueue);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processBlockReceiptMessageAndShouldThrowAnException() {
        long requestId = 0;
        List<TransactionReceipt> receipts = new LinkedList<>();
        String expected = "Not supported BlockReceipt processing";
        try {
            lightProcessor.processBlockReceiptsMessage(requestId, receipts, msgQueue);
        } catch (UnsupportedOperationException e) {
            assertEquals(expected, e.getMessage());
        }
    }
    
    @Test
    public void processGetTransactionIndexMessageAndReturnsTransactionIndexCorrectly() {
        final Block block = mock(Block.class);
        Transaction tx = mock(Transaction.class);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);

        Keccak256 txHash = new Keccak256(randomBytes(32));

        long id = 100;
        long blockNumber = 101;
        int txIndex = 42069;
        Keccak256 blockHash = new Keccak256(HASH_1);

        when(block.getHash()).thenReturn(blockHash);
        when(tx.getHash()).thenReturn(txHash);
        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);

        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getNumber()).thenReturn(blockNumber);
        when(transactionInfo.getIndex()).thenReturn(txIndex);

        TransactionIndexMessage expectedMessage = new TransactionIndexMessage(id, blockNumber, block.getHash().getBytes(), txIndex);

        ArgumentCaptor<TransactionIndexMessage> argument = forClass(TransactionIndexMessage.class);
        lightProcessor.processGetTransactionIndex(id, txHash.getBytes(), msgQueue);
        verify(msgQueue).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetTransactionIndexMessageWithIncorrectBlockHash() {
        lightProcessor.processGetTransactionIndex(100, blockHash.getBytes(), msgQueue);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processTransactionIndexMessageAndShouldThrowAnException() {
        try {
            lightProcessor.processTransactionIndexMessage(0, 0, null, 0, msgQueue);
        } catch (UnsupportedOperationException e) {
            assertEquals("Not supported TransactionIndexMessage processing", e.getMessage());
        }
    }

    @Test
    public void processGetCodeMessageAndShouldReturnsCodeHashCorrectly() {
        final Block block = mock(Block.class);
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);

        byte[] codeHash = randomBytes(32);
        RskAddress address = randomAddress();
        long id = 0;

        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getCodeHash(address)).thenReturn(new Keccak256(codeHash));

        CodeMessage expectedMessage = new CodeMessage(id, codeHash);

        ArgumentCaptor<CodeMessage> argument = forClass(CodeMessage.class);
        lightProcessor.processGetCodeMessage(id, blockHash.getBytes(), address.getBytes(), msgQueue);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetCodeMessageWithInvalidBlockHash() {
        long id = 100;
        RskAddress address = randomAddress();

        lightProcessor.processGetCodeMessage(id, blockHash.getBytes(), address.getBytes(), msgQueue);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processCodeMessageAndShouldThrowAnException() {
        long requestId = 0;
        byte[] codeHash = randomBytes(32);

        String expected = "Not supported Code processing";
        try {
            lightProcessor.processCodeMessage(requestId, codeHash, msgQueue);
        } catch (UnsupportedOperationException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void processGetAccountsMessageAndShouldReturnsAccountsCorrectly() {
        long id = 101;
        Coin balance = Coin.valueOf(1010);
        long nonce = 100;
        RskAddress address = randomAddress();
        final Block block = mock(Block.class);
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        Keccak256 codeHash = randomHash();
        byte[] storageRoot = randomHash().getBytes();
        AccountState accountState = mock(AccountState.class);

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getAccountState(address)).thenReturn(accountState);

        when(accountState.getNonce()).thenReturn(BigInteger.valueOf(nonce));
        when(accountState.getBalance()).thenReturn(balance);
        when(repositorySnapshot.getCodeHash(address)).thenReturn(codeHash);
        when(repositorySnapshot.getRoot()).thenReturn(storageRoot);

        AccountsMessage expectedMessage = new AccountsMessage(id, new byte[] {0x00}, nonce,
                balance.asBigInteger().longValue(), codeHash.getBytes(), storageRoot);

        ArgumentCaptor<AccountsMessage> argument = forClass(AccountsMessage.class);
        lightProcessor.processGetAccountsMessage(id, blockHash.getBytes(), address.getBytes(), msgQueue);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetAccountsMessageWithInvalidBlockHash() {
        long requestId = 100;
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);
        byte[] addressHash = HashUtil.randomHash();

        lightProcessor.processGetAccountsMessage(requestId, blockHash.getBytes(), addressHash, msgQueue);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processAccountsMessageAndShouldThrowAnException() {
        long id = 1;
        byte [] merkleInclusionProof = new byte[] {0x01};
        long nonce = 123;
        long balance = 100;
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = HashUtil.randomHash();

        String expected = "Not supported AccountsMessage processing";
        try {
            lightProcessor.processAccountsMessage(id, merkleInclusionProof, nonce, balance, codeHash, storageRoot, msgQueue);
        } catch (UnsupportedOperationException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void processGetBlockHeaderMessageAndShouldReturnsBlockHeaderCorrectly() {
        final Block block = mock(Block.class);
        long requestId = 100;
        BlockHeader blockHeader = mock(BlockHeader.class);
        byte[] blockHeaderHash = randomHash().getBytes();

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHeader()).thenReturn(blockHeader);
        when(blockHeader.getFullEncoded()).thenReturn(blockHeaderHash);

        BlockHeaderMessage expectedMessage = new BlockHeaderMessage(requestId, blockHeader);

        ArgumentCaptor<BlockHeaderMessage> argument = forClass(BlockHeaderMessage.class);
        lightProcessor.processGetBlockHeaderMessage(requestId, blockHash.getBytes(), msgQueue);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetBlockHeaderMessageWithInvalidBlockHash() {
        long requestId = 100;
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);

        lightProcessor.processGetBlockHeaderMessage(requestId, blockHash.getBytes(), msgQueue);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processBlockHeaderMessageAndShouldThrowAnException() {
        long requestId = 0;
        BlockHeader blockHeader = mock(BlockHeader.class);

        String expected = "Not supported BlockHeader processing";
        try {
            lightProcessor.processBlockHeaderMessage(requestId, blockHeader, msgQueue);
        } catch (UnsupportedOperationException e) {
            assertEquals(expected, e.getMessage());
        }
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