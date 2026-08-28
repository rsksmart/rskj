/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockProcessResult;
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.SyncBlockValidatorRule;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DownloadingBodiesSyncStateTest {

    // TODO Test missing logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private BlockFactory blockFactory;
    private SyncBlockValidatorRule syncBlockValidatorRule;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private Peer peer;

    @BeforeEach
    void setUp() throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        blockFactory = mock(BlockFactory.class);
        syncBlockValidatorRule = mock(SyncBlockValidatorRule.class);
        blockchain = mock(Blockchain.class);
        blockSyncService = mock(BlockSyncService.class);
        peer = mock(Peer.class);

        when(peer.getPeerNodeID()).thenReturn(new NodeID(new byte[]{2}));
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    /**
     * Roughly a fifth of mainnet blocks carry no uncles and nothing but their REMASC transaction, so
     * their body follows from the header and costs a round trip to ask for. Such a header must be
     * built locally and must never turn into a body request; the ones next to it still must.
     */
    @Test
    void aDerivableBodyIsBuiltLocallyAndNeverRequested() {
        BlockHeader derivable = headerAt(100L, new byte[]{10});
        BlockHeader requestable = headerAt(101L, new byte[]{11});

        Deque<BlockHeader> chunk = new ArrayDeque<>();
        chunk.add(derivable);
        chunk.add(requestable);
        List<Deque<BlockHeader>> pendingHeaders = new ArrayList<>();
        pendingHeaders.add(chunk);

        Block derivedBlock = mock(Block.class);
        when(blockFactory.hasDerivableBody(derivable)).thenReturn(true);
        when(blockFactory.hasDerivableBody(requestable)).thenReturn(false);
        when(blockFactory.newBlockWithDerivedBody(derivable)).thenReturn(derivedBlock);
        when(syncBlockValidatorRule.isValid(derivedBlock)).thenReturn(true);
        when(blockSyncService.processBlock(derivedBlock, null, true)).thenReturn(mock(BlockProcessResult.class));

        DownloadingBodiesSyncState state = stateReadyToDownload(pendingHeaders);

        state.onEnter();

        // the derivable one was assembled and imported without touching the network
        verify(blockFactory, times(1)).newBlockWithDerivedBody(derivable);
        verify(blockSyncService, times(1)).processBlock(derivedBlock, null, true);
        verify(syncEventsHandler, never()).sendBodyRequest(any(), eq(derivable));

        // its neighbour still goes out as an ordinary request
        verify(syncEventsHandler, times(1)).sendBodyRequest(peer, requestable);
    }

    /**
     * If a locally derived body fails validation the header must fall back to being requested, and
     * no peer may be blamed - no peer sent it.
     */
    @Test
    void aDerivedBodyThatFailsValidationFallsBackToARequest() {
        BlockHeader header = headerAt(100L, new byte[]{10});

        Deque<BlockHeader> chunk = new ArrayDeque<>();
        chunk.add(header);
        List<Deque<BlockHeader>> pendingHeaders = new ArrayList<>();
        pendingHeaders.add(chunk);

        Block derivedBlock = mock(Block.class);
        when(blockFactory.hasDerivableBody(header)).thenReturn(true);
        when(blockFactory.newBlockWithDerivedBody(header)).thenReturn(derivedBlock);
        when(syncBlockValidatorRule.isValid(derivedBlock)).thenReturn(false);

        DownloadingBodiesSyncState state = stateReadyToDownload(pendingHeaders);

        state.onEnter();

        verify(blockSyncService, never()).processBlock(any(), any(), anyBoolean());
        verify(syncEventsHandler, times(1)).sendBodyRequest(peer, header);
        verify(peersInformation, never())
                .reportEventToPeerScoring(any(Peer.class), any(EventType.class), anyString(), any());
    }

    private BlockHeader headerAt(long number, byte[] hashSeed) {
        BlockHeader header = mock(BlockHeader.class);
        byte[] hash = new byte[32];
        System.arraycopy(hashSeed, 0, hash, 0, hashSeed.length);
        when(header.getNumber()).thenReturn(number);
        when(header.getHash()).thenReturn(new Keccak256(hash));
        return header;
    }

    /** Builds a state with one peer already usable and one chunk of work assigned to it. */
    private DownloadingBodiesSyncState stateReadyToDownload(List<Deque<BlockHeader>> pendingHeaders) {
        SyncPeerStatus peerStatus = mock(SyncPeerStatus.class);
        when(peerStatus.getStatus()).thenReturn(new Status(1_000L, new byte[32]));
        when(peersInformation.getPeer(peer)).thenReturn(peerStatus);
        when(peersInformation.getBestPeerCandidates()).thenReturn(Collections.singletonList(peer));

        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                pendingHeaders,
                Collections.emptyMap());

        Deque<Integer> chunksOfSegment = new ArrayDeque<>();
        for (int i = pendingHeaders.size() - 1; i >= 0; i--) {
            chunksOfSegment.addLast(i);
        }
        List<Deque<Integer>> chunksBySegment = new ArrayList<>();
        chunksBySegment.add(chunksOfSegment);
        Map<Integer, Integer> segmentByChunk = new HashMap<>();
        for (int i = 0; i < pendingHeaders.size(); i++) {
            segmentByChunk.put(i, 0);
        }
        TestUtils.setInternalState(state, "chunksBySegment", chunksBySegment);
        TestUtils.setInternalState(state, "segmentByChunk", segmentByChunk);
        return state;
    }

    @Test
    void newBodyWhenUnexpectedMessageLogEvent() {
        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                Collections.emptyList(),
                Collections.emptyMap());

        BlockHeader header = mock(BlockHeader.class);
        DownloadingBodiesSyncState.PendingBodyResponse pendingBodyResponse = new DownloadingBodiesSyncState.PendingBodyResponse(peer.getPeerNodeID(), header);
        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        long messageId = 2L;
        pendingBodyResponses.put(messageId, pendingBodyResponse);
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        BodyResponseMessage message = new BodyResponseMessage(33L, Collections.emptyList(), Collections.emptyList(), null);
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                        "Unexpected body received on {}", DownloadingBodiesSyncState.class);
    }

    @Test
    void newBodyWhenUnexpectedMessageFromPeerLogEvent() {
        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                Collections.emptyList(),
                Collections.emptyMap());

        BlockHeader header = mock(BlockHeader.class);
        DownloadingBodiesSyncState.PendingBodyResponse pendingBodyResponse = new DownloadingBodiesSyncState.PendingBodyResponse(mock(NodeID.class), header);
        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        long messageId = 2L;
        pendingBodyResponses.put(messageId, pendingBodyResponse);
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        BodyResponseMessage message = new BodyResponseMessage(messageId, Collections.emptyList(), Collections.emptyList(), null);
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                        "Unexpected body received on {}", DownloadingBodiesSyncState.class);
    }

    @Test
    void tickOnTimeoutLogEvent() {
        Deque<BlockHeader> headers = new ArrayDeque<>();
        headers.add(mock(BlockHeader.class));

        List<Deque<BlockHeader>> pendingHeaders = new ArrayList<>();
        pendingHeaders.add(headers);

        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                pendingHeaders,
                Collections.emptyMap());

        // Work is tracked per request now, so a single outstanding body request is enough to set up.
        // A timeout only reports to peer scoring once the peer is actually discarded, which takes
        // several consecutive expiries: penalising every expired request would ruin the local
        // reputation of a merely slow peer and shrink the download pool.
        BlockHeader header = mock(BlockHeader.class);
        long messageId = 2L;

        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        pendingBodyResponses.put(messageId,
                new DownloadingBodiesSyncState.PendingBodyResponse(peer.getPeerNodeID(), header, peer, 0));
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        Map<Peer, Set<Long>> inFlightByPeer = new HashMap<>();
        Set<Long> inFlight = new HashSet<>();
        inFlight.add(messageId);
        inFlightByPeer.put(peer, inFlight);
        TestUtils.setInternalState(state, "inFlightByPeer", inFlightByPeer);

        // one short of the last-resort tick threshold, so this tick trips it
        Map<Peer, Integer> consecutiveTimeoutsByPeer = new HashMap<>();
        consecutiveTimeoutsByPeer.put(peer, 19);
        TestUtils.setInternalState(state, "consecutiveTimeoutsByPeer", consecutiveTimeoutsByPeer);

        state.tick(syncConfiguration.getTimeoutWaitingRequest());

        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting body on {}", DownloadingBodiesSyncState.class);
    }

    @Test
    void aSingleTimeoutDoesNotPenaliseThePeer() {
        Deque<BlockHeader> headers = new ArrayDeque<>();
        headers.add(mock(BlockHeader.class));

        List<Deque<BlockHeader>> pendingHeaders = new ArrayList<>();
        pendingHeaders.add(headers);

        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                pendingHeaders,
                Collections.emptyMap());

        long messageId = 7L;
        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        pendingBodyResponses.put(messageId,
                new DownloadingBodiesSyncState.PendingBodyResponse(
                        peer.getPeerNodeID(), mock(BlockHeader.class), peer, 0));
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        Map<Peer, Set<Long>> inFlightByPeer = new HashMap<>();
        Set<Long> inFlight = new HashSet<>();
        inFlight.add(messageId);
        inFlightByPeer.put(peer, inFlight);
        TestUtils.setInternalState(state, "inFlightByPeer", inFlightByPeer);

        state.tick(syncConfiguration.getTimeoutWaitingRequest());

        verify(peersInformation, never())
                .reportEventToPeerScoring(eq(peer), eq(EventType.TIMEOUT_MESSAGE), anyString(), any());
    }
}
