package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class FindingConnectionPointSyncStateTest {

    // TODO Test other logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private BlockStore blockStore;
    private Peer peer;

    @Before
    public void setUp() throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        peer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(peer.getPeerNodeID()).thenReturn(nodeID);
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    public void noConnectionPoint() {
        when(blockStore.getMinNumber()).thenReturn(0L);
        FindingConnectionPointSyncState target =
                new FindingConnectionPointSyncState(
                        SyncConfiguration.IMMEDIATE_FOR_TESTING,
                        syncEventsHandler,
                        blockStore,
                        peer, 10L);

        when(blockStore.isBlockExist(any())).thenReturn(false);

        target.onEnter();
        for(int i = 0; i < 4; i++) {
            target.newConnectionPointData(new byte[32]);
        }

        verify(syncEventsHandler, times(1)).onSyncIssue(any(), any());
    }

    @Test
    public void onMessageTimeOut() {
        FindingConnectionPointSyncState target = new FindingConnectionPointSyncState(
                syncConfiguration,
                syncEventsHandler,
                blockStore,
                peer,
                10L);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1)).onErrorSyncing(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests from node [{}] on {}", FindingConnectionPointSyncState.class);
    }

}
