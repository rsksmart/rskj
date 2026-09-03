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
     * Last-resort threshold, counted in <em>ticks during which a peer timed out</em>, not in expired
     * requests. Counting requests tied the threshold to the pipeline depth: with many requests in
     * flight a single silent peer expires all of them at once and would be discarded immediately,
     * which in turn emptied the peer set and aborted the whole round.
     */
    private static final int MAX_CONSECUTIVE_TIMEOUT_TICKS_PER_PEER = 20;

    /**
     * Below this many simultaneously-silent peers, "they all timed out" is not evidence of anything:
     * with one peer awaited it is the ordinary single-peer timeout.
     */
    private static final int MIN_PEERS_TO_INFER_LOCAL_STALL = 2;

    private int consecutiveLocalStallTicks;

    /**
     * Fraction of the awaited peers that must go silent together before we read it as our own stall
     * rather than theirs. Requiring *all* of them was too strict to be useful: a stall rarely lands
     * on every outstanding request at once, so partial stalls fell through to the per-peer path and
     * still cost us the peer set. Measured on a live sync, requiring all caught 51 stalls while 48
     * peers were still discarded around them.
     */
    private static final double LOCAL_STALL_PEER_FRACTION = 2.0 / 3.0;

    /**
     * How many consecutive ticks we are willing to explain away as our own stall.
     *
     * <p>Without a bound this inference livelocks. If the peer set genuinely goes bad and stays
     * bad, every tick looks identical - all peers time out together - so it is read as a local
     * stall forever, nobody is ever penalised or replaced, and the same unresponsive peers are
     * re-requested indefinitely. Observed in the field: 96 consecutive ticks over 48 minutes with
     * zero blocks imported, on a node whose peers were all still nominally connected.
     *
     * <p>A real local stall is a GC pause, a flush or a slow disk: seconds, not minutes. After this
     * many ticks the benefit of the doubt is withdrawn and the normal per-peer timeout accounting
     * takes over, which is what eventually discards dead peers and lets the round recover.
     */
    private static final int MAX_CONSECUTIVE_LOCAL_STALL_TICKS = 4;

    /**
     * Half-minute throttling window. A peer counts the messages it receives from us inside each
     * calendar minute, so a rolling 60s budget could still put nearly twice the limit into one of
     * its minutes when the two windows straddle. Limiting to half the budget over 30s makes the
     * bound provable: any 60s interval is the union of two disjoint 30s windows, so it can never
     * carry more than the configured per-minute allowance.
     */
    private static final long RATE_WINDOW_MS = 30_000L;

    /**
     * A round can only finish as fast as its slowest outstanding request. Peers differ enormously
     * in how quickly they answer - measured service rates across a live peer set spanned more than a
     * factor of two - so a block parked behind a slow peer holds up the round while faster peers sit
     * idle. Any request older than this is additionally asked of a second peer, and whichever answer
     * arrives first wins.
     */
    private static final Duration HEDGE_AFTER = Duration.ofSeconds(3);

    /**
     * Hedging only pays while there is spare capacity to spend on it, so it is limited to this
     * fraction of a peer's allowance. Without a bound, a slow patch across the whole peer set would
     * turn into a second full copy of the range.
     */
    private static final int HEDGE_CAPACITY_DIVISOR = 2;

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
    private final boolean deriveEmptyBodies;

    // set when a fill attempt was held back purely by the outbound rate limiter
    private boolean lastFillRateLimited;

    /**
     * Set when a fill derived at least one body locally. Like {@link #lastFillRateLimited} this
     * marks "progress was made without putting a request in flight", which the end-of-tick check
     * must not mistake for "no peer can serve the remaining work".
     */
    private boolean lastFillDerivedBodies;

    /**
     * Ceiling on bodies derived in a single fill. Deriving costs no round trip, so the temptation is
     * to drain every derivable header at once - but each one is executed inline on this thread, and
     * a round can contain thousands of them. Bounding the burst keeps the peers' pipelines topped up
     * between batches; {@code tick} and every arriving body call back in, so the rest follows
     * immediately.
     */
    private static final int MAX_DERIVED_BODIES_PER_FILL = 64;

    /**
     * Headers whose derived body failed validation. Without this a rejected derivation would be
     * requeued, derived again, and rejected forever; recording it sends the header down the normal
     * request path instead, where a peer answers for its own body as before.
     */
    private final Set<Keccak256> underivableHeaders = new HashSet<>();

    // bodies derived from their header during this phase, for the end-of-phase log line
    private int derivedBodyCount;

    /**
     * Request ids superseded by a hedge. A late answer for one of these is ignored rather than
     * punished. Bounded as an LRU: clearing the whole set instead would make later duplicates look
     * like unsolicited bodies and cost us the peers that sent them.
     */
    private static final int MAX_ABANDONED_TRACKED = 8192;
    private final Set<Long> abandonedRequests = Collections.newSetFromMap(
            new LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > MAX_ABANDONED_TRACKED;
                }
            });

    // headers that already have a hedge in flight, so we only duplicate once
    private final Set<Keccak256> hedgedHeaders = new HashSet<>();

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
        this.deriveEmptyBodies = syncConfiguration.isDeriveEmptyBodiesEnabled();

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
            if (abandonedRequests.remove(requestId)) {
                // the other copy of a hedged request already delivered this body
                return;
            }
            handleUnexpectedBody(peer);
            return;
        }

        // we already checked that this message was expected
        PendingBodyResponse pending = pendingBodyResponses.remove(requestId);
        dropSiblingRequests(requestId, pending.header.getHash());
        boolean wasSaturated = inFlightCount(peer) >= allowanceOf(peer);
        untrackInFlight(peer, requestId);
        consecutiveTimeoutsByPeer.put(peer, 0);
        // A body actually arrived, so whatever we were is not stalled. Clear the local-stall
        // patience counter, otherwise a run of bad ticks earlier would still count against the
        // bound long after the node recovered.
        consecutiveLocalStallTicks = 0;
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

    private void logCompletion() {
        logger.info("Completed syncing phase, {} of the round's bodies derived from their headers without a request",
                derivedBodyCount);
    }

    private void verifyDownloadIsFinished() {
        if (isDownloadComplete()) {
            // Finished syncing
            logCompletion();
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

        // Who we were actually waiting on, captured before the timeouts clear the in-flight map.
        Set<Peer> peersAwaited = pendingBodyResponses.values().stream()
                .map(pending -> pending.peer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Peer> peersThatTimedOut = new HashSet<>();
        for (Long requestId : timedOut) {
            Peer peer = handleTimeoutMessage(requestId);
            if (peer != null) {
                peersThatTimedOut.add(peer);
            }
        }

        boolean looksLocal = isLocalStall(peersAwaited, peersThatTimedOut);
        if (looksLocal && consecutiveLocalStallTicks < MAX_CONSECUTIVE_LOCAL_STALL_TICKS) {
            // Everyone went quiet at once, which is not something independent peers do. Far more
            // likely we stalled - a long GC, the host swapping, a slow disk - and could not read
            // their answers in time. Penalising them for that costs the entire peer set, and then
            // the round has to rediscover it while we are still stalled. The requests have already
            // been re-queued, so the round continues; we simply do not blame anyone for it.
            //
            // Only for a bounded number of ticks though: see MAX_CONSECUTIVE_LOCAL_STALL_TICKS.
            consecutiveLocalStallTicks++;
            logger.warn("{} of {} peers timed out in the same tick; treating it as a local stall, not peer failure"
                            + " ({} of {} consecutive ticks)",
                    peersThatTimedOut.size(), peersAwaited.size(),
                    consecutiveLocalStallTicks, MAX_CONSECUTIVE_LOCAL_STALL_TICKS);
        } else {
            if (looksLocal) {
                logger.warn("{} consecutive ticks looked like a local stall; that is too long to be one."
                                + " Resuming normal per-peer timeout accounting so dead peers get replaced.",
                        consecutiveLocalStallTicks);
            }
            consecutiveLocalStallTicks = 0;
            for (Peer peer : peersThatTimedOut) {
                int ticks = consecutiveTimeoutsByPeer.merge(peer, 1, Integer::sum);
                if (ticks >= MAX_CONSECUTIVE_TIMEOUT_TICKS_PER_PEER) {
                    logger.warn("Discarding peer {} after {} consecutive ticks with body timeouts",
                            peer.getPeerNodeID(), ticks);
                    peersInformation.reportEventToPeerScoring(peer, EventType.TIMEOUT_MESSAGE,
                            "Timeout waiting body on {}", this.getClass());
                    dropPeer(peer);
                }
            }
        }

        // Peers connect and report their status continuously; pick up any that became usable since
        // this phase started instead of running the whole range with whoever happened to be ready.
        refreshSuitablePeers();

        if (suitablePeers.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }

        startDownloading(new ArrayList<>(suitablePeers));
        hedgeStragglers();

        if (isDownloadComplete()) {
            logCompletion();
            syncEventsHandler.stopSyncing();
        } else if (pendingBodyResponses.isEmpty()) {
            if (!lastFillRateLimited && !lastFillDerivedBodies) {
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
        lastFillDerivedBodies = false;
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

        int derivedHere = 0;
        int allowance = allowanceOf(peer);
        while (inFlightCount(peer) < allowance) {
            // Poll before checking the rate limiter: a derivable header costs the peer nothing, so
            // being at the outbound limit is no reason not to take it.
            Optional<Assignment> polled = pollAssignmentFor(peer);
            if (!polled.isPresent()) {
                break;
            }
            Assignment assignment = polled.get();

            if (canDerive(assignment.header)) {
                if (derivedHere >= MAX_DERIVED_BODIES_PER_FILL) {
                    requeue(assignment.header, assignment.chunk);
                    break;
                }
                deriveBody(assignment);
                derivedHere++;
                continue;
            }

            if (!allowSend(peer)) {
                // there is capacity but we would go over the peer's per-minute message threshold
                requeue(assignment.header, assignment.chunk);
                lastFillRateLimited = true;
                break;
            }
            sendRequest(peer, assignment);
        }

        if (derivedHere > 0) {
            // Deliberately not signalling completion from here: this runs inside the per-peer fill
            // loop, and stopping the phase mid-iteration would let the remaining peers keep sending
            // requests for a round that is already over. `tick` and `newBody` both check, so a round
            // finished entirely by derivation is picked up on the next tick.
            lastFillDerivedBodies = true;
        }
    }

    /**
     * True when this header's body follows from the header itself, so no peer has to send it. Worth
     * roughly a fifth of mainnet: measured over the whole chain to block 9,190,721, 22.31% of blocks
     * have no uncles and carry only their REMASC transaction.
     */
    private boolean canDerive(BlockHeader header) {
        return deriveEmptyBodies
                && !underivableHeaders.contains(header.getHash())
                && blockFactory.hasDerivableBody(header);
    }

    /**
     * Builds a body locally and feeds it through the very same construction, validation and import
     * path a downloaded body takes, so nothing downstream can tell the two apart.
     *
     * <p>If validation rejects it, the header is marked underivable and requeued rather than
     * dropped: it then goes out as an ordinary request and a peer answers for it as before. No peer
     * is penalised, because no peer sent this body.
     */
    private void deriveBody(Assignment assignment) {
        BlockHeader header = assignment.header;
        Block block;
        try {
            block = blockFactory.newBlockWithDerivedBody(header);
        } catch (IllegalArgumentException ex) {
            logger.warn("Derived body rejected for block {}; requesting it instead", header.getNumber(), ex);
            underivableHeaders.add(header.getHash());
            requeue(header, assignment.chunk);
            return;
        }

        if (!blockValidationRule.isValid(block)) {
            logger.warn("Derived body failed validation for block {}; requesting it instead", header.getNumber());
            underivableHeaders.add(header.getHash());
            requeue(header, assignment.chunk);
            return;
        }

        derivedBodyCount++;
        // No peer sent this block, so there is no sender to attribute or to blame.
        blockSyncService.processBlock(block, null, true);
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
        if (chunksBySegment.isEmpty()) {
            return Optional.empty();
        }
        // Any peer tall enough may serve any header: the body is checked against the trusted
        // header regardless of who sent it. Capping a peer at the segment its own skeleton covers
        // left most peers idle for the later chunks of an extended range.
        int startSegment = chunksBySegment.size() - 1;
        long peerBest = bestBlockNumberOf(peer);
        if (peerBest < 0) {
            return Optional.empty();
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
        requeue(pending.header, pending.chunk);
    }

    private void requeue(BlockHeader header, int chunkNumber) {
        if (chunkNumber < 0 || chunkNumber >= pendingHeaders.size()) {
            return;
        }
        pendingHeaders.get(chunkNumber).addFirst(header);

        Integer segmentNumber = segmentByChunk.get(chunkNumber);
        if (segmentNumber != null) {
            Deque<Integer> chunks = chunksBySegment.get(segmentNumber);
            if (!chunks.contains(chunkNumber)) {
                chunks.addLast(chunkNumber);
            }
        }
    }

    /**
     * True when a timeout looks like our own stall rather than the peers' fault.
     *
     * <p>Peers fail independently, so one going quiet says something about that peer. Most of them
     * going quiet in the same tick says something about us. Requiring at least two peers keeps the
     * single-peer case - the one this check cannot distinguish - on the normal path.
     */
    private boolean isLocalStall(Set<Peer> peersAwaited, Set<Peer> peersThatTimedOut) {
        if (peersThatTimedOut.size() < MIN_PEERS_TO_INFER_LOCAL_STALL) {
            return false;
        }
        return peersThatTimedOut.size() >= Math.ceil(peersAwaited.size() * LOCAL_STALL_PEER_FRACTION);
    }

    /** Re-queues one expired request and backs its peer off. Returns the peer, if known. */
    private Peer handleTimeoutMessage(long requestId) {
        PendingBodyResponse pending = pendingBodyResponses.remove(requestId);
        if (pending == null) {
            return null;
        }
        Peer peer = pending.peer;
        if (peer != null) {
            untrackInFlight(peer, requestId);
            // Back off rather than discard: a slower pipeline still contributes blocks.
            allowanceByPeer.put(peer, Math.max(1, allowanceOf(peer) / 2));
        }
        requeue(pending);
        return peer;
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

    /**
     * An unsolicited body is cheap to ignore and is now an ordinary occurrence: a hedged request
     * whose loser answers after the winner, or a body that arrived just after its request timed out
     * and was re-queued elsewhere. Bodies are checked against the trusted header regardless, so
     * discarding the peer over one costs throughput and buys nothing.
     */
    private void handleUnexpectedBody(Peer peer) {
        peersInformation.reportEventToPeerScoring(peer, EventType.UNEXPECTED_MESSAGE,
                "Unexpected body received on {}", this.getClass());
        fillPeer(peer);
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

    /** Retires any other in-flight request for the same header (the losing side of a hedge). */
    private void dropSiblingRequests(long winningRequestId, Keccak256 headerHash) {
        if (hedgedHeaders.isEmpty()) {
            return;
        }
        hedgedHeaders.remove(headerHash);
        List<Long> siblings = new ArrayList<>();
        for (Map.Entry<Long, PendingBodyResponse> e : pendingBodyResponses.entrySet()) {
            if (e.getKey() != winningRequestId && e.getValue().header.getHash().equals(headerHash)) {
                siblings.add(e.getKey());
            }
        }
        for (Long id : siblings) {
            PendingBodyResponse sibling = pendingBodyResponses.remove(id);
            if (sibling != null && sibling.peer != null) {
                untrackInFlight(sibling.peer, id);
            }
            abandonedRequests.add(id);
        }
    }

    /**
     * Duplicates long-outstanding requests onto a second peer, but only once the whole range has
     * been handed out, so hedging never competes with fresh work for the request budget.
     */
    private void hedgeStragglers() {
        // Only once the whole range has been handed out. Hedging mid-round was measured and it made
        // things slightly worse: a duplicate is another message, and a peer's queue is served in
        // priority order with the sender's message rate counting against it, so the extra copies
        // cost more in priority than they win back in latency. At the tail there is no first-copy
        // work left to lose priority for, and a single stalled request is what ends the round.
        if (!pendingHeaders.stream().allMatch(Collection::isEmpty)) {
            return;
        }
        List<PendingBodyResponse> stragglers = new ArrayList<>();
        for (PendingBodyResponse pending : pendingBodyResponses.values()) {
            if (pending.peer != null
                    && pending.elapsed.compareTo(HEDGE_AFTER) >= 0
                    && !hedgedHeaders.contains(pending.header.getHash())) {
                stragglers.add(pending);
            }
        }
        for (PendingBodyResponse pending : stragglers) {
            Peer other = pickAlternativePeer(pending.peer);
            if (other == null) {
                continue;
            }
            long messageId = syncEventsHandler.sendBodyRequest(other, pending.header);
            pendingBodyResponses.put(messageId,
                    new PendingBodyResponse(other.getPeerNodeID(), pending.header, other, pending.chunk));
            inFlightByPeer.computeIfAbsent(other, k -> new HashSet<>()).add(messageId);
            recordSend(other);
            hedgedHeaders.add(pending.header.getHash());
        }
    }

    /**
     * Picks the least loaded peer other than the one already sitting on this request, and only one
     * with genuine spare capacity - hedging is worth doing with an idle peer, not by displacing
     * first-copy work on a busy one.
     */
    private Peer pickAlternativePeer(Peer exclude) {
        Peer best = null;
        int bestLoad = Integer.MAX_VALUE;
        for (Peer candidate : suitablePeers) {
            if (candidate.equals(exclude) || !allowSend(candidate)) {
                continue;
            }
            int load = inFlightCount(candidate);
            int spare = Math.max(1, allowanceOf(candidate) / HEDGE_CAPACITY_DIVISOR);
            if (load < spare && load < bestLoad) {
                bestLoad = load;
                best = candidate;
            }
        }
        return best;
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
