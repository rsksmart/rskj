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
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.*;

public class DownloadingSkeletonSyncState extends BaseSelectedPeerSyncState {

    private final PeersInformation peersInformation;
    private final Map<Peer, List<BlockIdentifier>> skeletons;
    private final List<Peer> candidates;
    private long connectionPoint;
    private long expectedSkeletons;
    private boolean selectedPeerAnswered;


    public DownloadingSkeletonSyncState(SyncConfiguration syncConfiguration,
                                        SyncEventsHandler syncEventsHandler,
                                        PeersInformation peersInformation,
                                        Peer peer,
                                        long connectionPoint) {
        super(syncEventsHandler, syncConfiguration, peer);
        this.connectionPoint = connectionPoint;
        this.skeletons = new HashMap<>();
        this.selectedPeerAnswered = false;
        this.peersInformation = peersInformation;
        this.candidates = peersInformation.getPeerCandidates();
        this.expectedSkeletons = 0;
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) {
        boolean isSelectedPeer = peer.equals(selectedPeer);

        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            peersInformation.reportEventToPeerScoring(peer, EventType.INVALID_MESSAGE,
                    "Invalid skeleton received from node [{}] on {}", this.getClass());

            // when the selected peer fails automatically all process restarts
            if (isSelectedPeer){
                syncEventsHandler.stopSyncing();
                return;
            }
        } else {
            skeletons.put(peer, skeleton);
        }

        expectedSkeletons--;
        selectedPeerAnswered = selectedPeerAnswered || isSelectedPeer;

        if (expectedSkeletons <= 0){
            if (skeletons.isEmpty()){
                syncEventsHandler.stopSyncing();
                return;
            }
            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint, peer);
        }
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            candidates.stream()
                    .filter(c -> !skeletons.containsKey(c))
                    .forEach(p ->
                            peersInformation.reportEventToPeerScoring(p, EventType.TIMEOUT_MESSAGE,
                                    "Timeout waiting skeleton from node [{}] on {}", this.getClass()));

            // when the selected peer fails automatically all process restarts
            if (!selectedPeerAnswered){
                syncEventsHandler.stopSyncing();
                return;
            }

            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint, selectedPeer);
        }
    }

    @Override
    public void onEnter() {
        peersInformation.getPeerCandidates().forEach(p -> syncEventsHandler.sendSkeletonRequest(p, connectionPoint));
    }
}
