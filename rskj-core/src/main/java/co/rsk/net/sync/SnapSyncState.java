/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
import co.rsk.net.SnapshotProcessor;
import co.rsk.scoring.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class SnapSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");
    private final SnapshotProcessor snapshotProcessor;
    private final PeersInformation peers;

    public SnapSyncState(SyncEventsHandler syncEventsHandler, SnapshotProcessor snapshotProcessor, SyncConfiguration syncConfiguration, PeersInformation peers) {
        super(syncEventsHandler, syncConfiguration);
        this.snapshotProcessor = snapshotProcessor; // TODO(snap-poc) code in SnapshotProcessor should be moved here probably
        this.peers = peers;
    }

    @Override
    public void onEnter() {
        snapshotProcessor.startSyncing(this.peers, this);
    }

    public void newChunk() {
        resetTimeElapsed();
    }

    @Override
    public void tick(Duration duration) {
        // TODO(snap-poc) handle multiple peers casuistry, similarly to co.rsk.net.sync.DownloadingBodiesSyncState.tick

        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingSnapChunk()) >= 0) {
            onMessageTimeOut();
        }
    }

    // TODO(snap-poc) handle potential errors by calling co.rsk.net.sync.SyncEventsHandler.onErrorSyncing, like other SyncStates do

    @Override
    protected void onMessageTimeOut() {
        // TODO(snap-poc) handle multiple peers here, not just stop syncing, similarly to co.rsk.net.sync.DownloadingBodiesSyncState.tick
        Peer timeoutPeer = this.peers.getBestPeer().get();
        syncEventsHandler.stopSyncing();

        logger.warn("Timeout on SnapSyncState for peer {}", timeoutPeer.getPeerNodeID());
        syncEventsHandler.onErrorSyncing(timeoutPeer, EventType.TIMEOUT_MESSAGE,
                "Timeout for peer {} on {}", timeoutPeer.getPeerNodeID(), this.getClass());
    }

    public void finished() {
        syncEventsHandler.snapSyncFinished();
        syncEventsHandler.stopSyncing();
    }
}
