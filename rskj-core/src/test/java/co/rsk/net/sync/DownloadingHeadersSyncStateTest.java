package co.rsk.net.sync;

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.Peer;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
}
