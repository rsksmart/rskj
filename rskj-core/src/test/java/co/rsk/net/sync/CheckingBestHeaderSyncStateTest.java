package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class CheckingBestHeaderSyncStateTest {

    private static final byte[] HASH_1 = HashUtil.sha256(new byte[]{1});

    private SyncEventsHandler syncEventsHandler;
    private Peer peer;
    private BlockHeaderValidationRule blockHeaderValidationRule;
    private CheckingBestHeaderSyncState state;

    @Before
    public void setUp() throws UnknownHostException {
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        blockHeaderValidationRule = mock(BlockHeaderValidationRule.class);
        peer = mock(Peer.class);
        state = new CheckingBestHeaderSyncState(syncConfiguration, syncEventsHandler, blockHeaderValidationRule, peer, HASH_1);

        when(peer.getPeerNodeID()).thenReturn(new NodeID(new byte[]{2}));
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    public void onEnterContinue() {
        state.onEnter();
        ChunkDescriptor chunk = new ChunkDescriptor(HASH_1, 1);
        verify(syncEventsHandler, times(1)).sendBlockHeadersRequest(eq(peer), eq(chunk));
    }

    @Test
    public void newBlockHeadersWhenValidHeaderContinue() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HASH_1);
        when(blockHeaderValidationRule.isValid(header)).thenReturn(true);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1)).startFindingConnectionPoint(peer);
    }

    @Test
    public void newBlockHeadersWhenInValidHeaderOnErrorSyncing() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HASH_1);
        when(blockHeaderValidationRule.isValid(header)).thenReturn(false);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer.getPeerNodeID(),
                        peer.getAddress(),
                        "Invalid chunk received from node {}",
                        EventType.INVALID_HEADER,
                        CheckingBestHeaderSyncState.class);
    }

    @Test
    public void newBlockHeadersWhenDifferentHeaderOnErrorSyncing() {
        BlockHeader header = mock(BlockHeader.class, Mockito.RETURNS_DEEP_STUBS);
        when(header.getHash().getBytes()).thenReturn(HashUtil.sha256(new byte[]{5}));
        when(blockHeaderValidationRule.isValid(header)).thenReturn(true);

        state.newBlockHeaders(Collections.singletonList(header));

        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer.getPeerNodeID(),
                        peer.getAddress(),
                        "Invalid chunk received from node {}",
                        EventType.INVALID_HEADER,
                        CheckingBestHeaderSyncState.class);
    }

    @Test
    public void onMessageTimeOut() {
        state.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer.getPeerNodeID(),
                        peer.getAddress(),
                        "Timeout waiting requests {}",
                        EventType.TIMEOUT_MESSAGE,
                        CheckingBestHeaderSyncState.class,
                        peer.getPeerNodeID(),
                        peer.getAddress());
    }
}
