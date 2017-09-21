package co.rsk.net.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class FindingConnectionPointSyncStatusTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SyncStatus status = new FindingConnectionPointSyncStatus();
        status = status.newPeerStatus(null, null, Collections.emptySet());
        Assert.assertEquals(SyncStatuses.FINDING_CONNECTION_POINT, status.getStatus());
    }
}
