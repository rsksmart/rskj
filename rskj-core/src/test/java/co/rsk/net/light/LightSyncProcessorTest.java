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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.GetBlockHeadersByNumberMessage;
import co.rsk.net.light.message.LightClientMessage;
import co.rsk.net.light.message.StatusMessage;
import co.rsk.net.light.state.StartRoundSyncState;
import co.rsk.validators.ProofOfWorkRule;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static co.rsk.net.light.LightSyncProcessor.MAX_REQUESTED_HEADERS;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;


public class LightSyncProcessorTest {

    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightStatus lightPeerStatus;
    private StatusMessage statusMessage;
    private long requestId;
    private ProofOfWorkRule proofOfWorkRule;
    private LightPeersInformation lightPeersInformation;
    private Blockchain blockchain;

    @Before
    public void setUp() {
        //Light Sync Processor
        blockchain = mock(Blockchain.class);
        proofOfWorkRule = mock(ProofOfWorkRule.class);
        lightPeersInformation = new LightPeersInformation();

        Genesis genesis = getGenesis();
        lightSyncProcessor = spy(new LightSyncProcessor(mock(SystemProperties.class), genesis, mock(BlockStore.class), blockchain, proofOfWorkRule, lightPeersInformation));

        //Light peer
        Channel channel = mock(Channel.class);
        MessageQueue messageQueue = mock(MessageQueue.class);
        lightPeer = spy(new LightPeer(channel, messageQueue));

        //Peer status
        final BlockDifficulty peerBlockDifficulty = new BlockDifficulty(BigInteger.TEN);
        final long peerBestBlockNumber = 1;
        final Keccak256 peerBestBlockHash = new Keccak256(HashUtil.randomHash());
        this.lightPeerStatus = getLightStatus(genesis, peerBlockDifficulty, peerBestBlockNumber, peerBestBlockHash);


        //Current status
        final long myBestBlockNumber = 1L;
        final Keccak256 myBestBlockHash = new Keccak256(randomHash());
        final BlockDifficulty myTotalDifficulty = new BlockDifficulty(BigInteger.ONE);
        final Block myBestBlock = getBlock(myBestBlockHash, myBestBlockNumber);
        setupBlockChainStatus(blockchain, myTotalDifficulty, myBestBlock);

        //lastRequestId in a new LightSyncProcessor starts in zero.
        requestId = 0;
        statusMessage = new StatusMessage(requestId, lightPeerStatus, false);
    }

    @Test
    public void receiveInvalidPoWHeaderInMessageAndShouldBeIgnored() {
        long requestId = 1;

        List<BlockHeader> bHs = new ArrayList<>();
        BlockHeader blockHeader = getBlockHeader(new Keccak256(HashUtil.randomHash()), 1);
        bHs.add(blockHeader);


        when(proofOfWorkRule.isValid(blockHeader)).thenReturn(false);
        lightSyncProcessor.sendBlockHeadersByNumberMessage(lightPeer, 10L, 1, 0, false);
        processBlockHeaderAndVerifyDoesntSendMessage(bHs, requestId);
        verify(lightSyncProcessor, times(1)).invalidPoW(lightPeer);
    }

    @Test
    public void receiveNotPendingMessageAndShouldBeIgnored() {
        long requestId = 1;

        List<BlockHeader> bHs = new ArrayList<>();
        BlockHeader blockHeader = getBlockHeader(new Keccak256(HashUtil.randomHash()), 1);
        bHs.add(blockHeader);

        processBlockHeaderAndVerifyDoesntSendMessage(bHs, requestId);
        verify(lightSyncProcessor, times(1)).notPendingMessage(lightPeer);
    }

    @Test
    public void receiveMoreBlockHeadersThanAllowedMessageAndShouldBeIgnored() {
        long requestId = 1;

        List<BlockHeader> bHs = new ArrayList<>();
        for (int i = 0; i <= MAX_REQUESTED_HEADERS; i++) {
            final BlockHeader bh = mock(BlockHeader.class);
            bHs.add(bh);
        }

        lightSyncProcessor.sendBlockHeadersByNumberMessage(lightPeer, 10L, 1, 0, false);
        processBlockHeaderAndVerifyDoesntSendMessage(bHs, requestId);
        verify(lightSyncProcessor, times(1)).wrongBlockHeadersSize(lightPeer);
    }

    @Test
    public void receiveEmptyBlockHeadersListMessageAndShouldBeIgnored() {
        List<BlockHeader> bHs = new ArrayList<>();
        long requestId = 1;

        lightSyncProcessor.sendBlockHeadersByNumberMessage(lightPeer, 10L, 1, 0, false);
        processBlockHeaderAndVerifyDoesntSendMessage(bHs, requestId);
        verify(lightSyncProcessor, times(1)).wrongBlockHeadersSize(lightPeer);
    }

