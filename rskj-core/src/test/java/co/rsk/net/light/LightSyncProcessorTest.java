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
import co.rsk.net.light.message.BlockHeadersMessage;
import co.rsk.net.light.message.GetBlockHeadersMessage;
import co.rsk.net.light.message.StatusMessage;
import co.rsk.validators.ProofOfWorkRule;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
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

import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;


public class LightSyncProcessorTest {

    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightStatus lightStatus;
    private StatusMessage statusMessage;
    private long requestId;
    private Keccak256 blockHash;
    private ProofOfWorkRule proofOfWorkRule;
    private LightPeersInformation lightPeersInformation;
    private Keccak256 bestBlockHash;

    @Before
    public void setUp() {
        //Light Sync Processor
        Genesis genesis = mock(Genesis.class);
        Keccak256 genesisHash = new Keccak256(HashUtil.randomHash());
        when(genesis.getHash()).thenReturn(genesisHash);

        Blockchain blockchain = mock(Blockchain.class);
        proofOfWorkRule = mock(ProofOfWorkRule.class);
        lightPeersInformation = new LightPeersInformation();
        lightSyncProcessor = new LightSyncProcessor(mock(SystemProperties.class), genesis, mock(BlockStore.class), blockchain, proofOfWorkRule, lightPeersInformation);

        //Light peer
        Channel channel = mock(Channel.class);
        MessageQueue messageQueue = mock(MessageQueue.class);
        lightPeer = spy(new LightPeer(channel, messageQueue));

        //Light peer status
        long bestNumber = 1;
        int networkId = 0;
        byte protocolVersion = (byte) 0;
        BigInteger peerStatusTotalDifficulty = BigInteger.TEN;
        BlockDifficulty blockDifficulty = new BlockDifficulty(peerStatusTotalDifficulty);
        blockHash = new Keccak256(HashUtil.randomHash());
        lightStatus = new LightStatus(protocolVersion, networkId, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());

        //Current status
        BlockChainStatus blockChainStatus = mock(BlockChainStatus.class);
        when(blockchain.getStatus()).thenReturn(blockChainStatus);
        BlockDifficulty totalDifficulty = BlockDifficulty.ONE;
        when(blockChainStatus.getTotalDifficulty()).thenReturn(totalDifficulty);
        when(blockChainStatus.hasLowerDifficultyThan(lightStatus)).thenReturn(totalDifficulty.compareTo(blockDifficulty) < 0);
        Block bestBlock = mock(Block.class);
        bestBlockHash = new Keccak256(randomHash());
        when(bestBlock.getHash()).thenReturn(bestBlockHash);
        when(bestBlock.getNumber()).thenReturn(1L);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        //lastRequestId in a new LightSyncProcessor starts in zero.
        requestId = 0;
        statusMessage = new StatusMessage(requestId, lightStatus, false);
    }

    @Test
    public void processStatusMessageAndShouldAskForAndReceiveBlockHeaderCorrectly() {

        LightProcessor lightProcessor = mock(LightProcessor.class);
        LightMessageHandler lightMessageHandler = new LightMessageHandler(lightProcessor, lightSyncProcessor);
        LightClientHandler lightClientHandler = new LightClientHandler(lightPeer, lightSyncProcessor, lightMessageHandler);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ChannelHandlerContext ctx = ch.pipeline().firstContext();

        //Message expected
        final int max = 1;
        final int skip = 0;
        final boolean reverse = true;
        GetBlockHeadersMessage expectedMessage = new GetBlockHeadersMessage(++requestId, bestBlockHash.getBytes(), max, skip, reverse);

        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer, ctx, lightClientHandler);
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());
        assertFalse(lightPeersInformation.hasTxRelay(lightPeer));

        //BlockHeader response

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(proofOfWorkRule.isValid(blockHeader)).thenReturn(true);
        byte[] fullEncodedBlockHeader = randomHash();
        when(blockHeader.getFullEncoded()).thenReturn(fullEncodedBlockHeader);
        List<BlockHeader> bHs = new ArrayList<>();
        bHs.add(blockHeader);

        BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage(requestId, bHs);

        lightMessageHandler.processMessage(lightPeer, blockHeadersMessage, ctx, lightClientHandler);

        assertEquals(1, lightPeer.getBlocks().size());
        assertEquals(bHs, lightPeer.getBlocks());
    }

    @Test
    public void peerWithTxRelayActivatedConnectCorrectly() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        //Message sent
        StatusMessage statusMessageWithTxRelaySet = new StatusMessage(requestId, lightStatus, true);

        lightSyncProcessor.processStatusMessage(statusMessageWithTxRelaySet, lightPeer, ctx, lightClientHandler);
        assertTrue(lightPeersInformation.hasTxRelay(lightPeer));
    }
}
