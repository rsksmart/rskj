/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.db.BlockStore;
import org.ethereum.validator.DependentBlockHeaderRule;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SnapDownloadingHeadersSyncState extends DownloadingHeadersSyncState {

    private final PeersInformation peersInformation;
    private final BlockStore blockStore;

    public SnapDownloadingHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            ConsensusValidationMainchainView mainchainView,
            DependentBlockHeaderRule blockParentValidationRule,
            BlockHeaderValidationRule blockHeaderValidationRule,
            Peer peer,
            Map<Peer, List<BlockIdentifier>> skeletons,
            long connectionPoint,
            PeersInformation peersInformation,
            BlockStore blockStore) {
        super(syncConfiguration, syncEventsHandler, mainchainView, blockParentValidationRule, blockHeaderValidationRule, peer, skeletons, connectionPoint);
        this.peersInformation = peersInformation;
        this.blockStore = blockStore;
    }

    @Override
    void processPendingHeaders(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer) {
        pendingHeaders.forEach(headers -> headers.forEach(blockStore::saveBlockHeader));

        Optional<Peer> bestPeerOpt = peersInformation.getBestPeer();
        if (bestPeerOpt.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        Optional<Peer> bestSnapPeerOpt = peersInformation.getBestSnapPeer();
        if (bestSnapPeerOpt.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        Optional<Status> bestSnapPeerStatusOpt = Optional.ofNullable(peersInformation.getPeer(bestSnapPeerOpt.get())).map(SyncPeerStatus::getStatus);
        if (bestSnapPeerStatusOpt.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        BlockHeader last = pendingHeaders.get(pendingHeaders.size() - 1).getLast();
        long numOfBlocksTillBest = bestSnapPeerStatusOpt.get().getBestBlockNumber() - last.getNumber();
        long syncMaxDistance = (long) syncConfiguration.getChunkSize() * syncConfiguration.getMaxSkeletonChunks();
        if (numOfBlocksTillBest < syncMaxDistance) {
            syncEventsHandler.startSnapSync(bestSnapPeerOpt.get());
            return;
        }

        Peer bestPeer = bestPeerOpt.get();
        if (bestPeer.getPeerNodeID().equals(peer.getPeerNodeID())) {
            syncEventsHandler.startDownloadingSnapSkeleton(last.getNumber(), bestPeer);
        } else {
            syncEventsHandler.startFindingSnapConnectionPoint(bestPeer);
        }
    }
}
