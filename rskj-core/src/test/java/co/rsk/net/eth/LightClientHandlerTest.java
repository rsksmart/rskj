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

package co.rsk.net.eth;

import co.rsk.core.Coin;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.light.*;
import co.rsk.net.light.message.*;
import co.rsk.vm.BytecodeCompiler;
import co.rsk.validators.ProofOfWorkRule;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.TestUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.DataWord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.ethereum.TestUtils.randomAddress;
import static org.ethereum.TestUtils.randomHash;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

public class LightClientHandlerTest {
    private MessageQueue messageQueue;
    private LightClientHandler lightClientHandler;
    private ChannelHandlerContext ctx;
    private Blockchain blockchain;
    private BlockStore blockStore;
    private RepositoryLocator repositoryLocator;
    private SystemProperties config;
    private Genesis genesis;
    private LightSyncProcessor lightSyncProcessor;
    private Keccak256 genesisHash;
    private LightPeer lightPeer;
    private LightMessageHandler lightMessageHandler;

    @Before
    public void setup() {
        genesisHash = new Keccak256(HashUtil.randomHash());
        messageQueue = mock(MessageQueue.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        config = mock(SystemProperties.class);
        repositoryLocator = mock(RepositoryLocator.class);
        genesis = mock(Genesis.class);
        lightSyncProcessor = new LightSyncProcessor(config, genesis, blockStore, blockchain, mock(ProofOfWorkRule.class), mock(LightPeersInformation.class));
        lightPeer = spy(new LightPeer(mock(Channel.class), messageQueue));
        LightClientHandler.Factory factory = (lightPeer) -> new LightClientHandler(lightPeer, lightSyncProcessor, lightMessageHandler);
        lightClientHandler = factory.newInstance(lightPeer);
        LightProcessor lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        lightMessageHandler = new LightMessageHandler(lightProcessor, lightSyncProcessor);


        when(genesis.getHash()).thenReturn(genesisHash);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ctx = ch.pipeline().firstContext();
    }

    @Test
    public void lightClientHandlerSendValidStatusMessage() {
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        int networkId = 0;
        byte protocolVersion = (byte) 0;
        BigInteger totalDifficulty = BigInteger.ONE;

        Block bestBlock = getMockedBestBlock();
        when(blockStore.getTotalDifficultyForHash(bestBlock.getHash().getBytes())).thenReturn(blockDifficulty);
        when(blockDifficulty.asBigInteger()).thenReturn(totalDifficulty);

        when(genesis.getHash()).thenReturn(genesisHash);
        when(config.networkId()).thenReturn(networkId);

        LightStatus status = new LightStatus(protocolVersion, networkId, blockDifficulty, bestBlock.getHash().getBytes(), bestBlock.getNumber(), genesisHash.getBytes());
        StatusMessage statusMessage = new StatusMessage(0L, status, false);

        lightClientHandler.activate();
        assertSendExpectedMessage(statusMessage);
    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidProtocolVersion() {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);

        when(genesis.getHash()).thenReturn(genesisHash);

        LightStatus status = new LightStatus((byte) 1, 0, blockDifficulty, new Keccak256(HashUtil.randomHash()).getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage m = new StatusMessage(0L, status, false);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);

        verify(messageQueue).disconnect(eq(ReasonCode.INCOMPATIBLE_PROTOCOL));
    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidNetworkId() {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);

        when(genesis.getHash()).thenReturn(genesisHash);

        LightStatus status = new LightStatus((byte) 0, 55, blockDifficulty, new Keccak256(HashUtil.randomHash()).getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage m = new StatusMessage(0L, status, false);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);

        verify(messageQueue).disconnect(eq(ReasonCode.NULL_IDENTITY));
    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidGenesisHash() {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        byte[] invalidHash = HashUtil.randomHash();

        LightStatus status = new LightStatus((byte) 0, 0, blockDifficulty, new Keccak256(HashUtil.randomHash()).getBytes(), bestNumber, invalidHash);
        StatusMessage m = new StatusMessage(0L, status, false);
        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);

        verify(messageQueue).disconnect(eq(ReasonCode.UNEXPECTED_GENESIS));
    }

    @Test
    public void lightClientHandlerProcessStatusWithHigherBlockDifficulty() {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = BlockDifficulty.ZERO;
        Block bestBlock = getMockedBestBlock();
        BlockChainStatus blockChainStatus = new BlockChainStatus(bestBlock, BlockDifficulty.ONE);

        when(genesis.getHash()).thenReturn(genesisHash);
        when(blockchain.getStatus()).thenReturn(blockChainStatus);

        LightStatus status = new LightStatus((byte) 0, 0, blockDifficulty, new Keccak256(HashUtil.randomHash()).getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage m = new StatusMessage(0L, status, false);
        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);

        verify(messageQueue, times(0)).sendMessage(any());
    }

    @Test
    public void lightClientHandlerSendsGetBlockReceiptsToQueue() {
        final Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        Block block = getMockedBlock(1, blockHash);
        List<TransactionReceipt> receipts = new LinkedList<>();
        GetBlockReceiptsMessage m = new GetBlockReceiptsMessage(0, block.getHash().getBytes());
        includeBlockInBlockchainAndBlockStore(block);
        BlockReceiptsMessage response = new BlockReceiptsMessage(0, receipts);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsBlockReceiptsToQueueAndShouldThrowAnException() {
        List<TransactionReceipt> receipts = new LinkedList<>();
        BlockReceiptsMessage m = new BlockReceiptsMessage(0, receipts);
        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    @Test
    public void lightClientHandlerSendsGetTransactionIndexToQueue() {
        final Transaction tx = mock(Transaction.class);
        final TransactionInfo transactionInfo = mock(TransactionInfo.class);
        final Keccak256 txHash = new Keccak256(TestUtils.randomBytes(32));
        final long id = 100;
        final int txIndex = 42069;
        final long blockNumber = 101;
        final Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        final Block block = getMockedBlock(blockNumber, blockHash);
        includeBlockInBlockchainAndBlockStore(block);

        when(tx.getHash()).thenReturn(txHash);
        when(transactionInfo.getIndex()).thenReturn(txIndex);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);

        GetTransactionIndexMessage m = new GetTransactionIndexMessage(id, txHash.getBytes());
        TransactionIndexMessage response = new TransactionIndexMessage(id, blockNumber, blockHash.getBytes(), txIndex);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);

        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsTransactionIndexMessageToQueueAndShouldThrowAnException() {
        TransactionIndexMessage m = new TransactionIndexMessage(2, 42, new byte[] {0x23}, 23);
        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    @Test
    public void lightClientHandlerSendsGetCodeToQueue() {
        BytecodeCompiler compiler = new BytecodeCompiler();
        byte[] bytecode = compiler.compile("PUSH1 0x01 PUSH1 0x02 ADD");
        RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        RskAddress address = TestUtils.randomAddress();

        final Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        Block block = getMockedBlock(1, blockHash);
        includeBlockInBlockchainAndBlockStore(block);

        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getCode(address)).thenReturn(bytecode);

        GetCodeMessage m = new GetCodeMessage(0, block.getHash().getBytes(), address.getBytes());
        CodeMessage response = new CodeMessage(0, bytecode);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsCodeMsgToQueueAndShouldThrowAnException() {
        byte[] codeHash = HashUtil.randomHash();
        CodeMessage m = new CodeMessage(0, codeHash);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    @Test
    public void lightClientHandlerSendsGetAccountsToQueue() {
        long id = 101;
        RskAddress address = randomAddress();
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        Keccak256 codeHash = randomHash();
        byte[] storageRoot = randomHash().getBytes();
        AccountState accountState = mock(AccountState.class);
        Coin balance = Coin.valueOf(1010);
        long nonce = 100;

        Keccak256 blockHash = randomHash();
        final Block block = getMockedBlock(1, blockHash);
        includeBlockInBlockchainAndBlockStore(block);

        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getAccountState(address)).thenReturn(accountState);
        when(accountState.getNonce()).thenReturn(BigInteger.valueOf(nonce));
        when(accountState.getBalance()).thenReturn(balance);
        when(repositorySnapshot.getCodeHash(address)).thenReturn(codeHash);
        when(repositorySnapshot.getRoot()).thenReturn(storageRoot);

        GetAccountsMessage m = new GetAccountsMessage(id, blockHash.getBytes(), address.getBytes());

        AccountsMessage response = new AccountsMessage(id, new byte[] {0x00}, nonce,
                balance.asBigInteger().longValue(), codeHash.getBytes(), storageRoot);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsAccountsMsgToQueueAndShouldThrowAnException() {
        long id = 1;
        byte[] merkleInclusionProof = HashUtil.randomHash();
        long nonce = 123;
        long balance = 100;
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = HashUtil.randomHash();
        AccountsMessage m = new AccountsMessage(id, merkleInclusionProof, nonce, balance, codeHash, storageRoot);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    @Test
    public void lightClientHandlerSendsGetBlockHeaderByHashToQueue() {
        Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        Block block = getMockedBlock(1, blockHash);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(block.getHeader());
        includeBlockInBlockchainAndBlockStore(block);

        GetBlockHeadersByHashMessage m = new GetBlockHeadersByHashMessage(1, blockHash.getBytes(), 1, 0, false);
        BlockHeadersMessage response = new BlockHeadersMessage(1, bHs);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test
    public void lightClientHandlerSendsGetBlockHeaderByNumberToQueue() {
        long blockNumber = 10L;
        Block block = getMockedBlock(blockNumber, randomHash());
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(block.getHeader());
        includeBlockInBlockchainAndBlockStore(block);

        GetBlockHeadersByNumberMessage m = new GetBlockHeadersByNumberMessage(1, blockNumber, 1, 0, false);
        BlockHeadersMessage response = new BlockHeadersMessage(1, bHs);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test
    public void lightClientHandlerSendsGetBlockBodyToQueue() {
        final Transaction transaction = mock(Transaction.class);
        final Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        final Block block = getMockedBlock(1, blockHash);
        includeBlockInBlockchainAndBlockStore(block);
        final BlockHeader blockHeader = block.getHeader();
        final byte[] transactionEncoded = HashUtil.randomHash();
        final long requestId = 10;

        LinkedList<BlockHeader> uncleList = new LinkedList<>();
        uncleList.add(blockHeader);

        LinkedList<Transaction> transactionList = new LinkedList<>();
        transactionList.add(transaction);
        when(block.getUncleList()).thenReturn(uncleList);
        when(block.getTransactionsList()).thenReturn(transactionList);
        when(transaction.getEncoded()).thenReturn(transactionEncoded);

        GetBlockBodyMessage m = new GetBlockBodyMessage(requestId, blockHash.getBytes());
        BlockBodyMessage response = new BlockBodyMessage(requestId, transactionList, uncleList);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendBlockBodyMsgToQueueAndShouldThrowAnException() {
        final long id = 0;
        final LinkedList<Transaction> transactionList = new LinkedList<>();
        final LinkedList<BlockHeader> uncleList = new LinkedList<>();
        BlockBodyMessage m = new BlockBodyMessage(id, transactionList, uncleList);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    @Test
    public void lightClientHandlerSendsGetStorageToQueue() {
        long id = 0;
        Keccak256 blockHash = randomHash();
        final Block block = getMockedBlock(1, blockHash);
        includeBlockInBlockchainAndBlockStore(block);
        RskAddress address = randomAddress();
        DataWord storageKey = DataWord.valueOf(HashUtil.randomHash());
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        byte[] storageValue = HashUtil.randomHash();

        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getStorageBytes(address, storageKey)).thenReturn(storageValue);

        GetStorageMessage m = new GetStorageMessage(id, blockHash.getBytes(), address.getBytes(), storageKey.getData());
        StorageMessage response = new StorageMessage(id, new byte[] {0x00}, storageValue);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
        assertSendExpectedMessage(response);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendStorageMsgToQueueAndShouldThrowAnException() {
        long id = 0;
        byte[] storageValue = HashUtil.randomHash();

        StorageMessage m = new StorageMessage(id, new byte[] {0x00},storageValue);

        lightMessageHandler.processMessage(lightPeer, m, ctx, lightClientHandler);
    }

    private void includeBlockInBlockchainAndBlockStore(Block block) {
        final Keccak256 blockHash = block.getHash();
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block);
        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockchain.getBlockByNumber(block.getNumber())).thenReturn(block);
    }

    private void assertSendExpectedMessage(LightClientMessage expected) {
        ArgumentCaptor<? extends LightClientMessage> argument = forClass(expected.getClass());
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(expected.getEncoded(), argument.getValue().getEncoded());
    }

    private Block getMockedBlock(long number, Keccak256 hash) {
        Block block = mock(Block.class);
        BlockHeader bHeader = getMockedBlockHeader(number, hash, HashUtil.randomHash());
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getHeader()).thenReturn(bHeader);
        return block;
    }

    private BlockHeader getMockedBlockHeader(long number, Keccak256 hash, byte[] blockHeaderEncoded) {
        BlockHeader bHeader = mock(BlockHeader.class);
        when(bHeader.getNumber()).thenReturn(number);
        when(bHeader.getHash()).thenReturn(hash);
        when(bHeader.getEncoded()).thenReturn(blockHeaderEncoded);
        when(bHeader.getFullEncoded()).thenReturn(blockHeaderEncoded);
        return bHeader;
    }

    private Block getMockedBestBlock() {
        final long bestNumber = 0;
        Block bestBlock = getMockedBlock(bestNumber, new Keccak256(HashUtil.randomHash()));
        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        return bestBlock;
    }
}
