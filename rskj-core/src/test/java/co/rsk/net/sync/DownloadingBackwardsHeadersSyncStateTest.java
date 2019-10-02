package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DownloadingBackwardsHeadersSyncStateTest {

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private BlockStore blockStore;
    private NodeID selectedPeerId;

    @Before
    public void setUp () {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        selectedPeerId = mock(NodeID.class);
    }

    @Test
    public void onEnter_requestSent() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeerId);

        ArgumentCaptor<ChunkDescriptor> descriptorCaptor = ArgumentCaptor.forClass(ChunkDescriptor.class);
        when(syncEventsHandler.sendBlockHeadersRequest(descriptorCaptor.capture(), any())).thenReturn(true);

        target.onEnter();

        verify(syncEventsHandler).sendBlockHeadersRequest(any(), eq(selectedPeerId));
        verify(syncEventsHandler, never()).onSyncIssue(any(), any());

        assertEquals(descriptorCaptor.getValue().getHash(), hash.getBytes());
        assertEquals(descriptorCaptor.getValue().getCount(), syncConfiguration.getChunkSize());
    }

    @Test
    public void onEnter_requestNotSent() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeerId);

        ArgumentCaptor<ChunkDescriptor> descriptorCaptor = ArgumentCaptor.forClass(ChunkDescriptor.class);
        when(syncEventsHandler.sendBlockHeadersRequest(descriptorCaptor.capture(), any())).thenReturn(false);

        target.onEnter();

        verify(syncEventsHandler).sendBlockHeadersRequest(any(), eq(selectedPeerId));
        verify(syncEventsHandler).onSyncIssue(any(), any());

        assertEquals(descriptorCaptor.getValue().getHash(), hash.getBytes());
        assertEquals(descriptorCaptor.getValue().getCount(), syncConfiguration.getChunkSize());
    }


    @Test
    public void newHeaders() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeerId);


        List<BlockHeader> receivedHeaders = new LinkedList<>();
        target.newBlockHeaders(receivedHeaders);


        verify(syncEventsHandler).backwardDownloadBodies(eq(selectedPeerId), eq(child), eq(receivedHeaders));
    }
}