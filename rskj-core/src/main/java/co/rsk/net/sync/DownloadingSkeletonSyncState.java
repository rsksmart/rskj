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
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class DownloadingSkeletonSyncState extends BaseSelectedPeerSyncState {

    /**
     * How long to keep waiting for the remaining skeletons once the trusted peer has answered.
     * Collecting a skeleton from every candidate is what lets the header and body phases spread
     * their requests, but a single unresponsive candidate must not hold a whole sync round hostage
     * for the full request timeout.
     */
    private static final Duration REMAINING_SKELETONS_GRACE = Duration.ofMillis(500);

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    /**
     * Chunks a peer puts in one skeleton. It is the responder's own maxSkeletonChunks, which we
     * cannot read, so the stride between the extra skeleton requests assumes the stock value. A
     * wrong guess simply fails the continuity check below and the round falls back to one skeleton.
     */
    private static final int ASSUMED_PEER_SKELETON_CHUNKS = 20;

    private final PeersInformation peersInformation;
    private final Map<Peer, List<BlockIdentifier>> skeletons;
    private final List<Peer> candidates;
    private long connectionPoint;
    private long expectedSkeletons;
    private boolean selectedPeerAnswered;
    private Duration elapsedSinceSelectedPeerAnswered = Duration.ZERO;

    /** Selected peer's skeletons, keyed by their first block number so they join in order. */
    private final SortedMap<Long, List<BlockIdentifier>> selectedPeerParts = new TreeMap<>();
    private final int rangeMultiplier;
    private final long skeletonStride;


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
        this.candidates = peersInformation.getBestPeerCandidates();
        this.expectedSkeletons = 0;
        this.rangeMultiplier = Math.max(1, syncConfiguration.getSkeletonRangeMultiplier());
        this.skeletonStride = (long) syncConfiguration.getChunkSize() * ASSUMED_PEER_SKELETON_CHUNKS;
    }

    /**
     * Joins the selected peer's skeletons into one continuous list. Each skeleton starts at the
     * block the previous one ended on, so the shared boundary is dropped. Joining stops at the
     * first boundary that does not line up, which leaves the round with a shorter but still
     * completely valid range.
     */
    private List<BlockIdentifier> stitchSelectedPeerSkeleton() {
        List<BlockIdentifier> combined = new ArrayList<>();
        for (List<BlockIdentifier> part : selectedPeerParts.values()) {
            if (combined.isEmpty()) {
                combined.addAll(part);
                continue;
            }
            BlockIdentifier tail = combined.get(combined.size() - 1);
            BlockIdentifier head = part.get(0);
            boolean joins = head.getNumber() == tail.getNumber()
                    && ByteUtil.fastEquals(head.getHash(), tail.getHash());
            if (!joins) {
                logger.debug("Skeleton at {} does not join onto {}; keeping the shorter range",
                        head.getNumber(), tail.getNumber());
                break;
            }
            combined.addAll(part.subList(1, part.size()));
        }
        return combined;
    }

    /** Publishes the joined skeleton for the selected peer and moves on to header download. */
    private void transitionToHeaders(Peer trustedPeer) {
        if (!selectedPeerParts.isEmpty()) {
            List<BlockIdentifier> combined = stitchSelectedPeerSkeleton();
            if (combined.size() >= 2) {
                skeletons.put(selectedPeer, combined);
            }
        }
        if (skeletons.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }
        syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint, trustedPeer);
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) {
        boolean isSelectedPeer = peer.equals(selectedPeer);

        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            peersInformation.reportEventToPeerScoring(peer, EventType.INVALID_MESSAGE,
                    "Invalid skeleton received on {}", this.getClass());

            // when the selected peer fails automatically all process restarts
            if (isSelectedPeer){
                syncEventsHandler.stopSyncing();
                return;
            }
        } else if (isSelectedPeer) {
            // several skeletons may come back from the trusted peer, one per slice of the range
            selectedPeerParts.put(skeleton.get(0).getNumber(), skeleton);
        } else {
            skeletons.put(peer, skeleton);
        }

        expectedSkeletons--;
        if (isSelectedPeer && !selectedPeerAnswered) {
            elapsedSinceSelectedPeerAnswered = Duration.ZERO;
        }
        selectedPeerAnswered = selectedPeerAnswered || isSelectedPeer;

        if (expectedSkeletons <= 0){
            transitionToHeaders(selectedPeerAnswered ? selectedPeer : peer);
        }
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);

        if (selectedPeerAnswered && !selectedPeerParts.isEmpty()) {
            elapsedSinceSelectedPeerAnswered = elapsedSinceSelectedPeerAnswered.plus(duration);
            if (elapsedSinceSelectedPeerAnswered.compareTo(REMAINING_SKELETONS_GRACE) >= 0) {
                // go with whatever arrived; stragglers simply do not take part in this round
                transitionToHeaders(selectedPeer);
                return;
            }
        }

        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            candidates.stream()
                    .filter(c -> !skeletons.containsKey(c))
                    .forEach(p ->
                            peersInformation.reportEventToPeerScoring(p, EventType.TIMEOUT_MESSAGE,
                                    "Timeout waiting skeleton on {}", this.getClass()));

            // when the selected peer fails automatically all process restarts
            if (!selectedPeerAnswered){
                syncEventsHandler.stopSyncing();
                return;
            }

            transitionToHeaders(selectedPeer);
        }
    }

    @Override
    public void onEnter() {
        // Request a skeleton from every candidate and wait for all of them, so that the body
        // download phase can spread its requests over many peers instead of a single one.
        // onEnter already contacted every candidate before this change; the responses were simply
        // thrown away because the state advanced on the first one. No extra requests are sent here.
        this.expectedSkeletons = candidates.size();
        candidates.forEach(p -> syncEventsHandler.sendSkeletonRequest(p, connectionPoint));

        // Ask the trusted peer for the following slices of the range at the same time. They are all
        // in flight together, so the extra coverage costs no additional round trip, and the
        // per-round setup is then amortised over rangeMultiplier times as many blocks.
        for (int i = 1; i < rangeMultiplier; i++) {
            syncEventsHandler.sendSkeletonRequest(selectedPeer, connectionPoint + i * skeletonStride);
            this.expectedSkeletons++;
        }
    }
}
