package co.rsk.net.sync;

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.crypto.HashUtil;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.util.*;

import static org.mockito.Mockito.*;

public class DownloadingHeadersSyncStateTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        Map<Peer, List<BlockIdentifier>> skeletons = Collections.singletonMap(null, null);
        SyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), skeletons,
                0);

        for (int i = 0; i < 10; i++) {
            syncState.newPeerStatus();
            Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());
        }
    }

    @Test
    public void itTimeoutsWhenWaitingForRequest() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), Collections.emptyMap(),
                0);

        syncState.newPeerStatus();
        Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest().dividedBy(2));
        Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest());
        Assert.assertTrue(syncEventsHandler.stopSyncingWasCalled());
    }

    @Test
    public void itDoesntTimeoutWhenSendingMessages() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                mock(Peer.class), Collections.emptyMap(),
                0);

        syncState.newPeerStatus();
        Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());

        for (int i = 0; i < 10; i++) {
            syncState.messageSent();
            Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());

            syncState.tick(syncConfiguration.getTimeoutWaitingRequest().dividedBy(2));
            Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());
        }

        syncState.tick(syncConfiguration.getTimeoutWaitingRequest());
        Assert.assertTrue(syncEventsHandler.stopSyncingWasCalled());
    }

    @Test
    public void newBlockHeadersWhenNoCurrentChunkThenSyncIssue() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        Whitebox.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.empty());

        syncState.newBlockHeaders(new ArrayList<>());

        verify(syncEventsHandler, times(1)).onSyncIssue(selectedPeer,
                "Current chunk not present for node [{}] on {}", DownloadingHeadersSyncState.class);
    }

    @Test
    public void newBlockHeadersWhenUnexpectedChunkSizeThenInvalidMessage() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        Whitebox.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        ChunkDescriptor currentChunk = mock(ChunkDescriptor.class);
        when(currentChunk.getCount()).thenReturn(2); // different from chunk size
        byte[] chunkHash = TestUtils.randomBytes(32);
        when(currentChunk.getHash()).thenReturn(chunkHash);
        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.of(currentChunk));

        List<BlockHeader> chunk = new ArrayList<>();
        chunk.add(mock(BlockHeader.class));
        syncState.newBlockHeaders(chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected chunk size received from node [{}] on {}: hash: {}", DownloadingHeadersSyncState.class, HashUtil.toPrintableHash(currentChunk.getHash()));
    }

    @Test
    public void newBlockHeadersWhenUnexpectedHeaderThenInvalidMessage() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SyncEventsHandler syncEventsHandler = mock(SyncEventsHandler.class);
        Peer selectedPeer = mock(Peer.class);
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                mock(ConsensusValidationMainchainView.class),
                mock(DependentBlockHeaderRule.class),
                mock(BlockHeaderValidationRule.class),
                selectedPeer, Collections.emptyMap(),
                0);

        ChunksDownloadHelper chunksDownloadHelper = mock(ChunksDownloadHelper.class);
        Whitebox.setInternalState(syncState, "chunksDownloadHelper", chunksDownloadHelper);

        ChunkDescriptor currentChunk = mock(ChunkDescriptor.class);
        when(currentChunk.getCount()).thenReturn(1);
        byte[] chunkHash = TestUtils.randomBytes(32);
        when(currentChunk.getHash()).thenReturn(chunkHash);
        when(chunksDownloadHelper.getCurrentChunk()).thenReturn(Optional.of(currentChunk));

        List<BlockHeader> chunk = new ArrayList<>();
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(TestUtils.randomBytes(32)); // different from chunkHash
        chunk.add(header);
        syncState.newBlockHeaders(chunk);

        verify(syncEventsHandler, times(1)).onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                "Unexpected chunk header hash received from node [{}] on {}: hash: {}", DownloadingHeadersSyncState.class, HashUtil.toPrintableHash(currentChunk.getHash()));
    }
}
