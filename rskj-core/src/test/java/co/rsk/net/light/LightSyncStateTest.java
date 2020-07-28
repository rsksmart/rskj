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
import co.rsk.net.light.message.LightClientMessage;
import co.rsk.net.light.state.*;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
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
    private static final long MEDIUM_TARGET_BLOCK_NUMBER = 250;
    private static final long LONG_TARGET_BLOCK_NUMBER = 385;
    private static final int MAX_REQUESTED_HEADERS = 192;
    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightPeer mediumLightPeer;
    private LightPeer longLightPeer;
    private ProofOfWorkRule powRule;
    private Blockchain blockchain;
    private Genesis genesis;

    @Before
    public void setUp() {
        LightPeersInformation lightPeersInformation = new LightPeersInformation();
        powRule = mock(ProofOfWorkRule.class);
        blockchain = mock(Blockchain.class);
        genesis = getGenesis();
        when(blockchain.getBlockByNumber(0)).thenReturn(genesis);

        lightPeer = mock(LightPeer.class);
        registerANewLightPeer(lightPeersInformation, TARGET_BLOCK_NUMBER, lightPeer);

        mediumLightPeer = mock(LightPeer.class);
        registerANewLightPeer(lightPeersInformation, MEDIUM_TARGET_BLOCK_NUMBER, mediumLightPeer);

        longLightPeer = mock(LightPeer.class);
        registerANewLightPeer(lightPeersInformation, LONG_TARGET_BLOCK_NUMBER, longLightPeer);

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

    @Test
    public void decidingLightSyncStateShouldTransitToStartRoundIfBestBlockIsZero() {
        final BlockHeader bestBlockHeader = getBlockHeader(0L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final DecidingLightSyncState decidingLightSyncState = new DecidingLightSyncState(lightSyncProcessor, lightPeer, bestBlockHeader);
        decidingLightSyncState.sync();

        assertEquals(lightSyncProcessor.getSyncState().getClass(), StartRoundSyncState.class);
    }

    @Test
    public void commonAncestorSearchShouldRequestABlockHeaderToSync() {
        final Keccak256 bestBlockHash = new Keccak256(randomHash());
        final int bestBlockNumber = 1;
        final BlockHeader bestBlockHeader = getBlockHeader(bestBlockNumber, bestBlockHash, BlockDifficulty.ONE);
        startCommonAncestorSearchFrom(bestBlockHeader);
        final GetBlockHeadersByNumberMessage expectedMsg = new GetBlockHeadersByNumberMessage(1, bestBlockHeader.getNumber(), 1, 0, true);
        assertSendMessage(expectedMsg, lightPeer);
    }

    @Test
    public void commonAncestorStateReceiveNotKnownHeadersAndShouldAskForSuccessors() {
        final BlockHeader startBlockHeader = getBlockHeader(5L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final CommonAncestorSearchSyncState syncState = startCommonAncestorSearchFrom(startBlockHeader);

        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        long newStartNumber = startBlockHeader.getNumber() - bhs.size();
        final BlockHeader newStartBlock = getBlockHeader(newStartNumber, new Keccak256(randomHash()), BlockDifficulty.ONE);
        includeBlockInBlockchain(newStartBlock);

        syncState.newBlockHeaders(lightPeer, bhs);

        final GetBlockHeadersByNumberMessage initialMsg = new GetBlockHeadersByNumberMessage(1, startBlockHeader.getNumber(), (int) startBlockHeader.getNumber(), 0, true);
        final GetBlockHeadersByNumberMessage expectedMsg = new GetBlockHeadersByNumberMessage(2, newStartBlock.getNumber(), 4, 0, true);
        ArgumentCaptor<LightClientMessage> argument = forClass(GetBlockHeadersByHashMessage.class);
        assertTwoMessagesWereSent(initialMsg, expectedMsg, lightPeer);
    }

    @Test
    public void commonAncestorStateReceiveNotKnownHeadersGetZeroAndShouldTransitToStartSyncRound() {
        final BlockHeader startBlockHeader = getBlockHeader(5L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final CommonAncestorSearchSyncState syncState = startCommonAncestorSearchFrom(startBlockHeader);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);
        bhs.addAll(getBlockHeaders(startBlockHeader.getNumber()-1, 4, 0, true));

        syncState.newBlockHeaders(lightPeer, bhs);

        final GetBlockHeadersByNumberMessage initialMsg = new GetBlockHeadersByNumberMessage(1, startBlockHeader.getNumber(), (int) startBlockHeader.getNumber(), 0, true);
        final GetBlockHeadersByNumberMessage secondMsg = new GetBlockHeadersByNumberMessage(2, genesis.getNumber()+1, (int) TARGET_BLOCK_NUMBER, 0, false);
        assertTwoMessagesWereSent(initialMsg, secondMsg, lightPeer);
    }

    @Test
    public void commonAncestorStateReceivesMoreThanMaxAmountOfHeadersAndShouldDiscardIt() {
        final long startNumber = 5L;
        final BlockHeader startBlockHeader = getBlockHeader(startNumber, new Keccak256(randomHash()), BlockDifficulty.ONE);
        final CommonAncestorSearchSyncState syncState = startCommonAncestorSearchFrom(startBlockHeader);
        final long numberOfHeaders = startBlockHeader.getNumber() + 2;
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);
        bhs.addAll(getBlockHeaders(numberOfHeaders, (int) numberOfHeaders-1, 0, true));

        syncState.newBlockHeaders(lightPeer, bhs);
        verify(lightSyncProcessor).moreBlocksThanAllowed();
    }

    @Test
    public void startNumberIsOverMaxRequestedHeadersAndShouldRequestMaxQuantity() {
        final BlockHeader bestBlockHeader = getBlockHeader(200L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        startCommonAncestorSearchFrom(bestBlockHeader);
        final GetBlockHeadersByNumberMessage expectedMsg = new GetBlockHeadersByNumberMessage(1, bestBlockHeader.getNumber(), MAX_REQUESTED_HEADERS, 0, true);
        assertSendMessage(expectedMsg, lightPeer);
    }

    @Test
    public void peerInCommonAncestorStateReceivesABlockWithLowDifficulty() {
        final BlockHeader startBlockHeader = getBlockHeader(5L, new Keccak256(randomHash()), new BlockDifficulty(BigInteger.valueOf(50)));
        includeBlockInBlockchain(startBlockHeader);

        final CommonAncestorSearchSyncState syncState = startCommonAncestorSearchFrom(startBlockHeader);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        syncState.newBlockHeaders(lightPeer, bhs);
        verify(lightSyncProcessor, times(1)).wrongDifficulty();
    }

    @Test
    public void commonAncestorStateReceiveKnownHeadersAndShouldTransitToStartRoundSyncState() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE);
        includeBlockInBlockchain(startBlockHeader);
        final CommonAncestorSearchSyncState syncState = startCommonAncestorSearchFrom(startBlockHeader);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(startBlockHeader);

        syncState.newBlockHeaders(lightPeer, bhs);

        final GetBlockHeadersByNumberMessage initialMsg = new GetBlockHeadersByNumberMessage(1, startBlockHeader.getNumber(), (int) startBlockHeader.getNumber(), 0, true);
        final GetBlockHeadersByNumberMessage expectedMsg = new GetBlockHeadersByNumberMessage(2, 2, 9, 0, false);
        ArgumentCaptor<LightClientMessage> argument = forClass(GetBlockHeadersByNumberMessage.class);
        assertTwoMessagesWereSent(initialMsg, expectedMsg, lightPeer);
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesMoreThanMaxAmountOfBlockHeadersAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, (int) TARGET_BLOCK_NUMBER, 0, false);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).moreBlocksThanAllowed();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesADifferentStartingBlockHeaderAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(3, 1, 0, false);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).differentFirstBlocks();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesABadSkippedListOfHeadersAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 0, false);
        blockHeaders.add(getBlockHeader(14L, new Keccak256(randomHash()), BlockDifficulty.ONE));

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectSkipped();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesListOfHeadersUnsortedAndShouldNotProcessIt() {
        final BlockHeader startBlockHeader = getBlockHeader(1L, Keccak256.ZERO_HASH, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 0, false);
        blockHeaders.add(getBlockHeader(1L, new Keccak256(randomHash()), BlockDifficulty.ONE));

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectSkipped();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesAHeaderWithIncorrectParentHashAndShouldNotProcessIt() {
        final Keccak256 startBlockHash = Keccak256.ZERO_HASH;
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, (int) TARGET_BLOCK_NUMBER-1, 0, false);
        when(blockHeaders.get(0).getParentHash()).thenReturn(new Keccak256(randomHash()));
        includeBlockInBlockchain(startBlockHeader);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).incorrectParentHash();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundGetLesserHeadersThanAsked() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = getBlockHeaders(2, 1, 192, false);
        when(blockHeaders.get(0).getParentHash()).thenReturn(startBlockHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, mediumLightPeer);

        verify(lightSyncProcessor, times(1)).failedAttempt();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundReceivesHeadersCorrectlyZeroSkippedAndShouldEnd() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(2, Math.toIntExact(TARGET_BLOCK_NUMBER - 1), 0, false));
        when(blockHeaders.get(0).getParentHash()).thenReturn(startBlockHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, blockHeaders, lightPeer);

        verify(lightSyncProcessor, times(1)).endStartRound();
        assertEquals(StartRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInStartRoundGetsPivotsAndShouldTransitsToFetchRound() {
        final Keccak256 startBlockHash = new Keccak256(randomHash());
        final Keccak256 parentHash = new Keccak256(randomHash());
        final BlockHeader startBlockHeader = getBlockHeader(1L, startBlockHash, BlockDifficulty.ONE);
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, 192, false);
        when(pivots.get(0).getParentHash()).thenReturn(startBlockHash);
        when(pivots.get(1).getParentHash()).thenReturn(parentHash);

        newBlockHeadersInStartRoundSyncState(startBlockHeader, pivots, mediumLightPeer);

        verify(lightSyncProcessor, times(1)).startFetchRound(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER);
        assertEquals(FetchRoundSyncState.class, lightSyncProcessor.getSyncState().getClass());
    }

    @Test
    public void peerInFetchSyncRoundShouldAskForTheSubchain() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final Keccak256 firstBlockHash = new Keccak256(randomHash());
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, 192, false);
        when(pivots.get(1).getParentHash()).thenReturn(firstBlockHash);

        final FetchRoundSyncState syncState = new FetchRoundSyncState(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER, lightSyncProcessor);
        syncState.sync();

        final GetBlockHeadersByHashMessage expectedMessage = new GetBlockHeadersByHashMessage(1L, firstBlockHash.getBytes(), 192, 0, true);
        assertSendMessage(expectedMessage, mediumLightPeer);
    }

    @Test
    public void peerInFetchSyncRoundReceivesTheFirstSubchainWithWrongLastBlockHeader() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final int subchainSize = 192;
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, subchainSize, false);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(pivots.get(1).getNumber() - 1, subchainSize, 0, true));
        final Keccak256 incorrectParentHash = new Keccak256(randomHash());
        connectBlockHeaders(incorrectParentHash, blockHeaders, pivots.get(1));

        executeACycleOfFetchSyncState(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER, blockHeaders);
        verify(lightSyncProcessor, times(1)).badSubchain();
    }

    @Test
    public void peerInFetchSyncRoundReceivesNonConnectedHeadersAndShouldntProcessIt() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final int subchainSize = 192;
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, subchainSize, false);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(pivots.get(1).getNumber() - 1, subchainSize, 0, true));
        final Keccak256 initialBlockHeaderHash = blockHeaders.get(0).getHash();
        when(pivots.get(1).getParentHash()).thenReturn(initialBlockHeaderHash);
        when(blockHeaders.get(0).getParentHash()).thenReturn(new Keccak256(randomHash()));

        executeACycleOfFetchSyncState(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER, blockHeaders);
        verify(lightSyncProcessor, times(1)).badConnected();
    }

    @Test
    public void peerInFetchSyncRoundAskForASubchainsAndShouldReceivesAndProcessIt() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final int subchainSize = 192;
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, subchainSize, false);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(pivots.get(1).getNumber() - 1, subchainSize, 0, true));
        connectBlockHeaders(pivots.get(0).getHash(), blockHeaders, pivots.get(1));

        executeACycleOfFetchSyncState(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER, blockHeaders);
        assertEquals(blockHeaders, lightSyncProcessor.getDownloadedHeaders());
        verify(lightSyncProcessor).endSync();
    }

    @Test
    public void peerInFetchSyncRoundReceivesACorrectSubChainButIncompleteAndShouldAskTheRest() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final int subchainSize = 192;
        final List<BlockHeader> pivots = getBlockHeaders(2, 2, subchainSize, false);
        final List<BlockHeader> blockHeaders = new ArrayList<>(getBlockHeaders(pivots.get(1).getNumber() - 1, subchainSize, 0, true));
        connectBlockHeaders(pivots.get(0).getHash(), blockHeaders, pivots.get(1));
        final int remainingBlockHeaders = 5;
        final int quantityOfArrivedHeaders = blockHeaders.size() - remainingBlockHeaders;

        executeACycleOfFetchSyncState(mediumLightPeer, pivots, MEDIUM_TARGET_BLOCK_NUMBER, blockHeaders.subList(0, quantityOfArrivedHeaders));

        final GetBlockHeadersByHashMessage firstExpectedMessage = new GetBlockHeadersByHashMessage(1L, pivots.get(1).getParentHash().getBytes(), Math.toIntExact(pivots.get(1).getNumber() - pivots.get(0).getNumber() - 1), 0, true);
        final GetBlockHeadersByHashMessage secondExpectedMessage = new GetBlockHeadersByHashMessage(2L, blockHeaders.get(quantityOfArrivedHeaders-1).getParentHash().getBytes(), remainingBlockHeaders, 0, true);
        assertTwoMessagesWereSent(firstExpectedMessage, secondExpectedMessage, mediumLightPeer);

    }

    @Test
    public void peerInFetchSyncRoundShouldAskForTwoSubchains() {
        //The cases of receive wrong headers in FetchSyncRound are not going to be implemented. The method 'isCorrect' were tested in previous tests.
        final int subchainSize = 192;
        final List<BlockHeader> pivots = getBlockHeaders(2, 3, subchainSize, false);

        final List<BlockHeader> firstBlockHeaderResponse = new ArrayList<>(getBlockHeaders(pivots.get(2).getNumber() - 1, subchainSize, 0, true));
        connectBlockHeaders(pivots.get(1).getHash(), firstBlockHeaderResponse, pivots.get(2));
        final GetBlockHeadersByHashMessage firstExpectedMessage = new GetBlockHeadersByHashMessage(1L, pivots.get(2).getParentHash().getBytes(), Math.toIntExact(pivots.get(2).getNumber() - pivots.get(1).getNumber() - 1), 0, true);

        final List<BlockHeader> secondBlockHeaderResponse = new ArrayList<>(getBlockHeaders(pivots.get(1).getNumber() - 1, subchainSize, 0, true));
        connectBlockHeaders(pivots.get(0).getHash(), secondBlockHeaderResponse, pivots.get(1));
        final GetBlockHeadersByHashMessage secondExpectedMessage = new GetBlockHeadersByHashMessage(2L, pivots.get(1).getParentHash().getBytes(), Math.toIntExact(pivots.get(1).getNumber() - pivots.get(0).getNumber() - 1), 0, true);


        executeACycleOfFetchSyncState(longLightPeer, pivots, LONG_TARGET_BLOCK_NUMBER, firstBlockHeaderResponse);
        assertEquals(firstBlockHeaderResponse, lightSyncProcessor.getDownloadedHeaders());
        assertTwoMessagesWereSent(firstExpectedMessage, secondExpectedMessage, longLightPeer);
    }

    @Test
    public void anEntireProcessSinceLightSyncProcessorStartsFromBlockHeaderUntilItEndsStartRound() {
        int requestId = 1;
        final int bestBlockNumber = 195;
        final Keccak256 bestBlockHash = new Keccak256(randomHash());
        final BlockHeader bestKnownHeader = getBlockHeader(bestBlockNumber, bestBlockHash, BlockDifficulty.ONE);

        //Send BlockHeadersRequest
        lightSyncProcessor.startSync(lightPeer, bestKnownHeader);
        final GetBlockHeadersByNumberMessage expectedMsg1 = new GetBlockHeadersByNumberMessage(requestId, bestKnownHeader.getNumber(), MAX_REQUESTED_HEADERS, 0, true);

        //This should be the peer's response
        final List<BlockHeader> firstBlockHeaderList = new ArrayList<>();
        when(powRule.isValid(bestKnownHeader)).thenReturn(true);
        firstBlockHeaderList.add(bestKnownHeader);
        firstBlockHeaderList.addAll(getBlockHeaders(bestBlockNumber-1, MAX_REQUESTED_HEADERS-1, 0, true));

        //Process received block's headers and request for the rest
        final int newStartBlockNumber = bestBlockNumber - MAX_REQUESTED_HEADERS;
        final Keccak256 newStartBlockHash = new Keccak256(randomHash());
        final BlockHeader newStartBlockHeader = getBlockHeader(newStartBlockNumber, newStartBlockHash, BlockDifficulty.ONE);
        includeBlockInBlockchain(newStartBlockHeader);
        final long secondRequestId = requestId+1;
        lightSyncProcessor.processBlockHeadersMessage(requestId, firstBlockHeaderList, lightPeer);

        //Process response and request for pivots
        GetBlockHeadersByNumberMessage expectedMsg2 = new GetBlockHeadersByNumberMessage(secondRequestId, newStartBlockHeader.getNumber(), newStartBlockNumber, 0, true);
        List<BlockHeader> secondBlockHeaderList = new ArrayList<>();
        secondBlockHeaderList.add(newStartBlockHeader);
        secondBlockHeaderList.addAll(getBlockHeaders(newStartBlockNumber-1, newStartBlockNumber-1, 0, true));
        lightSyncProcessor.processBlockHeadersMessage(secondRequestId, secondBlockHeaderList, lightPeer);

        //Process received pivots, get to Target Block  and transit to End Start Round
        final long thirdRequestId = secondRequestId+1;
        final int maxAmountOfHeaders = Math.toIntExact(TARGET_BLOCK_NUMBER - newStartBlockNumber);
        GetBlockHeadersByNumberMessage expectedMsg3 = new GetBlockHeadersByNumberMessage(thirdRequestId, newStartBlockNumber+1, maxAmountOfHeaders, 0, false);
        final List<BlockHeader> thirdBlockHeaderList = getBlockHeaders(newStartBlockNumber+1, maxAmountOfHeaders, 0, false);
        when(thirdBlockHeaderList.get(0).getParentHash()).thenReturn(newStartBlockHash);
        lightSyncProcessor.processBlockHeadersMessage(thirdRequestId, thirdBlockHeaderList, lightPeer);

        verify(lightSyncProcessor).endStartRound();
        assertThreeMessagesWereSent(expectedMsg1, expectedMsg2, expectedMsg3, lightPeer);
    }

    @Test
    public void anEntireProcessSinceLightSyncProcessorStartsFromGenesisUntilItGetTarget() {
        int requestId = 1;
        final int bestBlockNumber = 195;
        final Keccak256 bestBlockHash = new Keccak256(randomHash());
        final BlockHeader bestKnownHeader = getBlockHeader(bestBlockNumber, bestBlockHash, BlockDifficulty.ONE);

        //Send BlockHeadersRequest
        lightSyncProcessor.startSync(lightPeer, bestKnownHeader);
        final GetBlockHeadersByNumberMessage expectedMsg1 = new GetBlockHeadersByNumberMessage(requestId, bestKnownHeader.getNumber(), MAX_REQUESTED_HEADERS, 0, true);

        //This should be the peer's response
        final List<BlockHeader> firstBlockHeaderList = new ArrayList<>();
        when(powRule.isValid(bestKnownHeader)).thenReturn(true);
        firstBlockHeaderList.add(bestKnownHeader);
        firstBlockHeaderList.addAll(getBlockHeaders(bestBlockNumber-1, MAX_REQUESTED_HEADERS-1, 0, true));

        //Process received block's headers and request for the rest
        int newStartBlockNumber = bestBlockNumber - MAX_REQUESTED_HEADERS;
        final Keccak256 newStartBlockHash = new Keccak256(randomHash());
        final BlockHeader newStartBlockHeader = getBlockHeader(newStartBlockNumber, newStartBlockHash, BlockDifficulty.ONE);
        lightSyncProcessor.processBlockHeadersMessage(requestId, firstBlockHeaderList, lightPeer);

        //Process response and request for pivots
        final long secondRequestId = requestId+1;
        GetBlockHeadersByNumberMessage expectedMsg2 = new GetBlockHeadersByNumberMessage(secondRequestId, newStartBlockHeader.getNumber(), newStartBlockNumber, 0, true);
        List<BlockHeader> secondBlockHeaderList = new ArrayList<>();
        secondBlockHeaderList.add(newStartBlockHeader);
        when(powRule.isValid(newStartBlockHeader)).thenReturn(true);
        secondBlockHeaderList.addAll(getBlockHeaders(newStartBlockNumber-1, newStartBlockNumber-1, 0, true));
        lightSyncProcessor.processBlockHeadersMessage(secondRequestId, secondBlockHeaderList, lightPeer);

        //Process received pivots, get to Target Block  and transit to End Start Round
        final long thirdRequestId = secondRequestId+1;
        final int maxAmountOfHeaders = (int) TARGET_BLOCK_NUMBER;
        newStartBlockNumber = (int) (genesis.getNumber()+1);
        GetBlockHeadersByNumberMessage expectedMsg3 = new GetBlockHeadersByNumberMessage(thirdRequestId, newStartBlockNumber, maxAmountOfHeaders, 0, false);
        final List<BlockHeader> thirdBlockHeaderList = getBlockHeaders(newStartBlockNumber, maxAmountOfHeaders, 0, false);
        final Keccak256 genesisHash = genesis.getHash();
        when(thirdBlockHeaderList.get(0).getParentHash()).thenReturn(genesisHash);
        lightSyncProcessor.processBlockHeadersMessage(thirdRequestId, thirdBlockHeaderList, lightPeer);

        verify(lightSyncProcessor).endStartRound();
        assertThreeMessagesWereSent(expectedMsg1, expectedMsg2, expectedMsg3, lightPeer);
    }

    private void executeACycleOfFetchSyncState(LightPeer lightPeer, List<BlockHeader> pivots, long target, List<BlockHeader> blockHeadersResponse) {
        final FetchRoundSyncState syncState = new FetchRoundSyncState(lightPeer, pivots, target, lightSyncProcessor);
        syncState.sync();
        syncState.newBlockHeaders(lightPeer, blockHeadersResponse);
    }

    private void registerANewLightPeer(LightPeersInformation lightPeersInformation, long longTargetBlockNumber, LightPeer longLightPeer) {
        LightStatus longLightStatus = mock(LightStatus.class);
        setupBestPeerStatus(longLightStatus, longTargetBlockNumber);
        lightPeersInformation.registerLightPeer(longLightPeer, longLightStatus, false);
    }

    private Genesis getGenesis() {
        Genesis genesis = mock(Genesis.class);
        GenesisHeader genesisHeader = mock(GenesisHeader.class);
        when(genesis.getNumber()).thenReturn(0L);
        final Keccak256 genesisHash = new Keccak256(randomHash());
        when(genesis.getHash()).thenReturn(genesisHash);
        final BlockDifficulty genesisDiff = BlockDifficulty.ZERO;
        when(genesis.getDifficulty()).thenReturn(genesisDiff);
        when(blockchain.getBlockByNumber(0)).thenReturn(genesis);
        when(genesis.getHeader()).thenReturn(genesisHeader);
        when(genesisHeader.getNumber()).thenReturn(0L);
        when(genesisHeader.getDifficulty()).thenReturn(genesisDiff);
        when(genesisHeader.getHash()).thenReturn(genesisHash);
        return genesis;
    }

    private CommonAncestorSearchSyncState startCommonAncestorSearchFrom(BlockHeader bestBlockHeader) {
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlockHeader.getNumber(), blockchain);
        syncState.sync();
        return syncState;
    }

    private void newBlockHeadersInStartRoundSyncState(BlockHeader startBlockHeader, List<BlockHeader> blockHeaders, LightPeer lightPeer) {
        lightSyncProcessor.startSyncRound(lightPeer, startBlockHeader);
        final LightSyncState syncState = lightSyncProcessor.getSyncState();
        syncState.newBlockHeaders(lightPeer, blockHeaders);
    }

    private void assertSendMessage(LightClientMessage expectedMsg, LightPeer lightPeer) {
        ArgumentCaptor<LightClientMessage> argument = forClass(LightClientMessage.class);
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg.getEncoded(), argument.getValue().getEncoded());
    }

    private void assertTwoMessagesWereSent(LightClientMessage firstExpectedMessage, LightClientMessage secondExpectedMessage, LightPeer lightPeer) {
        ArgumentCaptor<LightClientMessage> argument = forClass(LightClientMessage.class);
        final List<LightClientMessage> arguments = argument.getAllValues();
        verify(lightPeer, times(2)).sendMessage(argument.capture());
        assertArrayEquals(firstExpectedMessage.getEncoded(), arguments.get(0).getEncoded());
        assertArrayEquals(secondExpectedMessage.getEncoded(), arguments.get(1).getEncoded());
    }

    private void assertThreeMessagesWereSent(LightClientMessage expectedMsg1, LightClientMessage expectedMsg2, LightClientMessage expectedMsg3, LightPeer lightPeer) {
        final ArgumentCaptor<LightClientMessage> argument = forClass(LightClientMessage.class);
        final List<LightClientMessage> arguments = argument.getAllValues();
        verify(lightPeer, times(3)).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg1.getEncoded(), arguments.get(0).getEncoded());
        assertArrayEquals(expectedMsg2.getEncoded(), arguments.get(1).getEncoded());
        assertArrayEquals(expectedMsg3.getEncoded(), arguments.get(2).getEncoded());
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

    private void connectBlockHeaders(Keccak256 lowHeaderHash, List<BlockHeader> blockHeaders, BlockHeader highHeader) {
        final Keccak256 firstHeaderHash = blockHeaders.get(0).getHash();
        when(highHeader.getParentHash()).thenReturn(firstHeaderHash);

        for (int i=0; i < blockHeaders.size()-1;i++) {
            final BlockHeader actualBlockHeader = blockHeaders.get(i);
            final BlockHeader parentBlockHeader = blockHeaders.get(i+1);
            final Keccak256 parentHash = parentBlockHeader.getHash();
            when(actualBlockHeader.getParentHash()).thenReturn(parentHash);
        }

        final BlockHeader endBlockHeader = blockHeaders.get(blockHeaders.size()-1);
        when(endBlockHeader.getParentHash()).thenReturn(lowHeaderHash);
    }
}
