/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.crypto.Keccak256;
import co.rsk.net.light.message.GetBlockHeadersByHashMessage;
import co.rsk.net.light.message.GetBlockHeadersByNumberMessage;
import co.rsk.net.light.message.GetBlockHeadersMessage;
import co.rsk.net.light.state.*;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
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

public class LightSyncStateTest {

    private static final long TARGET_BLOCK_NUMBER = 10;
    private static final long LONG_TARGET_BLOCK_NUMBER = 250;
    private static final int MAX_REQUESTED_HEADERS = 192;
    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightPeer longLightPeer;
    private ProofOfWorkRule powRule;
    private Blockchain blockchain;

    @Before
    public void setUp() {
        powRule = mock(ProofOfWorkRule.class);
        blockchain = mock(Blockchain.class);
        lightPeer = mock(LightPeer.class);
        LightStatus lightStatus = mock(LightStatus.class);
        setupBestPeerStatus(lightStatus, TARGET_BLOCK_NUMBER);

        longLightPeer = mock(LightPeer.class);
        LightStatus longLightStatus = mock(LightStatus.class);
        setupBestPeerStatus(longLightStatus, LONG_TARGET_BLOCK_NUMBER);

        LightPeersInformation lightPeersInformation = new LightPeersInformation();
        lightPeersInformation.registerLightPeer(lightPeer, lightStatus, false);
        lightPeersInformation.registerLightPeer(longLightPeer, longLightStatus, false);

        lightSyncProcessor = spy(getLightSyncProcessor(powRule, blockchain, lightPeersInformation));
    }

    @Test
    public void createLightSyncProcessorAndStateShouldBeIdle() {
        final LightSyncState syncState = lightSyncProcessor.getSyncState();
        assertEquals(syncState.getClass(), IdleSyncState.class);
    }

    @Test
    public void decidingLightSyncStateShouldTransitToSearchCommonAncestorIfBestBlockIsDifferentOfZero() {
        final BlockHeader bestBlockHeader = getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final DecidingLightSyncState decidingLightSyncState = new DecidingLightSyncState(lightSyncProcessor, lightPeer, bestBlockHeader);
        decidingLightSyncState.sync();

        assertEquals(lightSyncProcessor.getSyncState().getClass(), CommonAncestorSearchSyncState.class);
    }

