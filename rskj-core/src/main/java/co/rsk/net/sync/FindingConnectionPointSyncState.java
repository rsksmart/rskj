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
import org.ethereum.db.BlockStore;

import java.util.Optional;

public class FindingConnectionPointSyncState extends BaseSelectedPeerSyncState {

    private final BlockStore blockStore;
    private final ConnectionPointFinder connectionPointFinder;

    /**
     * Our own tip. During a long sync the connection point is almost always exactly this block,
     * because the previous round downloaded up to here from these same peers. Probing it directly
     * settles the search in a single round trip instead of the ~log2(peerBestBlock) sequential
     * requests the binary search needs (about 23 round trips against a 9M block chain, repeated for
     * every 3840 block round).
     */
    private final long ourBestBlockNumber;
    private final long minBlockNumber;
    private boolean tipProbeAnswered;

    public FindingConnectionPointSyncState(SyncConfiguration syncConfiguration,
                                           SyncEventsHandler syncEventsHandler,
                                           BlockStore blockStore,
                                           Peer selectedPeer,
                                           long peerBestBlockNumber) {
        super(syncEventsHandler, syncConfiguration, selectedPeer);
        long minNumber = blockStore.getMinNumber();

        this.blockStore = blockStore;
        this.minBlockNumber = minNumber;
        this.ourBestBlockNumber = Math.max(blockStore.getMaxNumber(), minNumber);
        // A common block can never be above our own tip, so the search never needs to look higher.
        this.connectionPointFinder = new ConnectionPointFinder(
                minNumber,
                Math.min(peerBestBlockNumber, this.ourBestBlockNumber));
    }

    /**
     * True when the search range is already degenerate, i.e. we hold nothing above the store's
     * minimum. The connection point is then that minimum and no request is needed. Asking would
     * mean requesting the genesis hash, which peers do not answer.
     */
    private boolean rangeIsSettled() {
        return ourBestBlockNumber - minBlockNumber <= 0;
    }

    private boolean canProbeTip() {
        // Never probe genesis: peers do not answer a block hash request for height 0, which is why
        // the binary search below also short-circuits when it lands on it.
        return ourBestBlockNumber > 0 && !rangeIsSettled();
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        if (!tipProbeAnswered && canProbeTip()) {
            tipProbeAnswered = true;
            if (isKnownBlock(hash)) {
                // The peer has our tip, so that is the connection point. No binary search needed.
                syncEventsHandler.startDownloadingSkeleton(ourBestBlockNumber, selectedPeer);
                return;
            }
            // Peer diverges below our tip: fall back to the regular binary search.
            this.resetTimeElapsed();
            trySendRequest();
            return;
        }

        boolean knownBlock = isKnownBlock(hash);
        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (cp.isPresent()) {
            if (knownBlock) {
                syncEventsHandler.startDownloadingSkeleton(cp.get(), selectedPeer);
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
        if (canProbeTip()) {
            syncEventsHandler.sendBlockHashRequest(selectedPeer, ourBestBlockNumber);
            return;
        }
        tipProbeAnswered = true;
        if (rangeIsSettled()) {
            // Nothing above the store minimum yet (fresh database): start right there.
            syncEventsHandler.startDownloadingSkeleton(minBlockNumber, selectedPeer);
            return;
        }
        trySendRequest();
    }
}
