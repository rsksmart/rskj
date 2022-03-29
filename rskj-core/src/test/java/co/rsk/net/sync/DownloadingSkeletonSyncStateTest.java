package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class DownloadingSkeletonSyncStateTest {

    // TODO Test other logic

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private Peer selectedPeer;

    @Before
    public void setUp () throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        selectedPeer = mock(Peer.class);
        NodeID nodeID = mock(NodeID.class);
        when(selectedPeer.getPeerNodeID()).thenReturn(nodeID);
        when(selectedPeer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    public void onMessageTimeOut() {
        DownloadingSkeletonSyncState target = new DownloadingSkeletonSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                selectedPeer,
                0);

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests from node [{}] on {}", DownloadingSkeletonSyncState.class);
    }

}

