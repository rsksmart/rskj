package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class DownloadingHeadersSyncStateTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SyncState syncState = new DownloadingHeadersSyncState(syncConfiguration, syncEventsHandler, null, Collections.emptyList(), 0);

        for (int i = 0; i < 10; i++) {
            syncState.newPeerStatus();
            Assert.assertFalse(syncEventsHandler.stopSyncingWasCalled());
        }
    }

    @Test
    public void itTimeoutsWhenWaitingForRequest() {
        SyncConfiguration syncConfiguration = SyncConfiguration.DEFAULT;
        SimpleSyncEventsHandler syncEventsHandler = new SimpleSyncEventsHandler();
        SyncState syncState = new DownloadingHeadersSyncState(syncConfiguration, syncEventsHandler, new SimpleSyncInformation(), Collections.emptyList(), 0);

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
        DownloadingHeadersSyncState syncState = new DownloadingHeadersSyncState(syncConfiguration, syncEventsHandler, new SimpleSyncInformation(), Collections.emptyList(), 0);

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
