package co.rsk.net.sync;

import co.rsk.net.simples.SimpleStatusHandler;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class FindingConnectionPointSyncStatusTest {
    @Test
    public void itIgnoresNewPeerInformation() {
        SimpleStatusHandler statusHandler = new SimpleStatusHandler();
        statusHandler.setStatus(new FindingConnectionPointSyncStatus());
        statusHandler.getStatus().newPeerStatus(statusHandler, null, null, Collections.emptySet());
        Assert.assertEquals(SyncStatusIds.FINDING_CONNECTION_POINT, statusHandler.getStatus().getId());
    }
}