    @Test
    public void morePeersThanAllowedTryToConnectToMeAndShouldBeDiscarded() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        StatusMessage statusMessage = new StatusMessage(requestId, lightPeerStatus, false);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer, ctx, lightClientHandler);

        //Message sent
        LightPeer lightPeer2 = mock(LightPeer.class);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer2, ctx, lightClientHandler);

        verify(lightPeer, times(1)).sendMessage(any());
        verify(lightPeer2, times(0)).sendMessage(any());
    }

    @Test
    public void peerWithTxRelayActivatedConnectCorrectly() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        //Message sent
        StatusMessage statusMessageWithTxRelaySet = new StatusMessage(requestId, lightPeerStatus, true);

        lightSyncProcessor.processStatusMessage(statusMessageWithTxRelaySet, lightPeer, ctx, lightClientHandler);
        assertTrue(lightPeersInformation.hasTxRelay(lightPeer));
    }

    @Test
    public void peerWithoutTxRelayActivatedConnectCorrectly() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        //Message sent
        StatusMessage statusMessageWithTxRelaySet = new StatusMessage(requestId, lightPeerStatus, false);

        lightSyncProcessor.processStatusMessage(statusMessageWithTxRelaySet, lightPeer, ctx, lightClientHandler);
        assertFalse(lightPeersInformation.hasTxRelay(lightPeer));
    }

    @Test
    public void processStatusMessageAndShouldAskForCommonAncestor() {

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        //Message expected
        final int maxAmountOfHeaders = 1;
        final int skip = 0;
        final boolean reverse = true;
        final long myBestNumber = blockchain.getBestBlock().getNumber();
        GetBlockHeadersByNumberMessage expectedMessage = new GetBlockHeadersByNumberMessage(++requestId, myBestNumber, maxAmountOfHeaders, skip, reverse);

        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer, ctx, lightClientHandler);
        assertEqualMessage(expectedMessage);

        //BlockHeader response
        final Block peerBestBlock = getBlock(new Keccak256(lightPeerStatus.getBestHash()), lightPeerStatus.getBestNumber());
        when(proofOfWorkRule.isValid(peerBestBlock.getHeader())).thenReturn(true);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(peerBestBlock.getHeader());
        final BlockDifficulty lightPeerTotalDifficulty = lightPeerStatus.getTotalDifficulty();
        when(peerBestBlock.getHeader().getDifficulty()).thenReturn(lightPeerTotalDifficulty);
        when(blockchain.getBlockByHash(peerBestBlock.getHash().getBytes())).thenReturn(peerBestBlock);

        //Process peer's block headers, because it's known then transits to StartSyncRound
        lightSyncProcessor.processBlockHeadersMessage(requestId++, bHs, lightPeer);

        assertEquals(1, lightPeer.getBlocks().size());
        assertEquals(bHs, lightPeer.getBlocks());
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    private void assertEqualMessage(LightClientMessage expected) {
        ArgumentCaptor<LightClientMessage> argument = forClass(LightClientMessage.class);
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expected.getEncoded(), argument.getValue().getEncoded());
    }

    private void processBlockHeaderAndVerifyDoesntSendMessage(List<BlockHeader> bHs, long requestId) {
        lightSyncProcessor.processBlockHeadersMessage(requestId, bHs, lightPeer);
        verify(lightPeer, times(0)).receivedBlockHeaders(any());
    }

    private void setupBlockChainStatus(Blockchain blockchain, BlockDifficulty totalDifficulty, Block bestBlock) {
        BlockChainStatus blockChainStatus = mock(BlockChainStatus.class);
        when(blockchain.getStatus()).thenReturn(blockChainStatus);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);
        when(blockChainStatus.getTotalDifficulty()).thenReturn(totalDifficulty);
        final boolean isLower = totalDifficulty.compareTo(lightPeerStatus.getTotalDifficulty()) < 0;
        when(blockChainStatus.hasLowerDifficultyThan(lightPeerStatus)).thenReturn(isLower);
    }

    private Block getBlock(Keccak256 blockHash, long blockNumber) {
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHash()).thenReturn(blockHash);
        when(bestBlock.getNumber()).thenReturn(blockNumber);
        final BlockHeader blockHeader = getBlockHeader(blockHash, blockNumber);
        when(bestBlock.getHeader()).thenReturn(blockHeader);
        return bestBlock;
    }

    private LightStatus getLightStatus(Genesis genesis, BlockDifficulty blockDifficulty, long bestNumber, Keccak256 bestHash) {
        final LightStatus lightStatus = spy(new LightStatus((byte) 0, 0, blockDifficulty, bestHash.getBytes(), bestNumber, genesis.getHash().getBytes()));
        when(lightStatus.getTotalDifficulty()).thenReturn(blockDifficulty);
        when(lightStatus.getBestNumber()).thenReturn(bestNumber);
        when(lightStatus.getBestHash()).thenReturn(randomHash());
        return lightStatus;
    }

    private Genesis getGenesis() {
        Genesis genesis = mock(Genesis.class);
        when(genesis.getHash()).thenReturn(new Keccak256(HashUtil.randomHash()));
        return genesis;
    }

    private BlockHeader getBlockHeader(Keccak256 blockHash, long blockNumber) {
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getNumber()).thenReturn(blockNumber);
        return blockHeader;
    }
}
