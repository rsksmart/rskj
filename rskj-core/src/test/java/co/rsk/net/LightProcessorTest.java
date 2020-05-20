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
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.*;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.DataWord;
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
    private LightPeer lightPeer;

    @Before
    public void setup(){
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        repositoryLocator = mock(RepositoryLocator.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        msgQueue = spy(MessageQueue.class);
        blockHash = new Keccak256(HASH_1);
        lightPeer = new LightPeer(mock(Channel.class), msgQueue);
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
        lightProcessor.processGetBlockReceiptsMessage(requestId, block.getHash().getBytes(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetBlockReceiptMessageWithInvalidBlockHash() {
        lightProcessor.processGetBlockReceiptsMessage(0, blockHash.getBytes(), lightPeer);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processBlockReceiptMessageAndShouldThrowAnException() {
        long requestId = 0;
        List<TransactionReceipt> receipts = new LinkedList<>();
        lightProcessor.processBlockReceiptsMessage(requestId, receipts, lightPeer);
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
        lightProcessor.processGetTransactionIndex(id, txHash.getBytes(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetTransactionIndexMessageWithIncorrectBlockHash() {
        lightProcessor.processGetTransactionIndex(100, blockHash.getBytes(), lightPeer);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processTransactionIndexMessageAndShouldThrowAnException() {
        lightProcessor.processTransactionIndexMessage(0, 0, null, 0, lightPeer);
    }

    @Test
    public void processGetCodeMessageAndShouldReturnsCodeHashCorrectly() {
        final Block block = mock(Block.class);
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);

        BytecodeCompiler compiler = new BytecodeCompiler();
        byte[] bytecode = compiler.compile("PUSH1 0x01 PUSH1 0x02 ADD");
        RskAddress address = randomAddress();
        long id = 0;

        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getCode(address)).thenReturn(bytecode);

        CodeMessage expectedMessage = new CodeMessage(id, bytecode);

        ArgumentCaptor<CodeMessage> argument = forClass(CodeMessage.class);
        lightProcessor.processGetCodeMessage(id, blockHash.getBytes(), address.getBytes(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetCodeMessageWithInvalidBlockHash() {
        long id = 100;
        final Block block = mock(Block.class);
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        RskAddress address = randomAddress();

        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getCode(address)).thenReturn(null);

        lightProcessor.processGetCodeMessage(id, blockHash.getBytes(), address.getBytes(), lightPeer);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processGetCodeMessageWithNoCode() {
        long id = 100;
        RskAddress address = randomAddress();

        lightProcessor.processGetCodeMessage(id, blockHash.getBytes(), address.getBytes(), lightPeer);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processCodeMessageAndShouldThrowAnException() {
        long requestId = 0;
        byte[] codeHash = randomBytes(32);

        lightProcessor.processCodeMessage(requestId, codeHash, lightPeer);
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
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getAccountState(address)).thenReturn(accountState);

        when(accountState.getNonce()).thenReturn(BigInteger.valueOf(nonce));
        when(accountState.getBalance()).thenReturn(balance);
        when(repositorySnapshot.getCodeHash(address)).thenReturn(codeHash);
        when(repositorySnapshot.getRoot()).thenReturn(storageRoot);

        AccountsMessage expectedMessage = new AccountsMessage(id, new byte[] {0x00}, nonce,
                balance.asBigInteger().longValue(), codeHash.getBytes(), storageRoot);

        ArgumentCaptor<AccountsMessage> argument = forClass(AccountsMessage.class);
        lightProcessor.processGetAccountsMessage(id, blockHash.getBytes(), address.getBytes(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetAccountsMessageWithInvalidBlockHash() {
        long requestId = 100;
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);
        byte[] addressHash = HashUtil.randomHash();

        lightProcessor.processGetAccountsMessage(requestId, blockHash.getBytes(), addressHash, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processAccountsMessageAndShouldThrowAnException() {
        long id = 1;
        byte [] merkleInclusionProof = new byte[] {0x01};
        long nonce = 123;
        long balance = 100;
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = HashUtil.randomHash();

        lightProcessor.processAccountsMessage(id, merkleInclusionProof, nonce, balance, codeHash, storageRoot, lightPeer);
    }

    @Test
    public void processGetBlockHeadersMessageAndShouldReturnsBlockHeaderCorrectly() {
        long requestId = 100;

        final Block block = mock(Block.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        byte[] blockHeaderHash = randomHash().getBytes();

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHeader()).thenReturn(blockHeader);
        when(blockHeader.getFullEncoded()).thenReturn(blockHeaderHash);

        final Block block1 = mock(Block.class);
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        byte[] blockHeaderHash1 = randomHash().getBytes();
        Keccak256 blockHash1 = randomHash();

        when(block.getParentHash()).thenReturn(blockHash1);
        when(blockStore.getBlockByHash(blockHash1.getBytes())).thenReturn(block1);
        when(block1.getHeader()).thenReturn(blockHeader1);
        when(blockHeader1.getFullEncoded()).thenReturn(blockHeaderHash1);

        List<BlockHeader> blockHeaders = new ArrayList<>();
        blockHeaders.add(blockHeader);
        blockHeaders.add(blockHeader1);

        BlockHeadersMessage expectedMessage = new BlockHeadersMessage(requestId, blockHeaders);

        ArgumentCaptor<BlockHeadersMessage> argument = forClass(BlockHeadersMessage.class);
        lightProcessor.processGetBlockHeadersMessage(requestId, blockHash.getBytes(), 2, lightPeer);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetBlockHeadersMessageWithInvalidBlockHash() {
        long requestId = 100;
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);

        lightProcessor.processGetBlockHeadersMessage(requestId, blockHash.getBytes(), 1, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processGetBlockBodyMessageAndShouldReturnsBlockBodyCorrectly() {
        final Block block = mock(Block.class);
        Transaction transaction = mock(Transaction.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        byte[] blockHeaderHash = randomHash().getBytes();
        byte[] transactionHash = randomHash().getBytes();
        long requestId = 100;

        LinkedList<BlockHeader> uncleList = new LinkedList<>();
        uncleList.add(blockHeader);

        LinkedList<Transaction> transactionList = new LinkedList<>();
        transactionList.add(transaction);

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getUncleList()).thenReturn(uncleList);
        when(block.getTransactionsList()).thenReturn(transactionList);
        when(blockHeader.getFullEncoded()).thenReturn(blockHeaderHash);
        when(blockHeader.getEncoded()).thenReturn(blockHeaderHash);
        when(transaction.getEncoded()).thenReturn(transactionHash);

        BlockBodyMessage expectedMessage = new BlockBodyMessage(requestId, transactionList, uncleList);

        ArgumentCaptor<BlockBodyMessage> argument = forClass(BlockBodyMessage.class);
        lightProcessor.processGetBlockBodyMessage(requestId, blockHash.getBytes(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetBlockBodyMessageWithInvalidBlockHash() {
        long requestId = 100;
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);

        lightProcessor.processGetBlockBodyMessage(requestId, blockHash.getBytes(), lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processBlockBodyMessageAndShouldThrowAnException() {
        LinkedList<Transaction> transactionList = new LinkedList<>();
        LinkedList<BlockHeader> uncleList = new LinkedList<>();

        lightProcessor.processBlockBodyMessage(0, uncleList, transactionList,  lightPeer);
    }

    @Test
    public void processGetStorageMessageAndShouldReturnsBlockBodyCorrectly() {
        long id = 0;
        final Block block = mock(Block.class);
        RskAddress address = randomAddress();
        DataWord storageKey = DataWord.valueOf(HashUtil.randomHash());
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        byte[] storageValue = HashUtil.randomHash();

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getStorageBytes(address, storageKey)).thenReturn(storageValue);

        StorageMessage expectedMessage = new StorageMessage(id, new byte[] {0x00}, storageValue);

        ArgumentCaptor<StorageMessage> argument = forClass(StorageMessage.class);
        lightProcessor.processGetStorageMessage(id, blockHash.getBytes(), address.getBytes(),
                storageKey.getData(), lightPeer);
        verify(msgQueue).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void processGetStorageMessageWithInvalidBlockHash() {
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);

        lightProcessor.processGetStorageMessage(100, blockHash.getBytes(), randomAddress().getBytes(),
                new byte[] {0x00}, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processGetStorageMessageWithNullStorage() {
        final Block block = mock(Block.class);
        RskAddress address = randomAddress();
        DataWord storageKey = DataWord.valueOf(HashUtil.randomHash());
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getStorageBytes(address, storageKey)).thenReturn(null);

        lightProcessor.processGetStorageMessage(100, blockHash.getBytes(), randomAddress().getBytes(),
                new byte[] {0x00}, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void processStorageMessageAndShouldThrowAnException() {
        lightProcessor.processStorageMessage(0, new byte[] {0x00}, new byte[] {0x00}, lightPeer);
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