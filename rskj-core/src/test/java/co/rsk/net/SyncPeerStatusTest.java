package co.rsk.net;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 17/09/2017.
 */
public class SyncPeerStatusTest {
    @Test
    public void justCreatedIsNotExpired() {
        SyncPeerStatus status = new SyncPeerStatus();

        Assert.assertFalse(status.isExpired(1000));
    }
}
