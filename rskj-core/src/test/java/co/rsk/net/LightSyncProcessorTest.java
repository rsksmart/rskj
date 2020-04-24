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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.LightStatus;
import co.rsk.net.light.LightSyncProcessor;
import co.rsk.net.light.message.BlockHeaderMessage;
import co.rsk.net.light.message.GetBlockHeaderMessage;
import co.rsk.net.light.message.StatusMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.config.SystemProperties;
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

import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;


public class LightSyncProcessorTest {

    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightStatus lightStatus;
    private StatusMessage statusMessage;
    private long requestId;
    private Keccak256 blockHash;

    @Before
    public void setUp() {
        //Light Sync Processor
        Genesis genesis = mock(Genesis.class);
        Blockchain blockchain = mock(Blockchain.class);
        lightSyncProcessor = new LightSyncProcessor(mock(SystemProperties.class), genesis, mock(BlockStore.class), blockchain);

        //Light peer
        Channel channel = mock(Channel.class);
        MessageQueue messageQueue = mock(MessageQueue.class);
        lightPeer = spy(new LightPeer(channel, messageQueue));

        //Light peer status
        long bestNumber = 0;
        int networkId = 0;
        byte protocolVersion = (byte) 0;
        BigInteger peerStatusTotalDifficulty = BigInteger.TEN;
        BlockDifficulty blockDifficulty = new BlockDifficulty(peerStatusTotalDifficulty);
        blockHash = new Keccak256(HashUtil.randomHash());
        Keccak256 genesisHash = new Keccak256(HashUtil.randomHash());
        lightStatus = new LightStatus(protocolVersion, networkId, blockDifficulty, blockHash.getBytes(), bestNumber, genesisHash.getBytes());

        //Current status
        when(genesis.getHash()).thenReturn(genesisHash);
        BlockChainStatus blockChainStatus = mock(BlockChainStatus.class);
        when(blockchain.getStatus()).thenReturn(blockChainStatus);
        BlockDifficulty totalDifficulty = BlockDifficulty.ONE;
        when(blockChainStatus.getTotalDifficulty()).thenReturn(totalDifficulty);
        when(blockChainStatus.hasLowerDifficultyThan(lightStatus)).thenReturn(totalDifficulty.compareTo(blockDifficulty) < 0);

        //lastRequestId in a new LightSyncProcessor starts in zero.
        requestId = 0;
        statusMessage = new StatusMessage(requestId, lightStatus, false);
    }

    @Test
    public void processStatusMessageAndShouldAskForAndReceiveBlockHeaderCorrectly() {

        LightProcessor lightProcessor = mock(LightProcessor.class);
        LightClientHandler lightClientHandler = new LightClientHandler(lightPeer, lightProcessor, lightSyncProcessor);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ChannelHandlerContext ctx = ch.pipeline().firstContext();

        //Message expected
        GetBlockHeaderMessage expectedMessage = new GetBlockHeaderMessage(++requestId, blockHash.getBytes());

        ArgumentCaptor<GetBlockHeaderMessage> argument = forClass(GetBlockHeaderMessage.class);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer, ctx, lightClientHandler);
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expectedMessage.getEncoded(), argument.getValue().getEncoded());

        //BlockHeader response

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(blockHash);
        byte[] fullEncodedBlockHeader = randomHash();
        when(blockHeader.getFullEncoded()).thenReturn(fullEncodedBlockHeader);

        BlockHeaderMessage blockHeaderMessage = new BlockHeaderMessage(requestId, blockHeader);


        lightClientHandler.channelRead0(ctx, blockHeaderMessage);

        assertEquals(1, lightPeer.getBlocks().size());
        assertEquals(blockHeader.getFullEncoded(), lightPeer.getBlocks().get(0).getFullEncoded());
    }

    @Test
    public void morePeersThanAllowedTryToConnectToMeAndShouldBeDiscarded() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        LightClientHandler lightClientHandler = mock(LightClientHandler.class);

        //Message sent
        long requestId = 0; //lastRequestId in a new LightSyncProcessor starts in zero.
        StatusMessage statusMessage = new StatusMessage(requestId, lightStatus, false);

        LightPeer lightPeer2 = mock(LightPeer.class);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer, ctx, lightClientHandler);
        lightSyncProcessor.processStatusMessage(statusMessage, lightPeer2, ctx, lightClientHandler);

        verify(lightPeer, times(1)).sendMessage(any());
        verify(lightPeer2, times(0)).sendMessage(any());
    }


}
