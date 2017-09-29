package co.rsk.net.sync;

import co.rsk.net.SyncProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

public class SyncingWithPeerSyncStateTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SyncProcessor syncProcessor = new SyncProcessor(null,null, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        syncProcessor.setSyncState(new SyncingWithPeerSyncState(SyncConfiguration.IMMEDIATE_FOR_TESTING, syncProcessor));
        syncProcessor.getSyncState().newPeerStatus();
        Assert.assertEquals(SyncStatesIds.SYNC_WITH_PEER, syncProcessor.getSyncState().getId());
    }

    @Test
    public void itTimeoutsWhenWaitingForRequest() {
        SyncProcessor syncProcessor = new SyncProcessor(null,null, SyncConfiguration.IMMEDIATE_FOR_TESTING);
        syncProcessor.setSyncState(new SyncingWithPeerSyncState(SyncConfiguration.IMMEDIATE_FOR_TESTING, syncProcessor));
        syncProcessor.getSyncState().tick(Duration.ofMinutes(1));
        Assert.assertEquals(SyncStatesIds.DECIDING, syncProcessor.getSyncState().getId());
    }

}
