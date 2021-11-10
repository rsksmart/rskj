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

import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public abstract class BaseSyncState implements SyncState {
    protected SyncConfiguration syncConfiguration;
    protected SyncEventsHandler syncEventsHandler;

    protected Duration timeElapsed;

    public BaseSyncState(SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration) {
        this.syncEventsHandler = syncEventsHandler;
        this.syncConfiguration = syncConfiguration;

        this.resetTimeElapsed();
    }

    protected void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            onMessageTimeOut();
        }
    }

    protected void onMessageTimeOut() {
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
    }

    @Override
    public void newBody(BodyResponseMessage message, Peer peer) {
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
    }

    @Override
    public void newPeerStatus() { }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) {
    }

    @Override
    public void onEnter() { }

    @VisibleForTesting
    public void messageSent() {
        resetTimeElapsed();
    }
}
