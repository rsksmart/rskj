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
import co.rsk.net.Status;
import org.ethereum.db.BlockStore;

import java.time.Duration;
import java.util.Optional;

public class DecidingSyncState extends BaseSyncState {
    private final PeersInformation peersInformation;
    private final BlockStore blockStore;

    public DecidingSyncState(SyncConfiguration syncConfiguration,
                             SyncEventsHandler syncEventsHandler,
                             PeersInformation peersInformation,
                             BlockStore blockStore) {
        super(syncEventsHandler, syncConfiguration);

        this.peersInformation = peersInformation;
        this.blockStore = blockStore;
    }

    @Override
    public void newPeerStatus() {
        if (peersInformation.count() >= syncConfiguration.getExpectedPeers()) {
            tryStartSyncing();
        }
    }

    @Override
    public void tick(Duration duration) {
        peersInformation.cleanExpired();
        timeElapsed = timeElapsed.plus(duration);
        if (peersInformation.count() > 0 && timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingPeers()) >= 0) {
            tryStartSyncing();
        }
    }

    @Override
    public void onEnter() {
        Optional<Peer> bestPeerOpt = peersInformation.getBestPeer();
        Optional<Long> peerBestBlockNumOpt = bestPeerOpt.flatMap(this::getPeerBestBlockNumber);
        boolean isLongSyncing = peerBestBlockNumOpt.map(this::shouldLongSync).orElse(false);

        syncEventsHandler.onLongSyncUpdate(isLongSyncing, peerBestBlockNumOpt.orElse(null));
    }

    private void tryStartSyncing() {
        Optional<Peer> bestPeerOpt = peersInformation.getBestPeer();
        Optional<Long> peerBestBlockNumOpt = bestPeerOpt.flatMap(this::getPeerBestBlockNumber);
        if (!bestPeerOpt.isPresent() || !peerBestBlockNumOpt.isPresent()) { // no best peer, skip
            syncEventsHandler.onLongSyncUpdate(false, null);
            return;
        }

        Peer bestPeer = bestPeerOpt.get();
        long peerBestBlockNum = peerBestBlockNumOpt.get();
        if (shouldLongSync(peerBestBlockNum)) { // start "long" / "forward" sync
            syncEventsHandler.onLongSyncUpdate(true, peerBestBlockNum);
            syncEventsHandler.startSyncing(bestPeer);
        } else { // start "short" / "backward" sync
            syncEventsHandler.onLongSyncUpdate(false, null);
            syncEventsHandler.backwardSyncing(bestPeer);
        }
    }

    private boolean shouldLongSync(long peerBestBlockNumber) {
        long distanceToTip = peerBestBlockNumber - blockStore.getBestBlock().getNumber();
        return distanceToTip > syncConfiguration.getLongSyncLimit() || blockStore.getMinNumber() == 0;
    }

    private Optional<Long> getPeerBestBlockNumber(Peer peer) {
        return Optional.ofNullable(peersInformation.getPeer(peer))
                .flatMap(pi -> Optional.ofNullable(pi.getStatus()).map(Status::getBestBlockNumber));
    }
}
