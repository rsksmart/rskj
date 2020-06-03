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
    private LightProcessor lightProcessor;
    private Blockchain blockchain;
    private BlockStore blockStore;
    private RepositoryLocator repositoryLocator;
    private SystemProperties config;
    private Genesis genesis;
    private LightSyncProcessor lightSyncProcessor;
    private Keccak256 genesisHash;
    private Keccak256 blockHash;
    private LightPeer lightPeer;
    private ProofOfWorkRule proofOfWorkRule;

    @Before
    public void setup() {
        messageQueue = mock(MessageQueue.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        config = mock(SystemProperties.class);
        repositoryLocator = mock(RepositoryLocator.class);
        genesis = mock(Genesis.class);
        genesisHash = new Keccak256(HashUtil.randomHash());
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        proofOfWorkRule = mock(ProofOfWorkRule.class);
        LightPeersInformation lightPeersInformation = mock(LightPeersInformation.class);
        lightSyncProcessor = new LightSyncProcessor(config, genesis, blockStore, blockchain, proofOfWorkRule, lightPeersInformation);
        lightPeer = spy(new LightPeer(mock(Channel.class), messageQueue));
        LightClientHandler.Factory factory = (lightPeer) -> new LightClientHandler(lightPeer, lightProcessor, lightSyncProcessor);
        lightClientHandler = factory.newInstance(lightPeer);
        blockHash = new Keccak256(HashUtil.randomHash());


        when(genesis.getHash()).thenReturn(genesisHash);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ctx = ch.pipeline().firstContext();
    }

    @Test
    public void lightClientHandlerSendValidStatusMessage()   {
        Block bestBlock = mock(Block.class);
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        long bestNumber = 0L;
        int networkId = 0;
        byte protocolVersion = (byte) 0;
        BigInteger totalDifficulty = BigInteger.ONE;

        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getHash()).thenReturn(blockHash);
        when(bestBlock.getNumber()).thenReturn(bestNumber);
        when(blockStore.getTotalDifficultyForHash(blockHash.getBytes())).thenReturn(blockDifficulty);
        when(blockDifficulty.asBigInteger()).thenReturn(totalDifficulty);
        when(genesis.getHash()).thenReturn(genesisHash);
        when(config.networkId()).thenReturn(networkId);

        LightStatus status = new LightStatus(protocolVersion, networkId, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage statusMessage = new StatusMessage(0L, status, false);

        lightClientHandler.activate();

        ArgumentCaptor<StatusMessage> argument = forClass(StatusMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(statusMessage.getEncoded(), argument.getValue().getEncoded());

    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidProtocolVersion() throws Exception {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);

        when(genesis.getHash()).thenReturn(genesisHash);

        LightStatus status = new LightStatus((byte) 1, 0, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage m = new StatusMessage(0L, status, false);

        lightClientHandler.channelRead0(ctx, m);

        verify(messageQueue).disconnect(eq(ReasonCode.INCOMPATIBLE_PROTOCOL));
    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidNetworkId() throws Exception {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);

        when(genesis.getHash()).thenReturn(genesisHash);

        LightStatus status = new LightStatus((byte) 0, 55, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());
        StatusMessage m = new StatusMessage(0L, status, false);


        lightClientHandler.channelRead0(ctx, m);

        verify(messageQueue).disconnect(eq(ReasonCode.NULL_IDENTITY));
    }

    @Test
    public void lightClientHandlerProcessStatusWithInvalidGenesisHash() throws Exception {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        byte[] invalidHash = HashUtil.randomHash();

        LightStatus status = new LightStatus((byte) 0, 0, blockDifficulty, blockHash.getBytes(), bestNumber, invalidHash);
        StatusMessage m = new StatusMessage(0L, status, false);
        lightClientHandler.channelRead0(ctx, m);

        verify(messageQueue).disconnect(eq(ReasonCode.UNEXPECTED_GENESIS));
    }

    @Test
    public void lightClientHandlerProcessStatusWithHigherBlockDifficulty() {
        long bestNumber = 10L;
        BlockDifficulty blockDifficulty = BlockDifficulty.ZERO;
        Block bestBlock = mock(Block.class);
        BlockChainStatus blockChainStatus = new BlockChainStatus(bestBlock, BlockDifficulty.ONE);

        when(genesis.getHash()).thenReturn(genesisHash);
        when(blockchain.getStatus()).thenReturn(blockChainStatus);

        LightStatus status = new LightStatus((byte) 0, 0, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());

        StatusMessage m = new StatusMessage(0L, status, false);
        lightClientHandler.channelRead0(ctx, m);

        verify(messageQueue, times(0)).sendMessage(any());
    }

    @Test
    public void lightClientHandlerSendsGetBlockReceiptsToQueue() throws Exception {
        Block block = mock(Block.class);
        List<TransactionReceipt> receipts = new LinkedList<>();
        GetBlockReceiptsMessage m = new GetBlockReceiptsMessage(0, blockHash.getBytes());
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        BlockReceiptsMessage response = new BlockReceiptsMessage(0, receipts);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<BlockReceiptsMessage> argument = forClass(BlockReceiptsMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsBlockReceiptsToQueueAndShouldThrowAnException() throws Exception {
        List<TransactionReceipt> receipts = new LinkedList<>();
        BlockReceiptsMessage m = new BlockReceiptsMessage(0, receipts);
        lightClientHandler.channelRead0(ctx, m);
    }

    @Test
    public void lightClientHandlerSendsGetTransactionIndexToQueue() throws Exception {
        final Block block = mock(Block.class);
        Transaction tx = mock(Transaction.class);
        TransactionInfo transactionInfo = mock(TransactionInfo.class);

        Keccak256 txHash = new Keccak256(TestUtils.randomBytes(32));

        long id = 100;
        long blockNumber = 101;
        int txIndex = 42069;

        when(block.getHash()).thenReturn(blockHash);
        when(tx.getHash()).thenReturn(txHash);
        when(blockchain.getTransactionInfo(tx.getHash().getBytes())).thenReturn(transactionInfo);

        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(blockchain.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getNumber()).thenReturn(blockNumber);
        when(transactionInfo.getIndex()).thenReturn(txIndex);

        GetTransactionIndexMessage m = new GetTransactionIndexMessage(id, txHash.getBytes());
        TransactionIndexMessage response = new TransactionIndexMessage(id, blockNumber, blockHash.getBytes(), txIndex);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<TransactionIndexMessage> argument = forClass(TransactionIndexMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsTransactionIndexMessageToQueueAndShouldThrowAnException() throws Exception {
        TransactionIndexMessage m = new TransactionIndexMessage(2, 42, new byte[] {0x23}, 23);
        lightClientHandler.channelRead0(ctx, m);
    }

    @Test
    public void lightClientHandlerSendsGetCodeToQueue() throws Exception {
        BytecodeCompiler compiler = new BytecodeCompiler();
        byte[] bytecode = compiler.compile("PUSH1 0x01 PUSH1 0x02 ADD");
        RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        RskAddress address = TestUtils.randomAddress();
        Block block = mock(Block.class);


        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getCode(address)).thenReturn(bytecode);

        GetCodeMessage m = new GetCodeMessage(0, blockHash.getBytes(), address.getBytes());

        CodeMessage response = new CodeMessage(0, bytecode);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<CodeMessage> argument = forClass(CodeMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsCodeMsgToQueueAndShouldThrowAnException() throws Exception {
        byte[] codeHash = HashUtil.randomHash();
        CodeMessage m = new CodeMessage(0, codeHash);

        lightClientHandler.channelRead0(ctx, m);
    }

    @Test
    public void lightClientHandlerSendsGetAccountsToQueue() throws Exception {
        long id = 101;
        Keccak256 blockHash = randomHash();
        RskAddress address = randomAddress();
        final Block block = mock(Block.class);
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        Keccak256 codeHash = randomHash();
        byte[] storageRoot = randomHash().getBytes();
        AccountState accountState = mock(AccountState.class);
        Coin balance = Coin.valueOf(1010);
        long nonce = 100;

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getAccountState(address)).thenReturn(accountState);

        when(accountState.getNonce()).thenReturn(BigInteger.valueOf(nonce));
        when(accountState.getBalance()).thenReturn(balance);
        when(repositorySnapshot.getCodeHash(address)).thenReturn(codeHash);
        when(repositorySnapshot.getRoot()).thenReturn(storageRoot);

        GetAccountsMessage m = new GetAccountsMessage(id, blockHash.getBytes(), address.getBytes());

        AccountsMessage response = new AccountsMessage(id, new byte[] {0x00}, nonce,
                balance.asBigInteger().longValue(), codeHash.getBytes(), storageRoot);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<AccountsMessage> argument = forClass(AccountsMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendsAccountsMsgToQueueAndShouldThrowAnException() throws Exception {
        long id = 1;
        byte[] merkleInclusionProof = HashUtil.randomHash();
        long nonce = 123;
        long balance = 100;
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = HashUtil.randomHash();
        AccountsMessage m = new AccountsMessage(id, merkleInclusionProof, nonce, balance, codeHash, storageRoot);

        lightClientHandler.channelRead0(ctx, m);
    }

    @Test
    public void lightClientHandlerSendsGetBlockHeaderToQueue() throws Exception {
        Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        Block block = mock(Block.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(blockHeader);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHeader()).thenReturn(blockHeader);

        GetBlockHeadersMessage m = new GetBlockHeadersMessage(1, blockHash.getBytes(), 1, 0, false);

        lightClientHandler.channelRead0(ctx, m);

        BlockHeadersMessage response = new BlockHeadersMessage(1, bHs);

        ArgumentCaptor<BlockHeadersMessage> argument = forClass(BlockHeadersMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test
    public void receiveNotPendingMessageAndShouldBeIgnored() {

        BlockHeader blockHeader = mock(BlockHeader.class);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(blockHeader);
        long requestId = 0; //lastRequestId in a new LightSyncProcessor starts in zero.

        when(blockHeader.getHash()).thenReturn(blockHash);
        byte[] fullEncodedBlockHeader = randomHash().getBytes();
        when(blockHeader.getFullEncoded()).thenReturn(fullEncodedBlockHeader);

        BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage(requestId, bHs);

        lightClientHandler.channelRead0(ctx, blockHeadersMessage);

        verify(lightPeer, times(0)).receivedBlock(any());

    }

    @Test
    public void receiveInvalidPoWHeaderInMessageAndShouldBeIgnored() {

        BlockHeader blockHeader = mock(BlockHeader.class);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(blockHeader);
        long requestId = 0; //lastRequestId in a new LightSyncProcessor starts in zero.


        when(blockHeader.getHash()).thenReturn(blockHash);
        byte[] fullEncodedBlockHeader = randomHash().getBytes();
        when(blockHeader.getFullEncoded()).thenReturn(fullEncodedBlockHeader);

        when(proofOfWorkRule.isValid(blockHeader)).thenReturn(false);

        BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage(requestId, bHs);

        lightClientHandler.channelRead0(ctx, blockHeadersMessage);

        verify(lightPeer, times(0)).receivedBlock(any());

    }

    @Test
    public void receiveEmptyBlockHeadersListMessageAndShouldBeIgnored() {

        List<BlockHeader> bHs = new ArrayList<>();
        long requestId = 0; //lastRequestId in a new LightSyncProcessor starts in zero.

        BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage(requestId, bHs);

        lightClientHandler.channelRead0(ctx, blockHeadersMessage);

        verify(lightPeer, times(0)).receivedBlock(any());

    }

    @Test
    public void lightClientHandlerSendsGetBlockBodyToQueue() {
        Block block = mock(Block.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        Transaction transaction = mock(Transaction.class);
        Keccak256 blockHash = new Keccak256(HashUtil.randomHash());
        byte[] transactionEncoded = HashUtil.randomHash();
        byte[] blockHeaderEncoded = HashUtil.randomHash();
        long requestId = 10;

        LinkedList<BlockHeader> uncleList = new LinkedList<>();
        uncleList.add(blockHeader);

        LinkedList<Transaction> transactionList = new LinkedList<>();
        transactionList.add(transaction);

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getUncleList()).thenReturn(uncleList);
        when(block.getTransactionsList()).thenReturn(transactionList);
        when(blockHeader.getEncoded()).thenReturn(blockHeaderEncoded);
        when(blockHeader.getFullEncoded()).thenReturn(blockHeaderEncoded);
        when(transaction.getEncoded()).thenReturn(transactionEncoded);

        GetBlockBodyMessage m = new GetBlockBodyMessage(requestId, blockHash.getBytes());
        BlockBodyMessage response = new BlockBodyMessage(requestId, transactionList, uncleList);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<BlockBodyMessage> argument = forClass(BlockBodyMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendBlockBodyMsgToQueueAndShouldThrowAnException() throws Exception {
        long id = 0;
        LinkedList<Transaction> transactionList = new LinkedList<>();
        LinkedList<BlockHeader> uncleList = new LinkedList<>();
        BlockBodyMessage m = new BlockBodyMessage(id, transactionList, uncleList);

        lightClientHandler.channelRead0(ctx, m);
    }

    @Test
    public void lightClientHandlerSendsGetStorageToQueue() {
        long id = 0;
        final Block block = mock(Block.class);
        Keccak256 blockHash = randomHash();
        RskAddress address = randomAddress();
        DataWord storageKey = DataWord.valueOf(HashUtil.randomHash());
        final RepositorySnapshot repositorySnapshot = mock(RepositorySnapshot.class);
        byte[] storageValue = HashUtil.randomHash();

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getStorageBytes(address, storageKey)).thenReturn(storageValue);

        GetStorageMessage m = new GetStorageMessage(id, blockHash.getBytes(), address.getBytes(), storageKey.getData());
        StorageMessage response = new StorageMessage(id, new byte[] {0x00}, storageValue);

        lightClientHandler.channelRead0(ctx, m);

        ArgumentCaptor<StorageMessage> argument = forClass(StorageMessage.class);
        verify(messageQueue).sendMessage(argument.capture());
        assertArrayEquals(response.getEncoded(), argument.getValue().getEncoded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lightClientHandlerSendStorageMsgToQueueAndShouldThrowAnException() {
        long id = 0;
        byte[] storageValue = HashUtil.randomHash();

        StorageMessage m = new StorageMessage(id, new byte[] {0x00},storageValue);

        lightClientHandler.channelRead0(ctx, m);
    }

}
