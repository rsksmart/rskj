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
import co.rsk.scoring.EventType;
import org.ethereum.db.BlockStore;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSyncState {

    private final BlockStore blockStore;
    private final Peer selectedPeer;
    private final ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration,
                                           SyncEventsHandler syncEventsHandler,
                                           BlockStore blockStore,
                                           Peer selectedPeer,
                                           long peerBestBlockNumber) {
        super(syncEventsHandler, syncConfiguration);
        long minNumber = blockStore.getMinNumber();

        this.blockStore = blockStore;
        this.selectedPeer = selectedPeer;
        this.connectionPointFinder = new ConnectionPointFinder(
                minNumber,
                peerBestBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        boolean knownBlock = isKnownBlock(hash);
        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (cp.isPresent()) {
            if (knownBlock) {
                syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeer);
            } else {
                syncEventsHandler.onSyncIssue("Connection point not found with node {}", selectedPeer);
            }
             return;
        }

        if (knownBlock) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        cp = connectionPointFinder.getConnectionPoint();
        // No need to ask for genesis hash
        if (cp.isPresent() && cp.get() == 0L) {
            syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeer);
            return;
        }

        this.resetTimeElapsed();
        trySendRequest();
    }

    private boolean isKnownBlock(byte[] hash) {
        return blockStore.isBlockExist(hash);
    }

    private void trySendRequest() {
        syncEventsHandler.sendBlockHashRequest(selectedPeer, connectionPointFinder.getFindingHeight());
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(
                selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
