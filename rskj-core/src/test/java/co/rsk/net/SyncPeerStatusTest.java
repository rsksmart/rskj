package co.rsk.net;

import co.rsk.net.sync.SyncPeerStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 17/09/2017.
 */
public class SyncPeerStatusTest {
    @Test
    public void justCreatedIsNotExpired() {
        SyncPeerStatus status = new SyncPeerStatus();

        Assertions.assertFalse(status.isExpired(Duration.ofMillis(1000)));
    }

    @Test
    public void isExpiredAfterTimeout() throws InterruptedException {
        SyncPeerStatus status = new SyncPeerStatus();

        TimeUnit.MILLISECONDS.sleep(1000);

        Assertions.assertTrue(status.isExpired(Duration.ofMillis(100)));
    }

    @Test
    public void isNotExpiredAfterShortTimeout() throws InterruptedException {
        SyncPeerStatus status = new SyncPeerStatus();

        TimeUnit.MILLISECONDS.sleep(100);

        Assertions.assertFalse(status.isExpired(Duration.ofMillis(1000)));
    }
}
