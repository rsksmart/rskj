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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.SyncBlockValidatorRule;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.BlockHeaderExtensionV2;
import org.ethereum.core.BlockHeaderV1;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;

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
    void newBodyWithMismatchedExtensionScoresInvalidMessage() {
        DownloadingBodiesSyncState state = new DownloadingBodiesSyncState(syncConfiguration,
                syncEventsHandler,
                peersInformation,
                blockchain,
                blockFactory,
                blockSyncService,
                syncBlockValidatorRule,
                Collections.emptyList(),
                Collections.emptyMap());

        BlockHeaderV1 header = createHeaderV1();
        DownloadingBodiesSyncState.PendingBodyResponse pendingBodyResponse = new DownloadingBodiesSyncState.PendingBodyResponse(peer.getPeerNodeID(), header);
        Map<Long, DownloadingBodiesSyncState.PendingBodyResponse> pendingBodyResponses = new HashMap<>();
        long messageId = 2L;
        pendingBodyResponses.put(messageId, pendingBodyResponse);
        TestUtils.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        BlockHeaderExtension mismatchedExtension = new BlockHeaderExtensionV2(
                new byte[256], new short[] { 1 }, new byte[] { 1 });
        BodyResponseMessage message = new BodyResponseMessage(messageId,
                Collections.emptyList(), Collections.emptyList(), mismatchedExtension);

        state.newBody(message, peer);

        verify(peersInformation, times(1))
                .reportEventToPeerScoring(peer, EventType.INVALID_MESSAGE,
                        "Invalid body received on {}, no {}, hash {}",
                        DownloadingBodiesSyncState.class, header.getNumber(), header.getPrintableHash());
    }

    private BlockHeaderV1 createHeaderV1() {
        return new BlockHeaderV1(
                TestUtils.generateHash("parentHash").getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                TestUtils.generateAddress("coinbase"),
                HashUtil.EMPTY_TRIE_HASH,
                new byte[32],
                HashUtil.EMPTY_TRIE_HASH,
                TestUtils.generateBytes("logsBloom", 256),
                BlockDifficulty.ONE,
                1L,
                BigInteger.valueOf(6800000).toByteArray(),
                3000000L,
                7731067L,
                new byte[0],
                Coin.ZERO,
                new byte[80],
                new byte[0],
                new byte[0],
                new byte[0],
                Coin.valueOf(10L),
                0,
                false,
                false,
                false,
                null,
                new short[0],
                false
        );
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
