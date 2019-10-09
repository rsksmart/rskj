package co.rsk.net.sync;

import co.rsk.net.Peer;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class FindingConnectionPointSyncStateTest {

    private BlockStore blockStore;
    private SyncEventsHandler syncEventsHandler;
    private Peer peer;

    @Before
    public void setUp() {
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        peer = mock(Peer.class);
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

}