    //This test will be changed for transit to StartRoundSyncState with genesis as start block
    @Test
    public void decidingLightSyncStateShouldNotTransitToSearchCommonAncestorIfBestBlockIsZero() {
        final BlockHeader bestBlockHeader = getBlockHeader(0L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final DecidingLightSyncState decidingLightSyncState = new DecidingLightSyncState(lightSyncProcessor, lightPeer, bestBlockHeader);
        decidingLightSyncState.sync();

        assertNotEquals(lightSyncProcessor.getSyncState().getClass(), CommonAncestorSearchSyncState.class);
    }

    @Test
    public void commonAncestorSearchShouldRequestABlockHeaderToSync() {
        final BlockHeader bestBlockHeader = getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlockHeader.getHash().getBytes(), bestBlockHeader.getNumber(), blockchain);
        syncState.sync();

        final GetBlockHeadersByHashMessage expectedMsg = new GetBlockHeadersByHashMessage(1, bestBlockHeader.getHash().getBytes(), 1, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersByHashMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void commonAncestorStateReceiveNotKnownHeadersAndShouldAskForSuccessors() {
        final BlockHeader startBlockHeader = getBlockHeader(5L, new Keccak256(randomHash()), BlockDifficulty.ONE);

        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, startBlockHeader.getHash().getBytes(), startBlockHeader.getNumber(), blockchain);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        long newStartNumber = startBlockHeader.getNumber() - bhs.size();
        final BlockHeader newStartBlock = getBlockHeader(newStartNumber, new Keccak256(randomHash()), BlockDifficulty.ONE);
        includeBlockInBlockchain(newStartBlock);
        syncState.newBlockHeaders(lightPeer, bhs);

        final GetBlockHeadersByHashMessage expectedMsg = new GetBlockHeadersByHashMessage(1, newStartBlock.getHash().getBytes(), 4, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersByHashMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void startNumberIsOverMaxRequestedHeadersAndShouldRequestMaxQuantity() {
        final BlockHeader bestBlockHeader = getBlockHeader(200L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlockHeader.getHash().getBytes(), bestBlockHeader.getNumber(), blockchain);
        syncState.sync();

        final GetBlockHeadersByHashMessage expectedMsg = new GetBlockHeadersByHashMessage(1, bestBlockHeader.getHash().getBytes(), MAX_REQUESTED_HEADERS, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersByHashMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void peerInCommonAncestorStateReceivesABlockWithLowDifficulty() {
        final BlockHeader startBlockHeader = getBlockHeader(5L, new Keccak256(randomHash()), new BlockDifficulty(BigInteger.valueOf(50)));
        includeBlockInBlockchain(startBlockHeader);

        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, startBlockHeader.getHash().getBytes(), startBlockHeader.getNumber(), blockchain);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        syncState.newBlockHeaders(lightPeer, bhs);
        verify(lightSyncProcessor, times(1)).wrongDifficulty();
    }

    @Test
    public void commonAncestorStateReceiveKnownHeadersAndShouldTransitToStartRoundSyncState() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        includeBlockInBlockchain(startBlockHeader);

        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, startBlockHeader.getHash().getBytes(), startBlockHeader.getNumber(), blockchain);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        syncState.newBlockHeaders(lightPeer, bhs);

        GetBlockHeadersByNumberMessage expectedMsg = new GetBlockHeadersByNumberMessage(1, 2, 9, 0, false);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersByNumberMessage.class);
        assertSendSameMessage(expectedMsg, argument);
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesMoreThanMaxAmountOfBlockHeadersAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, (int) TARGET_BLOCK_NUMBER, 0, false);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).moreBlocksThanAllowed();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesADifferentStartingBlockHeaderAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(3, 1, 0, false);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).differentFirstBlocks();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesABadSkippedListOfHeadersAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 0, false);
        blockHeaders.add(getBlockHeader(14L, new Keccak256(randomHash()), BlockDifficulty.ONE));

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectSkipped();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesListOfHeadersUnsortedAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 0, false);
        blockHeaders.add(getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE));

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectSkipped();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesAHeaderWithIncorrectParentHashAndShouldNotProcessIt() {
        final Keccak256 startBlockHash = Keccak256.ZERO_HASH;
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(2, (int) TARGET_BLOCK_NUMBER-1, 0, false));
        when(blockHeaders.get(0).getParentHash()).thenReturn(new Keccak256(randomHash()));
        includeBlockInBlockchain(startBlockHeader);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectParentHash();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundReceivesHeadersCorrectlyWithZeroSkipAndShouldEnd() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(2, Math.toIntExact(TARGET_BLOCK_NUMBER - 1), 0, false));
        when(blockHeaders.get(0).getParentHash()).thenReturn(startBlockHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).endStartRound();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundGetLesserHeadersThanAsked() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 192, false);
        when(blockHeaders.get(0).getParentHash()).thenReturn(startBlockHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, longLightPeer);

        verify(lightSyncProcessor, times(1)).failedAttempt();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void peerInStartRoundDoesntGetTheTargetAndShouldAskMoreHeaders() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 2, 192, false);
        when(blockHeaders.get(0).getParentHash()).thenReturn(startBlockHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, longLightPeer);

        verify(lightSyncProcessor, times(1)).startFetchRound();
        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void anEntireProcessSinceLightSyncProcessorStartsUntilItFindsACommonBlock() {
        int requestId = 1;
        final int bestBlockNumber = 195;
        final Keccak256 bestBlockHash = new Keccak256(randomHash());
        final BlockHeader bestKnownHeader = getBlockHeader(bestBlockNumber, bestBlockHash, BlockDifficulty.ONE);

        //Send BlockHeadersRequest
        lightSyncProcessor.startSync(lightPeer, bestKnownHeader);
        final GetBlockHeadersByHashMessage expectedMsg1 = new GetBlockHeadersByHashMessage(requestId, bestKnownHeader.getHash().getBytes(), MAX_REQUESTED_HEADERS, 0, true);

        //This should be the peer's response
        final List<BlockHeader> firstBlockHeaderList = getBlockHeaders(bestBlockNumber, MAX_REQUESTED_HEADERS, 0, true);

        //Process received block's headers and request for the rest
        final int newStartBlockNumber = bestBlockNumber - MAX_REQUESTED_HEADERS;
        final Keccak256 newStartBlockHash = new Keccak256(randomHash());
        final BlockHeader newStartBlockHeader = getBlockHeader(newStartBlockNumber, newStartBlockHash, BlockDifficulty.ONE);
        includeBlockInBlockchain(newStartBlockHeader);
        lightSyncProcessor.processBlockHeadersMessage(requestId, firstBlockHeaderList, lightPeer);

        //Process response and request for pivots
        final long secondRequestId = requestId+1;
        GetBlockHeadersByHashMessage expectedMsg2 = new GetBlockHeadersByHashMessage(secondRequestId, newStartBlockHeader.getHash().getBytes(), newStartBlockNumber, 0, true);
        final Keccak256 commonAncestorHash = new Keccak256(randomHash());
        final BlockHeader commonAncestor = getBlockHeader(newStartBlockNumber, commonAncestorHash, BlockDifficulty.ONE);
        List<BlockHeader> secondBlockHeaderList = new ArrayList<>();
        secondBlockHeaderList.add(commonAncestor);
        includeBlockInBlockchain(commonAncestor);
        secondBlockHeaderList.addAll(getBlockHeaders(newStartBlockNumber+1, newStartBlockNumber-1, 0, true));
        lightSyncProcessor.processBlockHeadersMessage(secondRequestId, secondBlockHeaderList, lightPeer);

        //Process received pivots, get to Target Block  and transit to End Start Round
        final long thirdRequestId = secondRequestId+1;
        final int maxAmountOfHeaders = Math.toIntExact(TARGET_BLOCK_NUMBER - newStartBlockNumber);
        GetBlockHeadersByNumberMessage expectedMsg3 = new GetBlockHeadersByNumberMessage(thirdRequestId, newStartBlockNumber+1, maxAmountOfHeaders, 0, false);
        final List<BlockHeader> thirdBlockHeaderList = getBlockHeaders(newStartBlockNumber+1, maxAmountOfHeaders, 0, false);
        when(thirdBlockHeaderList.get(0).getParentHash()).thenReturn(commonAncestorHash);
        lightSyncProcessor.processBlockHeadersMessage(thirdRequestId, thirdBlockHeaderList, lightPeer);

        verify(lightSyncProcessor).endStartRound();
        assertMessagesWereSent(expectedMsg1, expectedMsg2, expectedMsg3);
    }

    private void newBlockHeadersInStartRoundSyncState(BlockHeader startBlockHeader, List<BlockHeader> blockHeaders, LightPeer lightPeer) {
        lightSyncProcessor.foundCommonAncestor(lightPeer, startBlockHeader);
        final LightSyncState syncState = lightSyncProcessor.getSyncState();
        syncState.newBlockHeaders(lightPeer, blockHeaders);
    }

    private void assertMessagesWereSent(GetBlockHeadersMessage expectedMsg1, GetBlockHeadersMessage expectedMsg2, GetBlockHeadersMessage expectedMsg3) {
        final ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        final List<GetBlockHeadersMessage> arguments = argument.getAllValues();
        verify(lightPeer, times(3)).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg1.getEncoded(), arguments.get(0).getEncoded());
        assertArrayEquals(expectedMsg2.getEncoded(), arguments.get(1).getEncoded());
        assertArrayEquals(arguments.get(2).getEncoded(), expectedMsg3.getEncoded());
    }

    private List<BlockHeader> getBlockHeaders(long initialNumber, int numberOfHeaders, int skip, boolean reverse) {
        final List<BlockHeader> blockHeaderList = new ArrayList<>();
        if (reverse && initialNumber - numberOfHeaders < 0) {
            return blockHeaderList;
        }
        for (int i = 0; i < numberOfHeaders; i++) {
            long blockNumber = reverse? initialNumber - i*(skip+1) : initialNumber + i*(skip+1);
            BlockHeader header = getBlockHeader(blockNumber, new Keccak256(randomHash()), BlockDifficulty.ONE);
            when(powRule.isValid(header)).thenReturn(true);
            blockHeaderList.add(header);
        }
        return blockHeaderList;
    }

    private void includeBlockInBlockchain(BlockHeader blockHeader) {
        Block anyBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(blockHeader.getNumber())).thenReturn(anyBlock);
        when(blockchain.getBlockByHash(blockHeader.getHash().getBytes())).thenReturn(anyBlock);
        when(anyBlock.getHeader()).thenReturn(blockHeader);
        when(powRule.isValid(blockHeader)).thenReturn(true);
    }

    private void assertSendSameMessage(GetBlockHeadersMessage expectedMsg, ArgumentCaptor<GetBlockHeadersMessage> argument) {
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg.getEncoded(), argument.getValue().getEncoded());
    }

    private BlockHeader getBlockHeader(long number, Keccak256 hash, BlockDifficulty blockDifficulty) {
        final BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(blockHeader.getNumber()).thenReturn(number);
        when(blockHeader.getDifficulty()).thenReturn(blockDifficulty);
        return blockHeader;
    }

    private LightSyncProcessor getLightSyncProcessor(ProofOfWorkRule powRule, Blockchain blockchain, LightPeersInformation lightPeersInformation) {
        Genesis genesis = mock(Genesis.class);
        return new LightSyncProcessor(mock(SystemProperties.class), genesis, mock(BlockStore.class), blockchain, powRule, lightPeersInformation);
    }

    private void setupBestPeerStatus(LightStatus lightStatus, long targetBlockNumber) {
        when(lightStatus.getTotalDifficulty()).thenReturn(new BlockDifficulty(BigInteger.TEN));
        when(lightStatus.getBestNumber()).thenReturn(targetBlockNumber);
        when(lightStatus.getBestHash()).thenReturn(randomHash());
    }
}
