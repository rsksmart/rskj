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

package co.rsk.net.light;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
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
    private LightPeer lightPeer;

    @Before
    public void setup(){
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        repositoryLocator = mock(RepositoryLocator.class);
        lightProcessor = spy(new LightProcessor(blockchain, blockStore, repositoryLocator));
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
    public void processGetBlockHeadersMessageWithNoSkipNoReverseAndShouldReturnsBlockHeaderCorrectly() {
        final long requestId = 100;
        final int max = 2;
        final int skip = 0;

        //Starting block
        final long startBlockNumber = 1;
        final byte[] startBlockHash = randomHash().getBytes();
        final Block startBlock = createMockedBlockAndIncludeInBlockchain(startBlockNumber, startBlockHash);


        //Second block
        final int secondBlockNumber = 2;
        byte[] secondBlockHeaderHash = randomHash().getBytes();
        final Block secondBlock = createMockedBlockAndIncludeInBlockchain(secondBlockNumber, secondBlockHeaderHash);

        //Best block of header server
        mockBestBlock();

        //Result expected
        List<BlockHeader> blockHeaders = new ArrayList<>();
        blockHeaders.add(startBlock.getHeader());
        blockHeaders.add(secondBlock.getHeader());

        BlockHeadersMessage expectedMessage = new BlockHeadersMessage(requestId, blockHeaders);
        ArgumentCaptor<BlockHeadersMessage> argument = forClass(BlockHeadersMessage.class);

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, startBlockHash, max, skip, false, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, startBlockNumber, max, skip, false, lightPeer);

        verify(msgQueue, times(2)).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(0).getEncoded());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(1).getEncoded());
        assertEquals(expectedMessage.getBlockHeaders(), argument.getAllValues().get(0).getBlockHeaders());
        assertEquals(expectedMessage.getBlockHeaders(), argument.getAllValues().get(1).getBlockHeaders());

    }

    @Test
    public void processGetBlockHeaderMessageWithReverseNoSkipReturnsEmptyBlockHeaderListAndShouldntBeProcessed() {
        final boolean reverse = true;
        final long requestId = 100;
        final int max = 2;
        final int skip = 0;

        //Starting block
        final long startBlockNumber = 0;
        final byte[] startBlockHash = randomHash().getBytes();
        final Block startBlock = createMockedBlockAndIncludeInBlockchain(startBlockNumber, startBlockHash);

        //Best block of header server
        Block bestBlock = mockBestBlock();

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, startBlock.getHash().getBytes(), max, skip, reverse, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, startBlockNumber, max, skip, reverse, lightPeer);

        verify(lightProcessor, times(2)).getBlockNumbersToResponse(max, skip, reverse, startBlockNumber, bestBlock);
        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processGetBlockHeadersByHashWithOneRequestedBlockHeaderAndNoReverseNoSkipShouldReturnsBlockHeaderCorrectly() {
        final long requestId = 100;
        final int max = 1;
        final int skip = 0;

        //Starting block

        final long startBlockNumber = 1;
        final byte[] startBlockHeaderHash = randomHash().getBytes();
        final Block startBlock = createMockedBlockAndIncludeInBlockchain(startBlockNumber, startBlockHeaderHash);

        //Best block of header server
        mockBestBlock();

        //Result expected

        List<BlockHeader> blockHeaders = new ArrayList<>();
        blockHeaders.add(startBlock.getHeader());

        BlockHeadersMessage expectedMessage = new BlockHeadersMessage(requestId, blockHeaders);
        ArgumentCaptor<BlockHeadersMessage> argument = forClass(BlockHeadersMessage.class);

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, startBlock.getHash().getBytes(), max, skip, false, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, startBlock.getNumber(), max, skip, false, lightPeer);

        verify(msgQueue, times(2)).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(0).getEncoded());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(1).getEncoded());
    }

    @Test
    public void processWithNoSkipReverseGetBlockHeadersMessageAndShouldReturnsBlockHeaderCorrectly() {
        final long requestId = 100;
        final int skip = 0;
        final int max = 2;

        //Starting block
        final long startBlockNumber = 1;
        byte[] startBlockHash = randomHash().getBytes();
        final Block startBlock = createMockedBlockAndIncludeInBlockchain(startBlockNumber, startBlockHash);

        //Best block of header server
        mockBestBlock();

        //Result expected (In reverse search zero block number is not included because it belongs to Genesis)
        List<BlockHeader> blockHeaders = new ArrayList<>();
        blockHeaders.add(startBlock.getHeader());

        BlockHeadersMessage expectedMessage = new BlockHeadersMessage(requestId, blockHeaders);
        ArgumentCaptor<BlockHeadersMessage> argument = forClass(BlockHeadersMessage.class);

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, startBlockHash, max, skip, true, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, startBlockNumber, max, skip, true, lightPeer);

        verify(msgQueue, times(2)).sendMessage(argument.capture());

        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(0).getEncoded());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getAllValues().get(1).getEncoded());
    }

    @Test
    public void processGetBlockHeadersMessageWithZeroMaxShouldNotBeProcessed() {
        final long requestId = 100;
        final int skip = 0;
        final int blockNumber = 3;
        final byte[] blockHash = randomHash().getBytes();

        createMockedBlockAndIncludeInBlockchain(blockNumber, blockHash);

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, blockHash, 0, skip, false, lightPeer);
        lightProcessor.processGetBlockHeadersByHashMessage(requestId, blockHash, 0, skip, true, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, blockNumber, 0, skip, false, lightPeer);
        lightProcessor.processGetBlockHeadersByNumberMessage(requestId, blockNumber, 0, skip, true, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void processGetBlockHeadersMessageWithInvalidStartingBlockHashShouldNotBeProcessed() {
        long requestId = 100;
        int skip = 0;

        //This is just for made the test more readable
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(null);
        when(blockStore.getChainBlockByNumber(0)).thenReturn(null);

        lightProcessor.processGetBlockHeadersByHashMessage(requestId, blockHash.getBytes(), 1, skip, false, lightPeer);
        lightProcessor.processGetBlockHeadersByHashMessage(requestId, blockHash.getBytes(), 1, skip, true, lightPeer);

        verify(msgQueue, times(0)).sendMessage(any());
    }

    @Test
    public void getBlockNumberToResponseNoReverseTest() {
        int max = 10;
        int skip = 2;
        long startNumber = 0;
        final int startingFrom = 0;
        final Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(50L);

        List<BlockHeader> blockHeadersExpected = getBlockHeadersMocked(max, skip, startingFrom); // {0,3,6,9,12,15,18,21,24,27} skipped by 2
        List<BlockHeader> blockHeaders = lightProcessor.getBlockNumbersToResponse(max, skip, false, startNumber, bestBlock);

        assertEquals(blockHeadersExpected, blockHeaders);
    }

    @Test
    public void getBlockNumberToResponseReverseTest() {
        int max = 10;
        int skip = 2;
        long startNumber = 27;
        final Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(50L);
        final int startingFrom = 1;

        List<BlockHeader> blockHeadersExpected = getBlockHeadersMocked(max, skip, startingFrom); // {0,3,6,9,12,15,18,21,24,27} skipped by 2
        List<BlockHeader> blockHeaders = lightProcessor.getBlockNumbersToResponse(max, skip, true, startNumber, bestBlock);

        assertEquals(blockHeadersExpected.size(), blockHeaders.size());
        for (int i = 1; i < blockHeaders.size(); i++) {
            assertEquals(blockHeadersExpected.get(blockHeaders.size() - i - 1), blockHeaders.get(i));
        }
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

    private Block mockBestBlock() {
        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn((long) 50);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        return bestBlock;
    }

    private Block createMockedBlockAndIncludeInBlockchain(long blockNumber, byte[] blockHash) {
        final Block block = mock(Block.class);
        final BlockHeader startBlockHeader = mock(BlockHeader.class);

        when(block.getHeader()).thenReturn(startBlockHeader);
        when(block.getNumber()).thenReturn(blockNumber);
        when(block.getHash()).thenReturn(new Keccak256(blockHash));
        when(startBlockHeader.getFullEncoded()).thenReturn(randomHash().getBytes());

        includeBlockInBlockchain(block);

        return block;
    }

    private void includeBlockInBlockchain(Block block) {
        when(blockStore.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block);
    }

    private List<BlockHeader> getBlockHeadersMocked(int max, int skip, int startingFrom) {
        List<BlockHeader> blockHeadersExpected = new ArrayList<>();
        for (int i = startingFrom; i < max; i++) {
            final Block block = mock(Block.class);
            final BlockHeader blockHeader = mock(BlockHeader.class);
            long blockNumber = i * (skip + 1);
            when(block.getHeader()).thenReturn(blockHeader);
            when(blockStore.getChainBlockByNumber(blockNumber)).thenReturn(block);
            when(block.getNumber()).thenReturn(blockNumber);
            blockHeadersExpected.add(blockHeader);
        }
        return blockHeadersExpected;
    }
}
