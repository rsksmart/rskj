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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;

/**
 * Downloads the header chunks described by the trusted skeleton.
 *
 * <p>Chunks used to be fetched one at a time from the selected peer, so a round of 20 chunks cost 20
 * sequential round trips before a single body could be requested. Every chunk is anchored to the
 * trusted skeleton (its top header must hash to the skeleton boundary, its size must match, and its
 * headers must link to each other), so a chunk is equally verifiable no matter which peer served it.
 * That makes it safe to keep several chunk requests in flight across different peers at once.
 *
 * <p>Downloads happen in parallel but chunks are <em>validated and published in ascending order</em>,
 * so the validation context (previously published headers visible through the mainchain view) is
 * exactly the same as it was when the download was sequential.
 */
public class DownloadingHeadersSyncState extends BaseSelectedPeerSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final Map<Peer, List<BlockIdentifier>> skeletons;
    private final List<Deque<BlockHeader>> pendingHeaders;
    private final DependentBlockHeaderRule blockParentValidationRule;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final Map<Keccak256, BlockHeader> pendingHeadersByHash;

    /** Chunk descriptors in ascending order; index i corresponds to skeleton link index i + 1. */
    private final List<ChunkDescriptor> chunkDescriptors;

    /** Chunks still waiting to be requested. */
    private final Deque<Integer> queuedChunks;

    /** Chunk index -> the request currently outstanding for it. */
    private final Map<Integer, InFlightChunk> inFlightByChunk;

    /** Outstanding request count per peer. */
    private final Map<Peer, Integer> inFlightCountByPeer;

    /** Raw responses that arrived but cannot be validated yet because an earlier chunk is missing. */
    private final Map<Integer, List<BlockHeader>> rawByChunk;

    private final Set<Peer> discardedPeers;

    private final int maxConcurrentRequests;
    private final int maxRequestsPerPeer;

    /** Next chunk that must be validated; chunks are published strictly in order. */
    private int nextChunkToValidate;

    public DownloadingHeadersSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            ConsensusValidationMainchainView mainchainView,
            DependentBlockHeaderRule blockParentValidationRule,
            BlockHeaderValidationRule blockHeaderValidationRule,
            Peer peer,
            Map<Peer, List<BlockIdentifier>> skeletons,
            long connectionPoint) {
        super(syncEventsHandler, syncConfiguration, peer);
        this.blockParentValidationRule = blockParentValidationRule;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.pendingHeaders = new ArrayList<>();
        this.skeletons = skeletons;
        this.pendingHeadersByHash = new ConcurrentHashMap<>();
        mainchainView.setPendingHeaders(pendingHeadersByHash);

        this.chunkDescriptors = buildChunkDescriptors(syncConfiguration, skeletons.get(selectedPeer), connectionPoint);
        this.queuedChunks = new ArrayDeque<>();
        this.inFlightByChunk = new HashMap<>();
        this.inFlightCountByPeer = new HashMap<>();
        this.rawByChunk = new HashMap<>();
        this.discardedPeers = new HashSet<>();
        this.nextChunkToValidate = 0;

        this.maxConcurrentRequests = Math.max(1, syncConfiguration.getMaxConcurrentHeaderRequests());
        this.maxRequestsPerPeer = Math.max(1, syncConfiguration.getMaxHeaderRequestsPerPeer());

        for (int i = 0; i < chunkDescriptors.size(); i++) {
            queuedChunks.addLast(i);
        }
    }

    private static List<ChunkDescriptor> buildChunkDescriptors(SyncConfiguration syncConfiguration,
                                                               List<BlockIdentifier> skeleton,
                                                               long connectionPoint) {
        List<ChunkDescriptor> descriptors = new ArrayList<>();
        if (skeleton == null) {
            return descriptors;
        }
        int maxLinkIndex = Math.min(skeleton.size() - 1, syncConfiguration.getMaxSkeletonChunks());
        for (int linkIndex = 1; linkIndex <= maxLinkIndex; linkIndex++) {
            byte[] hash = skeleton.get(linkIndex).getHash();
            long height = skeleton.get(linkIndex).getNumber();
            long lastHeight = skeleton.get(linkIndex - 1).getNumber();
            long previousKnownHeight = Math.max(lastHeight, connectionPoint);
            int count = (int) (height - previousKnownHeight);
            if (count <= 0) {
                continue;
            }
            descriptors.add(new ChunkDescriptor(hash, count));
        }
        return descriptors;
    }

    @Override
    public void onEnter() {
        if (chunkDescriptors.isEmpty()) {
            syncEventsHandler.onSyncIssue(selectedPeer, "Empty skeleton on {}", this.getClass());
            return;
        }
        logger.debug("Downloading {} header chunk(s), up to {} request(s) in flight",
                chunkDescriptors.size(), maxConcurrentRequests);
        dispatchRequests();
    }

    @Override
    public void newBlockHeaders(Peer peer, List<BlockHeader> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            reportAndRecover(peer, EventType.INVALID_MESSAGE, "Empty headers chunk received on {}");
            return;
        }

        // The top header identifies which chunk this is: it must hash to a skeleton boundary.
        Keccak256 topHash = chunk.get(0).getHash();
        Integer chunkIndex = findChunkIndexInFlight(topHash, peer);
        if (chunkIndex == null) {
            // A late duplicate for an already-completed chunk is harmless; anything else is not
            // something we asked this peer for.
            if (!isKnownChunkTopHash(topHash)) {
                reportAndRecover(peer, EventType.INVALID_MESSAGE, "Unexpected headers chunk received on {}");
            }
            return;
        }

        ChunkDescriptor descriptor = chunkDescriptors.get(chunkIndex);
        if (chunk.size() != descriptor.getCount()) {
            syncEventsHandler.onErrorSyncing(selectedPeer, EventType.INVALID_MESSAGE,
                    "Unexpected chunk size received on {}: hash: {}",
                    this.getClass(), HashUtil.toPrintableHash(descriptor.getHash()));
            return;
        }

        releaseInFlight(chunkIndex);
        rawByChunk.put(chunkIndex, chunk);

        if (!validateReadyChunks()) {
            return;
        }

        if (nextChunkToValidate >= chunkDescriptors.size()) {
            // Finished verifying headers
            syncEventsHandler.startDownloadingBodies(pendingHeaders, skeletons, selectedPeer);
            return;
        }

        resetTimeElapsed();
        dispatchRequests();
    }

    /**
     * Validates and publishes every chunk that is now contiguous with what has already been
     * published, preserving the original in-order validation semantics.
     *
     * @return false when a chunk turned out to be invalid and syncing was aborted.
     */
    private boolean validateReadyChunks() {
        while (nextChunkToValidate < chunkDescriptors.size()) {
            List<BlockHeader> chunk = rawByChunk.remove(nextChunkToValidate);
            if (chunk == null) {
                return true;
            }

            Deque<BlockHeader> headers = new ArrayDeque<>();
            // the headers come ordered by block number desc
            // we start adding the first parent header
            BlockHeader headerToAdd = chunk.get(chunk.size() - 1);
            headers.add(headerToAdd);

            for (int k = 1; k < chunk.size(); ++k) {
                BlockHeader parentHeader = chunk.get(chunk.size() - k);
                BlockHeader header = chunk.get(chunk.size() - k - 1);

                if (!blockHeaderIsValid(header, parentHeader)) {
                    syncEventsHandler.onErrorSyncing(selectedPeer, EventType.INVALID_HEADER,
                            "Invalid header received on {}, no: {}, hash: {}",
                            this.getClass(), header.getNumber(), header.getPrintableHash());
                    return false;
                }

                headers.add(header);
            }

            // publish only once the whole chunk validated
            headers.forEach(h -> pendingHeadersByHash.put(h.getHash(), h));
            pendingHeaders.add(headers);
            nextChunkToValidate++;
        }
        return true;
    }

    /** Sends chunk requests until the in-flight budget or the peer pool is exhausted. */
    private void dispatchRequests() {
        int guard = queuedChunks.size() + 1;
        while (!queuedChunks.isEmpty() && inFlightByChunk.size() < maxConcurrentRequests && guard-- > 0) {
            Integer chunkIndex = queuedChunks.pollFirst();
            Peer peer = pickPeerFor(chunkIndex);
            if (peer == null) {
                // no peer able to serve it right now; try again on the next tick
                queuedChunks.addFirst(chunkIndex);
                break;
            }
            syncEventsHandler.sendBlockHeadersRequest(peer, chunkDescriptors.get(chunkIndex));
            inFlightByChunk.put(chunkIndex, new InFlightChunk(peer));
            inFlightCountByPeer.merge(peer, 1, Integer::sum);
        }

        if (queuedChunks.isEmpty() && inFlightByChunk.isEmpty() && nextChunkToValidate < chunkDescriptors.size()) {
            syncEventsHandler.onSyncIssue(selectedPeer, "No peer can serve the remaining header chunks on {}",
                    this.getClass());
        }
    }

    /**
     * Chooses a peer whose own skeleton agrees with the trusted one at this boundary, preferring the
     * least loaded. The selected peer is always eligible: the skeleton being used is its own.
     */
    private Peer pickPeerFor(int chunkIndex) {
        Peer best = null;
        int bestLoad = Integer.MAX_VALUE;
        for (Peer candidate : skeletons.keySet()) {
            if (discardedPeers.contains(candidate) || !canServe(candidate, chunkIndex)) {
                continue;
            }
            int load = inFlightCountByPeer.getOrDefault(candidate, 0);
            if (load >= maxRequestsPerPeer) {
                continue;
            }
            if (load < bestLoad) {
                bestLoad = load;
                best = candidate;
            }
        }
        return best;
    }

    private boolean canServe(Peer peer, int chunkIndex) {
        if (peer.equals(selectedPeer)) {
            return true;
        }
        List<BlockIdentifier> peerSkeleton = skeletons.get(peer);
        if (peerSkeleton == null) {
            return false;
        }
        byte[] expected = chunkDescriptors.get(chunkIndex).getHash();
        // The peer must have the very block this chunk ends at, otherwise it is on another chain here.
        for (BlockIdentifier identifier : peerSkeleton) {
            if (ByteUtil.fastEquals(identifier.getHash(), expected)) {
                return true;
            }
        }
        return false;
    }

    private Integer findChunkIndexInFlight(Keccak256 topHash, Peer peer) {
        for (Map.Entry<Integer, InFlightChunk> entry : inFlightByChunk.entrySet()) {
            if (!entry.getValue().peer.equals(peer)) {
                continue;
            }
            if (ByteUtil.fastEquals(chunkDescriptors.get(entry.getKey()).getHash(), topHash.getBytes())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isKnownChunkTopHash(Keccak256 topHash) {
        for (ChunkDescriptor descriptor : chunkDescriptors) {
            if (ByteUtil.fastEquals(descriptor.getHash(), topHash.getBytes())) {
                return true;
            }
        }
        return false;
    }

    private void releaseInFlight(int chunkIndex) {
        InFlightChunk inFlight = inFlightByChunk.remove(chunkIndex);
        if (inFlight != null) {
            inFlightCountByPeer.computeIfPresent(inFlight.peer, (p, c) -> c <= 1 ? null : c - 1);
        }
    }

    private void reportAndRecover(Peer peer, EventType eventType, String message) {
        if (peer.equals(selectedPeer)) {
            // the trusted peer misbehaving aborts the whole sync, as it always did
            syncEventsHandler.onErrorSyncing(selectedPeer, eventType, message, this.getClass());
            return;
        }
        syncEventsHandler.onErrorSyncing(peer, eventType, message, this.getClass());
    }

    @Override
    public void tick(Duration duration) {
        if (inFlightByChunk.isEmpty()) {
            // Nothing outstanding: fall back to the global stall timeout so that a state which
            // cannot dispatch (no usable peer, empty skeleton) aborts instead of hanging forever.
            timeElapsed = timeElapsed.plus(duration);
            if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
                onMessageTimeOut();
            }
            return;
        }

        List<Integer> timedOut = new ArrayList<>();
        for (Map.Entry<Integer, InFlightChunk> entry : inFlightByChunk.entrySet()) {
            InFlightChunk inFlight = entry.getValue();
            inFlight.elapsed = inFlight.elapsed.plus(duration);
            if (inFlight.elapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
                timedOut.add(entry.getKey());
            }
        }

        for (Integer chunkIndex : timedOut) {
            InFlightChunk inFlight = inFlightByChunk.get(chunkIndex);
            Peer peer = inFlight.peer;
            releaseInFlight(chunkIndex);
            queuedChunks.addFirst(chunkIndex);

            if (peer.equals(selectedPeer)) {
                // preserve the legacy trust model: losing the selected peer aborts the sync
                syncEventsHandler.onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", this.getClass());
                return;
            }

            logger.debug("Header chunk {} timed out on helper peer {}, re-queuing", chunkIndex, peer.getPeerNodeID());
            discardedPeers.add(peer);
            syncEventsHandler.onErrorSyncing(peer, EventType.TIMEOUT_MESSAGE,
                    "Timeout waiting requests on {}", this.getClass());
        }

        if (!timedOut.isEmpty()) {
            resetTimeElapsed();
            dispatchRequests();
        }
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return skeletons.get(selectedPeer);
    }

    private boolean blockHeaderIsValid(BlockHeader header, BlockHeader parentHeader) {
        if (!parentHeader.getHash().equals(header.getParentHash())) {
            return false;
        }

        if (header.getNumber() != parentHeader.getNumber() + 1) {
            return false;
        }

        if (!blockHeaderValidationRule.isValid(header)) {
            return false;
        }

        return blockParentValidationRule.validate(header, parentHeader);
    }

    private static final class InFlightChunk {
        private final Peer peer;
        private Duration elapsed;

        private InFlightChunk(Peer peer) {
            this.peer = peer;
            this.elapsed = Duration.ZERO;
        }
    }
}
