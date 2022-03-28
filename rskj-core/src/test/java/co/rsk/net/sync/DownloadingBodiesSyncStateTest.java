package co.rsk.net.sync;

import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.SyncBlockValidatorRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

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
        Whitebox.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        BodyResponseMessage message = new BodyResponseMessage(33L, Collections.emptyList(), Collections.emptyList());
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventWithLog("Unexpected body received from node {}",
                        peer.getPeerNodeID(),
                        peer.getAddress(),
                        EventType.UNEXPECTED_MESSAGE,
                        peer.getPeerNodeID());
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
        Whitebox.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        BodyResponseMessage message = new BodyResponseMessage(messageId, Collections.emptyList(), Collections.emptyList());
        state.newBody(message, peer);
        verify(peersInformation, times(1))
                .reportEventWithLog("Unexpected body received from node {}",
                        peer.getPeerNodeID(),
                        peer.getAddress(),
                        EventType.UNEXPECTED_MESSAGE,
                        peer.getPeerNodeID());
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
        Whitebox.setInternalState(state, "pendingBodyResponses", pendingBodyResponses);

        Map<Peer, Integer> chunksBeingDownloaded = new HashMap<>();
        int peerChunk = 0;
        chunksBeingDownloaded.put(peer, peerChunk);
        Whitebox.setInternalState(state, "chunksBeingDownloaded", chunksBeingDownloaded);

        Map<Peer, Integer> segmentsBeingDownloaded = new HashMap<>();
        int peerSegment = 0;
        segmentsBeingDownloaded.put(peer, peerSegment);
        Whitebox.setInternalState(state, "segmentsBeingDownloaded", segmentsBeingDownloaded);

        List<Deque<Integer>> chunksBySegment = new ArrayList<>();
        Deque<Integer> segmentedChunks = new ArrayDeque<>();
        segmentedChunks.add(peerChunk);
        chunksBySegment.add(segmentedChunks);
        Whitebox.setInternalState(state, "chunksBySegment", chunksBySegment);

        Map<Peer, Duration> timeElapsedByPeer = new HashMap<>();
        timeElapsedByPeer.put(peer, Duration.ofSeconds(2));
        Whitebox.setInternalState(state, "timeElapsedByPeer", timeElapsedByPeer);

        Map<Peer, Long> messagesByPeers = new HashMap<>();
        messagesByPeers.put(peer, messageId);
        Whitebox.setInternalState(state, "messagesByPeers", messagesByPeers);

        state.tick(Duration.ofSeconds(1));
        verify(peersInformation, times(1))
                .reportEventWithLog("Timeout waiting body from node {}",
                        peer.getPeerNodeID(),
                        peer.getAddress(),
                        EventType.TIMEOUT_MESSAGE,
                        peer);
    }
}
