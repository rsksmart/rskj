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
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.SyncBlockValidatorRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Downloads block bodies from every suitable peer.
 *
 * <p>The wire protocol carries exactly one body per request/response pair, so the only way to go
 * faster than "one block per round trip per peer" is to keep several requests in flight at once and
 * to spread them over as many peers as possible. This state therefore tracks work <em>per request</em>
 * rather than per peer:
 *
 * <ul>
 *     <li>up to {@code maxInFlightPerPeer} body requests are outstanding to each peer;</li>
 *     <li>requests are throttled to {@code maxRequestsPerMinutePerPeer} per peer, because a stock
 *         RskJ peer rejects every message from a sender that goes over
 *         {@code peer.messageQueue.thresholdPerMinutePerPeer} (1000) within a calendar minute;</li>
 *     <li>a slow response times out and is re-queued on its own, instead of discarding the peer on
 *         the first late reply. A peer is dropped only after several consecutive timeouts.</li>
 * </ul>
 *
 * <p>Chunks are no longer owned exclusively by one peer: any peer whose skeleton covers a chunk may
 * pull pending headers from it, which keeps every peer busy until the whole range is downloaded.
 */
public class DownloadingBodiesSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    /**
     * Last-resort threshold: a peer that keeps timing out even after its in-flight allowance has
     * been squeezed down to one is not answering at all, so it is dropped. Timeouts on their own no
     * longer discard a peer - throughput scales with how many peers stay in the rotation, and a
     * merely slow peer is still worth keeping.
     */
    private static final int MAX_CONSECUTIVE_TIMEOUTS_PER_PEER = 40;

    /**
     * Half-minute throttling window. A peer counts the messages it receives from us inside each
     * calendar minute, so a rolling 60s budget could still put nearly twice the limit into one of
     * its minutes when the two windows straddle. Limiting to half the budget over 30s makes the
     * bound provable: any 60s interval is the union of two disjoint 30s windows, so it can never
     * carry more than the configured per-minute allowance.
     */
    private static final long RATE_WINDOW_MS = 30_000L;

    private final PeersInformation peersInformation;
    private final Blockchain blockchain;
    private final BlockFactory blockFactory;

    // responses on wait, keyed by request id
    private final Map<Long, PendingBodyResponse> pendingBodyResponses;

    // in-flight request ids per peer
    private final Map<Peer, Set<Long>> inFlightByPeer;

    // sliding window of send timestamps per peer, used to throttle outbound requests
    private final Map<Peer, Deque<Long>> sentTimestampsByPeer;

    // consecutive timeouts per peer
    private final Map<Peer, Integer> consecutiveTimeoutsByPeer;

    /**
     * Per-peer in-flight allowance. Peers differ widely in how fast they serve bodies, and pushing
     * a slow one too hard just parks requests in its queue until they expire. The allowance halves
     * on a timeout and creeps back up while the peer keeps up, so each peer settles at the depth it
     * can actually sustain.
     */
    private final Map<Peer, Integer> allowanceByPeer;

    // headers waiting to be completed by bodies divided by chunks
    private final List<Deque<BlockHeader>> pendingHeaders;

    // a skeleton from each suitable peer
    private final Map<Peer, List<BlockIdentifier>> skeletons;

    // segment a peer belongs to
    private final Map<Peer, Integer> segmentByNode;

    // chunks divided by segments
    private final List<Deque<Integer>> chunksBySegment;

    // segment each chunk belongs to
    private final Map<Integer, Integer> segmentByChunk;

    // peers that can be used to download blocks
    private final List<Peer> suitablePeers;

    // peers discarded during this phase; they must not be picked up again by the refresh below
    private final Set<Peer> discardedPeers;

    // maximum time waiting for a peer to answer
    private final Duration limit;
    private final SyncBlockValidatorRule blockValidationRule;
    private final BlockSyncService blockSyncService;

    private final int maxInFlightPerPeer;
    private final int maxRequestsPerMinutePerPeer;

    // set when a fill attempt was held back purely by the outbound rate limiter
    private boolean lastFillRateLimited;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration,
                                      SyncEventsHandler syncEventsHandler,
                                      PeersInformation peersInformation,
                                      Blockchain blockchain,
                                      BlockFactory blockFactory,
                                      BlockSyncService blockSyncService,
                                      SyncBlockValidatorRule blockValidationRule,
                                      List<Deque<BlockHeader>> pendingHeaders,
                                      Map<Peer, List<BlockIdentifier>> skeletons) {

        super(syncEventsHandler, syncConfiguration);
        this.peersInformation = peersInformation;
        this.blockchain = blockchain;
        this.blockFactory = blockFactory;
        this.limit = syncConfiguration.getTimeoutWaitingRequest();
        this.blockSyncService = blockSyncService;
        this.blockValidationRule = blockValidationRule;
        this.pendingBodyResponses = new HashMap<>();
        this.pendingHeaders = pendingHeaders;
        this.skeletons = skeletons;
        this.segmentByNode = new HashMap<>();
        this.chunksBySegment = new ArrayList<>();
        this.segmentByChunk = new HashMap<>();
        this.inFlightByPeer = new HashMap<>();
        this.sentTimestampsByPeer = new HashMap<>();
        this.consecutiveTimeoutsByPeer = new HashMap<>();
        this.discardedPeers = new HashSet<>();
        this.allowanceByPeer = new HashMap<>();

        this.maxInFlightPerPeer = Math.max(1, syncConfiguration.getMaxInFlightBodyRequestsPerPeer());
        this.maxRequestsPerMinutePerPeer = syncConfiguration.getMaxBodyRequestsPerMinutePerPeer();

        initializeSegments();
        this.suitablePeers = new ArrayList<>(segmentByNode.keySet());
    }

    @Override
    public void onEnter() {
        refreshSuitablePeers();
        logger.info("Starting body download from {} peer(s), up to {} request(s) in flight each, {} req/min cap",
                suitablePeers.size(), maxInFlightPerPeer,
                maxRequestsPerMinutePerPeer > 0 ? String.valueOf(maxRequestsPerMinutePerPeer) : "no");
        startDownloading(new ArrayList<>(suitablePeers));
    }

    @Override
    public void newBody(BodyResponseMessage message, Peer peer) {
        NodeID peerId = peer.getPeerNodeID();
        long requestId = message.getId();
        if (!isExpectedBody(requestId, peerId)) {
            handleUnexpectedBody(peer);
            return;
        }

        // we already checked that this message was expected
        PendingBodyResponse pending = pendingBodyResponses.remove(requestId);
        boolean wasSaturated = inFlightCount(peer) >= allowanceOf(peer);
        untrackInFlight(peer, requestId);
        consecutiveTimeoutsByPeer.put(peer, 0);
        if (wasSaturated) {
            allowanceByPeer.put(peer, Math.min(maxInFlightPerPeer, allowanceOf(peer) + 1));
        }

        BlockHeader header = pending.header;
        header.setExtension(message.getBlockHeaderExtension());
        Block block;
        try {
            block = blockFactory.newBlock(header, message.getTransactions(), message.getUncles());
            block.seal();
        } catch (IllegalArgumentException ex) {
            handleInvalidBody(peer, pending);
            return;
        }

        if (!blockValidationRule.isValid(block)) {
            handleInvalidBody(peer, pending);
            return;
        }

        // handle block
        // this is a controled place where we ask for blocks, we never should look for missing hashes
        if (blockSyncService.processBlock(block, peer, true).isInvalidBlock()) {
            handleInvalidBlock(peer, pending);
            return;
        }

        // keep this peer's pipeline topped up
        fillPeer(peer);
        verifyDownloadIsFinished();
    }

    private void verifyDownloadIsFinished() {
        if (isDownloadComplete()) {
            // Finished syncing
            logger.info("Completed syncing phase");
            syncEventsHandler.stopSyncing();
        }
    }

    /**
     * The range is done once no header is left to request and every request still outstanding is
     * redundant, i.e. its block already reached the blockchain by another route. Waiting for those
     * to come back (or time out) would stall the end of every round for no benefit, while waiting
     * for genuinely missing bodies is still required.
     */
    private boolean isDownloadComplete() {
        if (!pendingHeaders.stream().allMatch(Collection::isEmpty)) {
            return false;
        }
        return pendingBodyResponses.values().stream()
                .allMatch(pending -> isBlockKnown(pending.header.getHash()));
    }

    @Override
    public void tick(Duration duration) {
        // age every in-flight request and collect the ones that went past the limit
        List<Long> timedOut = new ArrayList<>();
        for (Map.Entry<Long, PendingBodyResponse> entry : pendingBodyResponses.entrySet()) {
            PendingBodyResponse pending = entry.getValue();
            pending.elapsed = pending.elapsed.plus(duration);
            if (pending.elapsed.compareTo(limit) >= 0) {
                timedOut.add(entry.getKey());
            }
        }

        for (Long requestId : timedOut) {
            handleTimeoutMessage(requestId);
        }

        // Peers connect and report their status continuously; pick up any that became usable since
        // this phase started instead of running the whole range with whoever happened to be ready.
        refreshSuitablePeers();

        if (suitablePeers.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        startDownloading(new ArrayList<>(suitablePeers));

        if (isDownloadComplete()) {
            logger.info("Completed syncing phase");
            syncEventsHandler.stopSyncing();
        } else if (pendingBodyResponses.isEmpty()) {
            if (!lastFillRateLimited) {
                // nothing in flight, work left, and no peer able to take it: we cannot make progress
                logger.warn("No peer can serve the {} remaining chunk(s); stopping sync",
                        pendingHeaders.stream().filter(d -> !d.isEmpty()).count());
                syncEventsHandler.stopSyncing();
            }
        }
    }

    /**
     * Adds every currently usable peer to the rotation.
     *
     * <p>A peer does not need to have contributed a skeleton to serve bodies: the body it returns is
     * checked against the header we already trust (transactions root and uncles hash), so a peer
     * that sends the wrong body is detected and penalised exactly as before. Restricting the
     * download to the handful of peers that answered the skeleton request left most of the
     * connected peers idle for the whole phase.
     */
    private void refreshSuitablePeers() {
        for (Peer peer : peersInformation.getBestPeerCandidates()) {
            if (!discardedPeers.contains(peer) && !suitablePeers.contains(peer)) {
                suitablePeers.add(peer);
            }
        }
    }

    /** Highest block this peer claims to have, or -1 when it has not reported a status yet. */
    private long bestBlockNumberOf(Peer peer) {
        SyncPeerStatus peerStatus = peersInformation.getPeer(peer);
        if (peerStatus == null || peerStatus.getStatus() == null) {
            return -1L;
        }
        return peerStatus.getStatus().getBestBlockNumber();
    }

    private void startDownloading(List<Peer> peers) {
        lastFillRateLimited = false;
        peers.forEach(this::fillPeer);
    }

    /**
     * Sends as many body requests to {@code peer} as the in-flight window and the outbound rate
     * limiter allow.
     */
    private void fillPeer(Peer peer) {
        if (!suitablePeers.contains(peer)) {
            return;
        }

        int allowance = allowanceOf(peer);
        while (inFlightCount(peer) < allowance) {
            if (!allowSend(peer)) {
                // there is capacity but we would go over the peer's per-minute message threshold
                lastFillRateLimited = true;
                return;
            }
            Optional<Assignment> assignment = pollAssignmentFor(peer);
            if (!assignment.isPresent()) {
                return;
            }
            sendRequest(peer, assignment.get());
        }
    }

    private void sendRequest(Peer peer, Assignment assignment) {
        long messageId = syncEventsHandler.sendBodyRequest(peer, assignment.header);
        PendingBodyResponse pending =
                new PendingBodyResponse(peer.getPeerNodeID(), assignment.header, peer, assignment.chunk);
        pendingBodyResponses.put(messageId, pending);
        inFlightByPeer.computeIfAbsent(peer, k -> new HashSet<>()).add(messageId);
        recordSend(peer);
    }

    /**
     * Picks the next header this peer is able to serve, scanning from its own segment downwards.
     * Chunks are shared: several peers may drain the same chunk concurrently, and each header is
     * handed out exactly once because it is removed from the deque here.
     */
    private Optional<Assignment> pollAssignmentFor(Peer peer) {
        Integer peerSegment = segmentByNode.get(peer);
        long peerBest = Long.MAX_VALUE;
        int startSegment;
        if (peerSegment != null) {
            // This peer's skeleton covers these chunks, so no extra height check is needed.
            startSegment = peerSegment;
        } else {
            // No skeleton from this peer: it may still serve any header it is tall enough for.
            if (chunksBySegment.isEmpty()) {
                return Optional.empty();
            }
            startSegment = chunksBySegment.size() - 1;
            peerBest = bestBlockNumberOf(peer);
            if (peerBest < 0) {
                return Optional.empty();
            }
        }

        for (int segmentNumber = startSegment; segmentNumber >= 0; segmentNumber--) {
            Deque<Integer> chunks = chunksBySegment.get(segmentNumber);
            while (!chunks.isEmpty()) {
                Integer chunkNumber = chunks.peekLast();
                Deque<BlockHeader> headers = pendingHeaders.get(chunkNumber);
                BlockHeader header = headers.poll();
                while (header != null) {
                    // we double check if the header was not downloaded or obtained by another way
                    if (!isBlockKnown(header.getHash())) {
                        if (header.getNumber() > peerBest) {
                            // this peer is not tall enough for this header; leave it for another one
                            headers.addFirst(header);
                            return Optional.empty();
                        }
                        return Optional.of(new Assignment(header, chunkNumber));
                    }
                    header = headers.poll();
                }
                // this chunk is drained, move on to the next one in the segment
                chunks.pollLast();
            }
        }
        return Optional.empty();
    }

    /** Puts a header back so that another request (possibly to another peer) can pick it up. */
    private void requeue(PendingBodyResponse pending) {
        int chunkNumber = pending.chunk;
        if (chunkNumber < 0 || chunkNumber >= pendingHeaders.size()) {
            return;
        }
        pendingHeaders.get(chunkNumber).addFirst(pending.header);

        Integer segmentNumber = segmentByChunk.get(chunkNumber);
        if (segmentNumber != null) {
            Deque<Integer> chunks = chunksBySegment.get(segmentNumber);
            if (!chunks.contains(chunkNumber)) {
                chunks.addLast(chunkNumber);
            }
        }
    }

    private void handleTimeoutMessage(long requestId) {
        PendingBodyResponse pending = pendingBodyResponses.remove(requestId);
        if (pending == null) {
            return;
        }
        Peer peer = pending.peer;
        if (peer != null) {
            untrackInFlight(peer, requestId);

            // Back off rather than discard: a slower pipeline still contributes blocks.
            allowanceByPeer.put(peer, Math.max(1, allowanceOf(peer) / 2));

            int timeouts = consecutiveTimeoutsByPeer.merge(peer, 1, Integer::sum);
            if (timeouts >= MAX_CONSECUTIVE_TIMEOUTS_PER_PEER) {
                // Squeezed all the way down and still silent: the peer is not answering at all.
                logger.warn("Discarding peer {} after {} consecutive body timeouts",
                        peer.getPeerNodeID(), timeouts);
                peersInformation.reportEventToPeerScoring(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting body on {}", this.getClass());
                dropPeer(peer);
            }
        }
        requeue(pending);
    }

    private void handleInvalidBlock(Peer peer, PendingBodyResponse pending) {
        peersInformation.reportEventToPeerScoring(
                peer, EventType.INVALID_BLOCK,
                "Invalid block received on {}, no {}, hash {}",
                this.getClass(), pending.header.getNumber(), pending.header.getPrintableHash());

        dropPeer(peer);
        requeue(pending);
        rescheduleAfterPeerLoss();
    }

    private void handleInvalidBody(Peer peer, PendingBodyResponse pending) {
        peersInformation.reportEventToPeerScoring(
                peer, EventType.INVALID_MESSAGE,
                "Invalid body received on {}, no {}, hash {}",
                this.getClass(), pending.header.getNumber(), pending.header.getPrintableHash());

        dropPeer(peer);
        requeue(pending);
        rescheduleAfterPeerLoss();
    }

    private void handleUnexpectedBody(Peer peer) {
        peersInformation.reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                "Unexpected body received on {}", this.getClass());

        dropPeer(peer);
        rescheduleAfterPeerLoss();
    }

    private void rescheduleAfterPeerLoss() {
        if (suitablePeers.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }
        startDownloading(new ArrayList<>(suitablePeers));
    }

    /** Removes a peer from the rotation and re-queues everything it still owed us. */
    private void dropPeer(Peer peer) {
        suitablePeers.remove(peer);
        discardedPeers.add(peer);
        sentTimestampsByPeer.remove(peer);
        consecutiveTimeoutsByPeer.remove(peer);
        allowanceByPeer.remove(peer);

        Set<Long> inFlight = inFlightByPeer.remove(peer);
        if (inFlight == null) {
            return;
        }
        for (Long requestId : inFlight) {
            PendingBodyResponse pending = pendingBodyResponses.remove(requestId);
            if (pending != null) {
                requeue(pending);
            }
        }
    }

    private int allowanceOf(Peer peer) {
        return allowanceByPeer.getOrDefault(peer, maxInFlightPerPeer);
    }

    private int inFlightCount(Peer peer) {
        Set<Long> inFlight = inFlightByPeer.get(peer);
        return inFlight == null ? 0 : inFlight.size();
    }

    private void untrackInFlight(Peer peer, long requestId) {
        Set<Long> inFlight = inFlightByPeer.get(peer);
        if (inFlight != null) {
            inFlight.remove(requestId);
        }
    }

    /**
     * True when another request may be sent to this peer without crossing its per-minute message
     * threshold. Peers count every message they receive from us within a calendar minute and start
     * rejecting once the threshold is passed, which would stall the download.
     */
    private boolean allowSend(Peer peer) {
        if (maxRequestsPerMinutePerPeer <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = sentTimestampsByPeer.computeIfAbsent(peer, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= RATE_WINDOW_MS) {
            timestamps.pollFirst();
        }
        return timestamps.size() < Math.max(1, maxRequestsPerMinutePerPeer / 2);
    }

    private void recordSend(Peer peer) {
        if (maxRequestsPerMinutePerPeer <= 0) {
            return;
        }
        sentTimestampsByPeer.computeIfAbsent(peer, k -> new ArrayDeque<>())
                .addLast(System.currentTimeMillis());
    }

    private boolean isBlockKnown(Keccak256 hash) {
        return blockchain.getBlockByHash(hash.getBytes()) != null;
    }

    /**
     * This method finds with the skeletons from each node, the segments we can divide chunks.
     * Each chunk belongs to a single segment, and each node associated to a segment can answer
     * for each block inside the chunks belonging to a segment.
     * Also each node on a superior segment can answer for every block on lower segments.
     * The idea is to find the "min common chunks" between nodes to find when a new segment starts
     */
    private void initializeSegments() {
        if (pendingHeaders.isEmpty()) {
            return;
        }

        Deque<Integer> segmentChunks = new ArrayDeque<>();
        int segmentNumber = 0;
        int chunkNumber = 0;
        List<Peer> nodes = getAvailableNodesIDSFor(chunkNumber);
        List<Peer> prevNodes = nodes;
        segmentChunks.push(chunkNumber);
        chunkNumber++;

        for (; chunkNumber < pendingHeaders.size(); chunkNumber++) {
            nodes = getAvailableNodesIDSFor(chunkNumber);
            if (prevNodes.size() != nodes.size()) {
                final List<Peer> filteringNodes = nodes;
                List<Peer> insertedNodes = prevNodes.stream()
                        .filter(k -> !filteringNodes.contains(k)).collect(Collectors.toList());
                insertSegment(segmentChunks, insertedNodes, segmentNumber);
                segmentNumber++;
                prevNodes = nodes;
                segmentChunks = new ArrayDeque<>();
            }
            segmentChunks.push(chunkNumber);
        }

        // last segment should be added always
        insertSegment(segmentChunks, nodes, segmentNumber);
    }

    private List<Peer> getAvailableNodesIDSFor(Integer chunkNumber) {
        return skeletons.entrySet().stream()
                .filter(e -> e.getValue().size() > chunkNumber + 1)
                .filter(e -> ByteUtil.fastEquals(
                    // the hash of the start of next chunk
                    e.getValue().get(chunkNumber + 1).getHash(),
                    // the first header of chunk
                    pendingHeaders.get(chunkNumber).getLast().getHash().getBytes()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void insertSegment(Deque<Integer> segmentChunks, List<Peer> nodes, Integer segmentNumber) {
        chunksBySegment.add(segmentChunks);
        segmentChunks.forEach(chunk -> segmentByChunk.put(chunk, segmentNumber));
        nodes.forEach(peer -> segmentByNode.put(peer, segmentNumber));
    }

    private boolean isExpectedBody(long requestId, NodeID peerId) {
        PendingBodyResponse expected = pendingBodyResponses.get(requestId);
        return expected != null && expected.nodeID.equals(peerId);
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    @VisibleForTesting
    int getInFlightCount() {
        return pendingBodyResponses.size();
    }

    @VisibleForTesting
    List<Peer> getSuitablePeers() {
        return suitablePeers;
    }

    private static final class Assignment {
        private final BlockHeader header;
        private final int chunk;

        private Assignment(BlockHeader header, int chunk) {
            this.header = header;
            this.chunk = chunk;
        }
    }

    @VisibleForTesting
    protected static class PendingBodyResponse {
        private NodeID nodeID;
        private BlockHeader header;
        private Peer peer;
        private int chunk;
        private Duration elapsed;

        PendingBodyResponse(NodeID nodeID, BlockHeader header) {
            this(nodeID, header, null, -1);
        }

        PendingBodyResponse(NodeID nodeID, BlockHeader header, Peer peer, int chunk) {
            this.nodeID = nodeID;
            this.header = header;
            this.peer = peer;
            this.chunk = chunk;
            this.elapsed = Duration.ZERO;
        }
    }
}
