package co.rsk.net.sync;

import co.rsk.net.NodeID;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class FindingConnectionPointSyncStateTest {

    private BlockStore blockStore;
    private SyncEventsHandler syncEventsHandler;
    private NodeID nodeId;

    @Before
    public void setUp() {
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockStore = mock(BlockStore.class);
        nodeId = mock(NodeID.class);
    }

    @Test
    public void noConnectionPoint() {
        when(blockStore.getMinNumber()).thenReturn(0L);
        FindingConnectionPointSyncState target =
                new FindingConnectionPointSyncState(
                        SyncConfiguration.IMMEDIATE_FOR_TESTING,
                        syncEventsHandler,
                        blockStore,
                        nodeId, 10L);

        when(blockStore.isBlockExist(any())).thenReturn(false);
        when(syncEventsHandler.sendBlockHashRequest(anyLong(), any())).thenReturn(true);

        target.onEnter();
        for(int i = 0; i < 4; i++) {
            target.newConnectionPointData(new byte[32]);
        }

        verify(syncEventsHandler, times(1)).onSyncIssue(any(), any());
    }

}