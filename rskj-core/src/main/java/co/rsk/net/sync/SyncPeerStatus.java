package co.rsk.net.sync;

import co.rsk.net.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Created by ajlopez on 29/08/2017. */
public class SyncPeerStatus {
    // Peer status
    private Status status;

    private final Clock clock = Clock.systemUTC();
    private Instant lastActivity;

    public SyncPeerStatus() {
        this.updateActivity();
    }

    private void updateActivity() {
        this.lastActivity = clock.instant();
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updateActivity();
    }

    public Status getStatus() {
        return this.status;
    }

    /**
     * It returns true or false depending on the comparison of last activity time plus timeout and
     * current time
     *
     * @param timeout time in milliseconds
     * @return true if the time since last activity plus timeout is less than current time in
     *     milliseconds
     */
    public boolean isExpired(Duration timeout) {
        return clock.instant().isAfter(this.lastActivity.plus(timeout));
    }
}
