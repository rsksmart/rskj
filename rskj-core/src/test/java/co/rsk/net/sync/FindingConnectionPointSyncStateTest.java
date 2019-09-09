package co.rsk.net.sync;

import co.rsk.net.NodeID;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class FindingConnectionPointSyncStateTest {

    private Blockchain blockchain;
    private SyncEventsHandler syncEventsHandler;
    private NodeID nodeId;

    @Before
    public void setUp() {
        syncEventsHandler = mock(SyncEventsHandler.class);
        nodeId = mock(NodeID.class);
        blockchain = mock(Blockchain.class);
    }

    @Test
    public void noConnectionPoint() {
        when(blockchain.getFirstBlockNumber()).thenReturn(0L);
        FindingConnectionPointSyncState target =
                new FindingConnectionPointSyncState(
                        SyncConfiguration.IMMEDIATE_FOR_TESTING,
                        syncEventsHandler,
                        blockchain,
                        true,
                        nodeId,
                        10L);

        when(blockchain.hasBlockInSomeBlockchain(any())).thenReturn(false);
        when(syncEventsHandler.sendBlockHashRequest(anyLong(), any())).thenReturn(true);

        target.onEnter();
        for(int i = 0; i < 4; i++) {
            target.newConnectionPointData(new byte[32]);
        }

        verify(syncEventsHandler, times(1)).onSyncIssue(any(), any());
    }

}