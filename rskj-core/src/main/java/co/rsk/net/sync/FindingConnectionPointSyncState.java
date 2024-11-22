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

import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import org.ethereum.db.BlockStore;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSelectedPeerSyncState {

    protected final BlockStore blockStore;
    private final ConnectionPointFinder connectionPointFinder;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration,
                                           SyncEventsHandler syncEventsHandler,
                                           BlockStore blockStore,
                                           Peer selectedPeer,
                                           long peerBestBlockNumber) {
        super(syncEventsHandler, syncConfiguration, selectedPeer);
        long minNumber = blockStore.getMinNumber();

        this.blockStore = blockStore;
        this.connectionPointFinder = new ConnectionPointFinder(
                minNumber,
                peerBestBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        boolean knownBlock = isKnown(new Keccak256(hash));
        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (cp.isPresent()) {
            if (knownBlock) {
                processConnectionPoint(cp.get(), selectedPeer);
            } else {
                syncEventsHandler.onSyncIssue(selectedPeer, "Connection point not found on {}", this.getClass());
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
            processConnectionPoint(cp.get(), selectedPeer);
            return;
        }

        this.resetTimeElapsed();
        trySendRequest();
    }

    protected boolean isKnown(Keccak256 hash) {
        return blockStore.isBlockExist(hash.getBytes());
    }

    private void trySendRequest() {
        syncEventsHandler.sendBlockHashRequest(selectedPeer, connectionPointFinder.getFindingHeight());
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    protected void processConnectionPoint(long connectionPoint, Peer peer) {
        syncEventsHandler.startDownloadingSkeleton(connectionPoint, peer);
    }
}
