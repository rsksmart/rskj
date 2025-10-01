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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class PeerAndModeDecidingSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger(PeerAndModeDecidingSyncState.class);

    private final PeersInformation peersInformation;
    private final BlockStore blockStore;

    public PeerAndModeDecidingSyncState(SyncConfiguration syncConfiguration,
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
        logger.trace("Starting tryStartSyncing");

        if (tryStartSnapshotSync()) {
            return;
        }

        if (tryStartBlockForwardSync()) {
            return;
        }

        if (tryStartShortBackwardSync()) {
            return;
        }

        syncEventsHandler.onLongSyncUpdate(false, null);
    }

    private boolean tryStartSnapshotSync() {
        if (!syncConfiguration.isClientSnapSyncEnabled()) {
            logger.trace("Snap syncing disabled");
            return false;
        }

        Optional<Peer> bestPeerOpt = peersInformation.getBestSnapPeer();
        Optional<Long> peerBestBlockNumOpt = bestPeerOpt.flatMap(this::getPeerBestBlockNumber);

        if (bestPeerOpt.isEmpty() || peerBestBlockNumOpt.isEmpty()) {
            logger.trace("Snap syncing not possible, no snap-capable peer available");
            return false;
        }

        // we consider Snap as part of the Long Sync
        if (!isValidSnapDistance(peerBestBlockNumOpt.get())) {
            logger.trace("Snap syncing not required");
            return false;
        }

        // we consider Snap as part of the Long Sync
        syncEventsHandler.onLongSyncUpdate(true, peerBestBlockNumOpt.get());

        // start snap syncing
        syncEventsHandler.startSnapSync(bestPeerOpt.get());
        return true;
    }

    private boolean tryStartBlockForwardSync() {
        Optional<Peer> bestPeerOpt = peersInformation.getBestPeer();
        Optional<Long> peerBestBlockNumOpt = bestPeerOpt.flatMap(this::getPeerBestBlockNumber);

        if (!bestPeerOpt.isPresent() || !peerBestBlockNumOpt.isPresent()) {
            logger.trace("Forward syncing not possible, no valid peer");
            return false;
        }

        if (!shouldLongSync(peerBestBlockNumOpt.get())) {
            logger.trace("Forward syncing not required");
            return false;
        }

        // start "long" / "forward" sync
        syncEventsHandler.onLongSyncUpdate(true, peerBestBlockNumOpt.get());
        syncEventsHandler.startBlockForwardSyncing(bestPeerOpt.get());
        return true;
    }

    private boolean tryStartShortBackwardSync() {
        if (checkGenesisConnected()) {
            logger.trace("Backward syncing not required");
            return false; // nothing else to do
        }

        Optional<Peer> peerForBackwardSync = peersInformation.getBestOrEqualPeer();
        if (!peerForBackwardSync.isPresent()) {
            logger.trace("Backward syncing not possible, no valid peer");
            return false;
        }

        // start "short" / "backward" sync
        syncEventsHandler.onLongSyncUpdate(false, null);
        syncEventsHandler.backwardSyncing(peerForBackwardSync.get());
        return true;
    }

    private boolean shouldLongSync(long peerBestBlockNumber) {
        long distanceToTip = peerBestBlockNumber - blockStore.getBestBlock().getNumber();
        return distanceToTip > syncConfiguration.getLongSyncLimit() || checkGenesisConnected();
    }

    private boolean isValidSnapDistance(long peerBestBlockNumber) {
        long distanceToTip = peerBestBlockNumber - blockStore.getBestBlock().getNumber();
        return distanceToTip > syncConfiguration.getSnapshotSyncLimit();
    }

    private Optional<Long> getPeerBestBlockNumber(Peer peer) {
        return Optional.ofNullable(peersInformation.getPeer(peer))
                .flatMap(pi -> Optional.ofNullable(pi.getStatus()).map(Status::getBestBlockNumber));
    }

    private boolean checkGenesisConnected() {
        return blockStore.getMinNumber() == 0;
    }
}
