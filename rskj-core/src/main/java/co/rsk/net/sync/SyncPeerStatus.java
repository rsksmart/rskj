/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.net.sync;

import co.rsk.net.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Created by ajlopez on 29/08/2017.
 */
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
     * It returns true or false depending on the comparison of last activity time
     * plus timeout and current time
     *
     * @param timeout time in milliseconds
     * @return true if the time since last activity plus timeout is less than current time in milliseconds
     */
    public boolean isExpired(Duration timeout) {
        return clock.instant().isAfter(this.lastActivity.plus(timeout));
    }
}

