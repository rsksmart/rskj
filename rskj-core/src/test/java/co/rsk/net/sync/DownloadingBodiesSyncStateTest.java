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

import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.SyncBlockValidatorRule;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;

import static org.mockito.Mockito.*;

public class DownloadingBodiesSyncStateTest {

    // TODO Test missing logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private BlockFactory blockFactory;
    private SyncBlockValidatorRule syncBlockValidatorRule;
    private Blockchain blockchain;
    private BlockSyncService blockSyncService;
    private Peer peer;

    @Before
    public void setUp() throws UnknownHostException {
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

    private BodyResponseMessage createBodyResponseMessage(long id, List<Transaction> transactions, List<BlockHeader> uncles) {
        return new BodyResponseMessage(id, transactions, uncles, null);
    }

    @Test
    public void newBodyWhenUnexpectedMessageLogEvent() {
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

        BodyResponseMessage message = this.createBodyResponseMessage(33L, Collections.emptyList(), Collections.emptyList());
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                        "Unexpected body received on {}", DownloadingBodiesSyncState.class);
    }

    @Test
    public void newBodyWhenUnexpectedMessageFromPeerLogEvent() {
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

        BodyResponseMessage message = this.createBodyResponseMessage(messageId, Collections.emptyList(), Collections.emptyList());
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                        "Unexpected body received on {}", DownloadingBodiesSyncState.class);
    }

    @Test
    public void tickOnTimeoutLogEvent() {
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

        BlockHeader header = mock(BlockHeader.class);
        DownloadingBodiesSyncState.PendingBodyResponse pendingBodyResponse = new DownloadingBodiesSyncState.PendingBodyResponse(peer.getPeerNodeID(), header);
        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        long messageId = 2L;
        pendingBodyResponses.put(messageId, pendingBodyResponse);
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        Map<Peer, Integer> chunksBeingDownloaded = new HashMap<>();
        int peerChunk = 0;
        chunksBeingDownloaded.put(peer, peerChunk);
        TestUtils.setInternalState(state, "chunksBeingDownloaded", chunksBeingDownloaded);

        Map<Peer, Integer> segmentsBeingDownloaded = new HashMap<>();
        int peerSegment = 0;
        segmentsBeingDownloaded.put(peer, peerSegment);
        TestUtils.setInternalState(state, "segmentsBeingDownloaded", segmentsBeingDownloaded);

        List<Deque<Integer>> chunksBySegment = new ArrayList<>();
        Deque<Integer> segmentedChunks = new ArrayDeque<>();
        segmentedChunks.add(peerChunk);
        chunksBySegment.add(segmentedChunks);
        TestUtils.setInternalState(state, "chunksBySegment", chunksBySegment);

        Map<Peer, Duration> timeElapsedByPeer = new HashMap<>();
        timeElapsedByPeer.put(peer, Duration.ofSeconds(2));
        TestUtils.setInternalState(state, "timeElapsedByPeer", timeElapsedByPeer);

        Map<Peer, Long> messagesByPeers = new HashMap<>();
        messagesByPeers.put(peer, messageId);
        TestUtils.setInternalState(state, "messagesByPeers", messagesByPeers);

        state.tick(Duration.ofSeconds(1));
        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting body on {}", DownloadingBodiesSyncState.class);
    }
}
