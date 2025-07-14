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
import co.rsk.net.messages.*;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public abstract class BaseSyncState implements SyncState {
    protected final SyncConfiguration syncConfiguration;
    protected final SyncEventsHandler syncEventsHandler;

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

    protected void onMessageTimeOut() { /* empty */ }

    @Override
    public void newBlockHeaders(Peer peer, List<BlockHeader> chunk) { /* empty */ }

    @Override
    public void newBody(BodyResponseMessage message, Peer peer) { /* empty */ }

    @Override
    public void newConnectionPointData(byte[] hash) { /* empty */ }

    @Override
    public void newPeerStatus() { /* empty */ }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) { /* empty */ }

    @Override
    public void onSnapStatus(Peer sender, SnapStatusResponseMessage responseMessage) { /* empty */ }

    @Override
    public void onSnapBlocks(Peer sender, SnapBlocksResponseMessage responseMessage) { /* empty */ }

    @Override
    public void onSnapStateChunk(Peer peer, SnapStateChunkResponseMessage responseMessage) { /* empty */ }

    @Override
    public void onSnapStateChunk(Peer peer, SnapStateChunkV2ResponseMessage responseMessage) { /* empty */ }

    @Override
    public void onEnter() { /* empty */ }

    @VisibleForTesting
    public void messageSent() {
        resetTimeElapsed();
    }
}
