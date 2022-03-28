package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DownloadingBackwardsHeadersSyncStateTest {

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private BlockStore blockStore;
    private Peer selectedPeer;

    @Before
    public void setUp () throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        selectedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(selectedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(selectedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    public void onEnter() {
        when(blockStore.getMinNumber()).thenReturn(50L);
        Block child = mock(Block.class);
        Keccak256 hash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(hash);
        when(blockStore.getChainBlockByNumber(50L)).thenReturn(child);

        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeer);

        ArgumentCaptor<ChunkDescriptor> descriptorCaptor = ArgumentCaptor.forClass(ChunkDescriptor.class);

        target.onEnter();

        verify(syncEventsHandler).sendBlockHeadersRequest(eq(selectedPeer), descriptorCaptor.capture());
        verify(syncEventsHandler, never()).onSyncIssue(any(), any());

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
                selectedPeer);


        List<BlockHeader> receivedHeaders = new LinkedList<>();
        target.newBlockHeaders(receivedHeaders);


        verify(syncEventsHandler).backwardDownloadBodies(eq(child), eq(receivedHeaders), eq(selectedPeer));
    }

    @Test
    public void onMessageTimeOut() {
        DownloadingBackwardsHeadersSyncState target = new DownloadingBackwardsHeadersSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                selectedPeer);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(selectedPeer.getPeerNodeID(),
                        selectedPeer.getAddress(),
                        "Timeout waiting requests {}",
                        EventType.TIMEOUT_MESSAGE,
                        DownloadingBackwardsHeadersSyncState.class,
                        selectedPeer.getPeerNodeID(),
                        selectedPeer.getAddress());
    }
}
