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

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

/**
 * Uses Binary Search to help find a connection point with another peer.
 */
public class ConnectionPointFinder {

    private long start;
    private long end;

    // Connection point found or not
    private Long connectionPoint = null;

    public ConnectionPointFinder(long fromHeight, long toHeight) {
        this.start = fromHeight;
        this.end = toHeight;
    }

    public Optional<Long> getConnectionPoint() {
        return Optional.ofNullable(this.connectionPoint);
    }

    public long getFindingHeight() {
        // this is implemented like this to avoid overflow problems
        return this.start + (this.end - this.start) / 2;
    }

    public void updateFound() {
        this.start = getFindingHeight();
        trySettingConnectionPoint();
    }

    public void updateNotFound() {
        this.end = getFindingHeight();
        trySettingConnectionPoint();
    }

    private void trySettingConnectionPoint() {
        if (this.end - this.start <= 1) {
            this.setConnectionPoint(this.start);
        }
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        this.connectionPoint = height;
    }
}
