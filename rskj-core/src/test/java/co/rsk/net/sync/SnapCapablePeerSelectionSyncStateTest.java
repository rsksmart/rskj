/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.net.simples.SimplePeer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapCapablePeerSelectionSyncStateTest {

    private static final int HEADERS_VALIDATION_COUNT = 64;
    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});
    private static final byte[] HASH_2 = HashUtil.sha256(new byte[]{2});

    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private BlockHeaderValidationRule blockHeaderValidationRule;
    private DependentBlockHeaderRule blockParentValidationRule;
    private SnapCapablePeerSelectionSyncState syncState;

    private Peer peer1;
    private Peer peer2;
    private Peer peer3;

    @BeforeEach
    void setUp() throws UnknownHostException {
        syncEventsHandler = mock(SyncEventsHandler.class);
        SyncConfiguration syncConfiguration = mock(SyncConfiguration.class);
        peersInformation = mock(PeersInformation.class);
        blockHeaderValidationRule = mock(BlockHeaderValidationRule.class);
        blockParentValidationRule = mock(DependentBlockHeaderRule.class);

        // Setup sync configuration defaults
        when(syncConfiguration.getChunkSize()).thenReturn(192);
        when(syncConfiguration.getMaxSkeletonChunks()).thenReturn(20);
        when(syncConfiguration.getTimeoutWaitingRequest()).thenReturn(Duration.ofSeconds(10));

        // Create test peers
        NodeID snapBootNodeId = new NodeID(TestUtils.generatePeerId("snapBootNode"));
        peer1 = new SimplePeer(new NodeID(TestUtils.generatePeerId("peer1")));
        peer2 = new SimplePeer(new NodeID(TestUtils.generatePeerId("peer2")));
        peer3 = new SimplePeer(snapBootNodeId);

        when(syncConfiguration.getSnapBootNodeIds()).thenReturn(Set.of(snapBootNodeId));

        syncState = new SnapCapablePeerSelectionSyncState(
                syncEventsHandler,
                syncConfiguration,
                peersInformation,
                blockHeaderValidationRule,
                blockParentValidationRule
        );
    }

    @Test
    void onEnter_withNoPeers_shouldStopSyncing() {
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(Collections.emptyList());

        syncState.onEnter();

        verify(syncEventsHandler).stopSyncing();
        verify(syncEventsHandler, never()).sendBlockHashRequest(any(), anyLong());
    }

    @Test
    void onEnter_withSnapBootNode_shouldSelectBootNodeDirectly() {
        List<Peer> snapPeers = Arrays.asList(peer1, peer2, peer3);
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(snapPeers);

        // Mock peer status for boot node
        SyncPeerStatus bootNodeStatus = createMockPeerStatus(10000L);
        when(peersInformation.getPeer(peer3)).thenReturn(bootNodeStatus);

        syncState.onEnter();

        verify(syncEventsHandler).startSnapSync(peer3, null);
        verify(syncEventsHandler, never()).sendBlockHashRequest(any(), anyLong());
    }

    @Test
    void onEnter_withMultiplePeers_shouldRequestCheckpointHashes() {
        List<Peer> snapPeers = Arrays.asList(peer1, peer2);
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(snapPeers);

        // Mock peer statuses
        SyncPeerStatus peerStatus1 = createMockPeerStatus(10000L);
        SyncPeerStatus peerStatus2 = createMockPeerStatus(12000L);
        when(peersInformation.getPeer(peer1)).thenReturn(peerStatus1);
        when(peersInformation.getPeer(peer2)).thenReturn(peerStatus2);

        syncState.onEnter();

        ArgumentCaptor<Long> blockNumberCaptor = ArgumentCaptor.forClass(Long.class);
        verify(syncEventsHandler, times(2)).sendBlockHashRequest(any(Peer.class), blockNumberCaptor.capture());
        
        List<Long> requestedBlockNumbers = blockNumberCaptor.getAllValues();
        // Both 10000 and 12000 round down to 10000, then subtract 3840 = 6160
        assertEquals(2, requestedBlockNumbers.size());
        requestedBlockNumbers.forEach(blockNum -> assertEquals(6160L, blockNum.longValue()));
        verify(syncEventsHandler, never()).stopSyncing();
    }

    @Test
    void onEnter_withPeersWithLowCheckpointNumber_shouldStopSyncing() {
        List<Peer> snapPeers = Collections.singletonList(peer1);
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(snapPeers);

        // Mock peer status with the low block number
        SyncPeerStatus peerStatus = createMockPeerStatus(100L);
        when(peersInformation.getPeer(peer1)).thenReturn(peerStatus);

        syncState.onEnter();

        verify(syncEventsHandler).stopSyncing();
        verify(syncEventsHandler, never()).sendBlockHashRequest(any(), anyLong());
    }

    @Test
    void newConnectionPointData_withValidHash_shouldStoreHashAndContinue() {
        setupStateWaitingForBlockHashes(Arrays.asList(peer1, peer2));

        syncState.newConnectionPointData(HASH_1, peer1);

        verify(syncEventsHandler, never()).onErrorSyncing(any(), any(), anyString(), any());
        // Should continue to request headers if all hashes received
    }

    @Test
    void newConnectionPointData_withUnexpectedPeer_shouldReportError() {
        setupStateWaitingForBlockHashes(Collections.singletonList(peer1));

        syncState.newConnectionPointData(HASH_1, peer2); // peer2 not in pending requests

        verify(peersInformation).processSyncingError(
                eq(peer2), 
                eq(EventType.UNEXPECTED_MESSAGE), 
                eq("Received unexpected block hash response from peer: {}"), 
                eq(peer2.getPeerNodeID())
        );
    }

    @Test
    void newConnectionPointData_allHashesReceived_shouldRequestHeaders() {
        List<Peer> peers = Arrays.asList(peer1, peer2);
        setupStateWaitingForBlockHashes(peers);

        syncState.newConnectionPointData(HASH_1, peer1);
        syncState.newConnectionPointData(HASH_2, peer2);

        ArgumentCaptor<ChunkDescriptor> chunkCaptor = ArgumentCaptor.forClass(ChunkDescriptor.class);
        verify(syncEventsHandler, times(2)).sendBlockHeadersRequest(any(Peer.class), chunkCaptor.capture());
        
        List<ChunkDescriptor> chunks = chunkCaptor.getAllValues();
        assertEquals(HEADERS_VALIDATION_COUNT, chunks.get(0).getCount());
        assertEquals(HEADERS_VALIDATION_COUNT, chunks.get(1).getCount());
    }

    @Test
    void newBlockHeaders_withValidHeaders_shouldValidateAndStore() {
        setupStateWaitingForHeaders(Collections.singletonList(peer1));
        List<BlockHeader> validHeaders = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        
        when(blockHeaderValidationRule.isValid(any())).thenReturn(true);
        when(blockParentValidationRule.validate(any(), any())).thenReturn(true);

        syncState.newBlockHeaders(peer1, validHeaders);

        // Validation should be called for each header until all are processed or one fails
        verify(blockHeaderValidationRule, atLeast(1)).isValid(any());
        verify(blockParentValidationRule, atLeast(0)).validate(any(), any());
    }

    @Test
    void newBlockHeaders_withInvalidCount_shouldReportError() {
        setupStateWaitingForHeaders(Collections.singletonList(peer1));
        List<BlockHeader> shortHeaders = createValidHeaderChain(32); // Invalid count

        syncState.newBlockHeaders(peer1, shortHeaders);

        verify(peersInformation).processSyncingError(
                eq(peer1), 
                eq(EventType.INVALID_MESSAGE), 
                eq("Expected {} headers but received {} headers from {}"), 
                eq(HEADERS_VALIDATION_COUNT), 
                eq(32), 
                eq(peer1.getPeerNodeID())
        );
    }

    @Test
    void newBlockHeaders_withInvalidHeaders_shouldReportError() {
        setupStateWaitingForHeaders(Collections.singletonList(peer1));
        List<BlockHeader> invalidHeaders = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        
        when(blockHeaderValidationRule.isValid(any())).thenReturn(false);

        syncState.newBlockHeaders(peer1, invalidHeaders);

        verify(peersInformation).processSyncingError(
                eq(peer1), 
                eq(EventType.INVALID_MESSAGE), 
                eq("Invalid headers received from {}"), 
                eq(peer1.getPeerNodeID())
        );
    }

    @Test
    void newBlockHeaders_withUnexpectedPeer_shouldReportError() {
        setupStateWaitingForHeaders(Collections.singletonList(peer1));
        List<BlockHeader> headers = createValidHeaderChain(HEADERS_VALIDATION_COUNT);

        syncState.newBlockHeaders(peer2, headers); // peer2 not expected

        verify(peersInformation).processSyncingError(
                eq(peer2), 
                eq(EventType.UNEXPECTED_MESSAGE), 
                eq("Unexpected block headers response from {}"), 
                eq(peer2.getPeerNodeID())
        );
    }

    @Test
    void selectBestPeer_withSingleValidPeer_shouldSelectThatPeer() {
        setupStateWaitingForHeaders(Collections.singletonList(peer1));
        List<BlockHeader> validHeaders = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        
        when(blockHeaderValidationRule.isValid(any())).thenReturn(true);
        when(blockParentValidationRule.validate(any(), any())).thenReturn(true);

        syncState.newBlockHeaders(peer1, validHeaders);

        ArgumentCaptor<BlockHeader> headerCaptor = ArgumentCaptor.forClass(BlockHeader.class);
        verify(syncEventsHandler).startSnapSync(eq(peer1), headerCaptor.capture());
        
        assertEquals(validHeaders.get(0), headerCaptor.getValue());
    }

    @Test
    void selectBestPeer_withMultipleValidPeers_shouldSelectBestOne() {
        List<Peer> peers = Arrays.asList(peer1, peer2);
        setupStateWaitingForHeaders(peers);
        
        // Create headers with different difficulties
        List<BlockHeader> headers1 = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        List<BlockHeader> headers2 = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        
        // Make peer2 have higher total difficulty
        SyncPeerStatus peerStatus1 = createMockPeerStatusWithDifficulty(10000L, new BlockDifficulty(BigInteger.valueOf(1000)));
        SyncPeerStatus peerStatus2 = createMockPeerStatusWithDifficulty(10000L, new BlockDifficulty(BigInteger.valueOf(2000)));
        when(peersInformation.getPeer(peer1)).thenReturn(peerStatus1);
        when(peersInformation.getPeer(peer2)).thenReturn(peerStatus2);
        
        when(blockHeaderValidationRule.isValid(any())).thenReturn(true);
        when(blockParentValidationRule.validate(any(), any())).thenReturn(true);

        syncState.newBlockHeaders(peer1, headers1);
        syncState.newBlockHeaders(peer2, headers2);

        // Should select peer2 due to higher total difficulty
        verify(syncEventsHandler).startSnapSync(eq(peer2), any());
    }

    @Test
    void selectBestPeer_withNoValidPeers_shouldStopSyncing() {
        setupStateWaitingForHeaders(Arrays.asList(peer1, peer2));
        List<BlockHeader> invalidHeaders1 = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        List<BlockHeader> invalidHeaders2 = createValidHeaderChain(HEADERS_VALIDATION_COUNT);
        
        when(blockHeaderValidationRule.isValid(any())).thenReturn(false);

        syncState.newBlockHeaders(peer1, invalidHeaders1);
        syncState.newBlockHeaders(peer2, invalidHeaders2);

        verify(syncEventsHandler).stopSyncing();
        verify(syncEventsHandler, never()).startSnapSync(any(), any());
    }

    @Test
    void onMessageTimeOut_duringBlockHashRequests_shouldHandleTimeout() {
        setupStateWaitingForBlockHashes(Arrays.asList(peer1, peer2));

        syncState.onMessageTimeOut();

        // Should report timeout for all pending peers
        verify(peersInformation).processSyncingError(
                eq(peer1), 
                eq(EventType.TIMEOUT_MESSAGE), 
                eq("Timeout waiting for block hash response from {}"), 
                eq(peer1.getPeerNodeID())
        );
        verify(peersInformation).processSyncingError(
                eq(peer2), 
                eq(EventType.TIMEOUT_MESSAGE), 
                eq("Timeout waiting for block hash response from {}"), 
                eq(peer2.getPeerNodeID())
        );
        verify(syncEventsHandler).stopSyncing();
    }

    @Test
    void onMessageTimeOut_duringHeadersRequests_shouldHandleTimeout() {
        setupStateWaitingForHeaders(Arrays.asList(peer1, peer2));

        syncState.onMessageTimeOut();

        // Should report timeout for all pending peers
        verify(peersInformation).processSyncingError(
                eq(peer1), 
                eq(EventType.TIMEOUT_MESSAGE), 
                eq("Timeout waiting for block headers response from {}"), 
                eq(peer1.getPeerNodeID())
        );
        verify(peersInformation).processSyncingError(
                eq(peer2), 
                eq(EventType.TIMEOUT_MESSAGE), 
                eq("Timeout waiting for block headers response from {}"), 
                eq(peer2.getPeerNodeID())
        );
    }

    @Test
    void onMessageTimeOut_duringBlockHashRequestsWithSomeHashesReceived_shouldRequestHeaders() {
        setupStateWaitingForBlockHashes(Arrays.asList(peer1, peer2));
        
        // Receive hash from peer1 before timeout
        syncState.newConnectionPointData(HASH_1, peer1);
        
        syncState.onMessageTimeOut();

        // Should report timeout for peer2 only
        verify(peersInformation).processSyncingError(
                eq(peer2), 
                eq(EventType.TIMEOUT_MESSAGE), 
                eq("Timeout waiting for block hash response from {}"), 
                eq(peer2.getPeerNodeID())
        );
        
        // Should proceed to request headers for peer1
        verify(syncEventsHandler).sendBlockHeadersRequest(eq(peer1), any());
        verify(syncEventsHandler, never()).stopSyncing();
    }

    @Test
    void tick_shouldTriggerTimeoutHandling() {
        setupStateWaitingForBlockHashes(Collections.singletonList(peer1));

        syncState.tick(Duration.ofSeconds(15)); // Longer than timeout

        verify(peersInformation).processSyncingError(
                eq(peer1), 
                eq(EventType.TIMEOUT_MESSAGE), 
                anyString(), 
                any()
        );
    }

    // Helper methods

    private void setupStateWaitingForBlockHashes(List<Peer> peers) {
        when(peersInformation.getBestSnapPeerCandidates()).thenReturn(peers);
        
        for (Peer peer : peers) {
            SyncPeerStatus peerStatus = createMockPeerStatus(10000L);
            when(peersInformation.getPeer(peer)).thenReturn(peerStatus);
        }
        
        syncState.onEnter();
        reset(syncEventsHandler); // Clear previous interactions
    }

    private void setupStateWaitingForHeaders(List<Peer> peers) {
        setupStateWaitingForBlockHashes(peers);
        
        // Send hashes for all peers to move to headers waiting state
        byte[] hash = HASH_1;
        for (Peer peer : peers) {
            syncState.newConnectionPointData(hash, peer);
            hash = Arrays.copyOf(hash, hash.length);
            hash[0]++; // Different hash for each peer
        }
        
        reset(syncEventsHandler, peersInformation); // Clear previous interactions
    }

    private SyncPeerStatus createMockPeerStatus(long bestBlockNumber) {
        return createMockPeerStatusWithDifficulty(bestBlockNumber, new BlockDifficulty(BigInteger.valueOf(bestBlockNumber)));
    }

    private SyncPeerStatus createMockPeerStatusWithDifficulty(long bestBlockNumber, BlockDifficulty totalDifficulty) {
        SyncPeerStatus peerStatus = mock(SyncPeerStatus.class);
        Status status = mock(Status.class);
        when(status.getBestBlockNumber()).thenReturn(bestBlockNumber);
        when(status.getTotalDifficulty()).thenReturn(totalDifficulty);
        when(peerStatus.getStatus()).thenReturn(status);
        return peerStatus;
    }

    private List<BlockHeader> createValidHeaderChain(int count) {
        List<BlockHeader> headers = new ArrayList<>();
        List<Keccak256> hashes = new ArrayList<>();
        long baseBlockNumber = 10000L;
        int callId = (int) (Math.random() * 1000000); // Make each call unique
        
        // Create all hashes first
        for (int i = 0; i < count + 1; i++) { // +1 for parent of last header
            byte[] hashBytes = HashUtil.sha256(BigInteger.valueOf(callId * 1000L + i).toByteArray());
            hashes.add(new Keccak256(hashBytes));
        }
        
        // Create headers and set up their properties
        for (int i = 0; i < count; i++) {
            BlockHeader header = mock(BlockHeader.class);
            long blockNumber = baseBlockNumber - i;
            
            when(header.getNumber()).thenReturn(blockNumber);
            when(header.getHash()).thenReturn(hashes.get(i));
            when(header.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(100)));
            when(header.getParentHash()).thenReturn(hashes.get(i + 1)); // Parent is the next hash
            
            headers.add(header);
        }
        
        return headers;
    }
}
