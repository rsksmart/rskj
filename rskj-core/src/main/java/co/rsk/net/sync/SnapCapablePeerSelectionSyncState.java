/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.BlockHeader;
import org.ethereum.validator.DependentBlockHeaderRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SnapCapablePeerSelectionSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private static final int HEADERS_VALIDATION_COUNT = 64;

    private final PeersInformation peersInformation;
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final DependentBlockHeaderRule blockParentValidationRule;

    private final Set<Peer> pendingCheckpointHashRequests = ConcurrentHashMap.newKeySet();
    private final Set<Peer> pendingHeadersRequests = ConcurrentHashMap.newKeySet();

    private final Map<Peer, byte[]> peerCheckpointHashes = new ConcurrentHashMap<>();
    private final Map<Peer, ValidatedHeader> validatedPeers = new ConcurrentHashMap<>();

    private volatile boolean isWaitingForHeaders;
    private volatile boolean isWaitingForBlockHashes;

    public SnapCapablePeerSelectionSyncState(SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration,
                                             PeersInformation peersInformation, BlockHeaderValidationRule blockHeaderValidationRule,
                                             DependentBlockHeaderRule blockParentValidationRule) {
        super(syncEventsHandler, syncConfiguration);
        this.peersInformation = peersInformation;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.blockParentValidationRule = blockParentValidationRule;
    }

    @Override
    public void onEnter() {
        List<Peer> snapPeerCandidates = this.peersInformation.getBestSnapPeerCandidates();
        if (snapPeerCandidates.isEmpty()) {
            logger.warn("No snap capable peers found");
            this.syncEventsHandler.stopSyncing();
        } else {
            Set<NodeID> snapBootNodeIds = syncConfiguration.getSnapBootNodeIds();
            Peer snapBootNode = snapPeerCandidates.stream().map(p -> Pair.of(p, peersInformation.getPeer(p)))
                    .filter(pair -> isPeerStatusAvailable(pair.getValue()))
                    .filter(pair -> snapBootNodeIds.contains(pair.getKey().getPeerNodeID())).max(
                    Comparator.comparing(pair -> pair.getValue().getStatus().getBestBlockNumber())
            ).map(Pair::getKey).orElse(null);
            if (snapBootNode != null) {
                logger.info("Found snap boot node: {}", snapBootNode.getPeerNodeID());
                onPeerSelectionComplete(snapBootNode, null);
                return;
            }

            logger.info("Selecting from: [{}] snap capable peer candidates", snapPeerCandidates.size());
            selectPeerFromCandidates(snapPeerCandidates);
        }
    }

    private void selectPeerFromCandidates(List<Peer> snapPeerCandidates) {
        logger.info("Requesting checkpoint block hashes from {} peers", snapPeerCandidates.size());

        isWaitingForBlockHashes = true;
        boolean sent = false;
        for (Peer peer : snapPeerCandidates) {
            Optional<Long> checkpointBlockNumberOpt = getCheckpointBlockNumber(peer);
            if (checkpointBlockNumberOpt.isEmpty()) {
                continue;
            }
            pendingCheckpointHashRequests.add(peer);
            syncEventsHandler.sendBlockHashRequest(peer, checkpointBlockNumberOpt.get());
            sent = true;
        }

        if (!sent) {
            logger.warn("No valid checkpoint block numbers found for snap capable peers");
            syncEventsHandler.stopSyncing();
            return;
        }

        resetTimeElapsed(); // Reset timeout timer after sending requests
    }

    private Optional<Long> getCheckpointBlockNumber(Peer peer) {
        SyncPeerStatus peerStatus = peersInformation.getPeer(peer);
        if (!isPeerStatusAvailable(peerStatus)) {
            logger.warn("Peer {} not found in peers information", peer.getPeerNodeID());
            return Optional.empty();
        }
        long bestBlockNumber = peerStatus.getStatus().getBestBlockNumber();
        long checkpointBlockNumber = BlockUtils.getSnapCheckpointBlockNumber(bestBlockNumber, syncConfiguration);
        if (checkpointBlockNumber < HEADERS_VALIDATION_COUNT) {
            logger.warn("Checkpoint block number {} is too low for peer {}. Minimum required is {}",
                        checkpointBlockNumber, peer.getPeerNodeID(), HEADERS_VALIDATION_COUNT);
            return Optional.empty();
        }

        return Optional.of(checkpointBlockNumber);
    }

    @Override
    public void newConnectionPointData(byte[] hash, Peer peer) {
        if (isWaitingForBlockHashes && pendingCheckpointHashRequests.remove(peer)) {
            peerCheckpointHashes.put(peer, hash);
            logger.debug("Received checkpoint block hash for peer: {}", peer);

            // Check if all block hash requests are complete
            checkBlockHashRequestsComplete();
        } else {
            logger.warn("Received unexpected block hash response from peer: {}", peer.getPeerNodeID());
            reportFailedPeer(peer, EventType.UNEXPECTED_MESSAGE, "Received unexpected block hash response from peer: {}", peer.getPeerNodeID());
        }
    }

    private void checkBlockHashRequestsComplete() {
        if (pendingCheckpointHashRequests.isEmpty()) {
            isWaitingForBlockHashes = false;
            requestBlockHeaders();
        }
    }

    private void requestBlockHeaders() {
        logger.info("Requesting {} blocks from {} peers", HEADERS_VALIDATION_COUNT, peerCheckpointHashes.size());

        isWaitingForHeaders = true;
        peerCheckpointHashes.forEach((peer, checkpointBlockHash) -> {
            pendingHeadersRequests.add(peer);
            ChunkDescriptor chunk = new ChunkDescriptor(checkpointBlockHash, HEADERS_VALIDATION_COUNT);
            syncEventsHandler.sendBlockHeadersRequest(peer, chunk);
        });
        resetTimeElapsed(); // Reset timeout timer after sending requests
    }

    @Override
    public void newBlockHeaders(Peer peer, List<BlockHeader> chunk) {
        if (isWaitingForHeaders && pendingHeadersRequests.remove(peer)) {
            handleHeadersResponse(peer, chunk);
        } else {
            logger.warn("Unexpected block headers response from peer: {}", peer.getPeerNodeID());
            reportFailedPeer(peer, EventType.UNEXPECTED_MESSAGE, "Unexpected block headers response from {}", peer.getPeerNodeID());
        }
    }

    private void handleHeadersResponse(Peer peer, List<BlockHeader> chunk) {
        if (chunk.size() != HEADERS_VALIDATION_COUNT) {
            logger.warn("Expected {} headers but received {} headers from peer: {}", HEADERS_VALIDATION_COUNT, chunk.size(), peer.getPeerNodeID());
            reportFailedPeer(peer, EventType.INVALID_MESSAGE, "Expected {} headers but received {} headers from {}", HEADERS_VALIDATION_COUNT, chunk.size(), peer.getPeerNodeID());
            return;
        }

        // Validate the headers
        if (validateHeaders(chunk)) {
            validatedPeers.put(peer, new ValidatedHeader(chunk.get(0), chunk.stream().map(BlockHeader::getDifficulty).reduce(BlockDifficulty.ZERO, BlockDifficulty::add))); // Store the highest header for this peer
            logger.debug("Peer {} passed headers validation", peer.getPeerNodeID());
        } else {
            logger.warn("Peer {} failed headers validation", peer.getPeerNodeID());
            reportFailedPeer(peer, EventType.INVALID_MESSAGE, "Invalid headers received from {}", peer.getPeerNodeID());
        }

        checkHeadersValidationComplete();
    }

    private boolean validateHeaders(List<BlockHeader> headers) {
        // Headers come ordered by block number desc, so we need to validate them in reverse order
        for (int i = 0; i < headers.size() - 1; i++) {
            BlockHeader header = headers.get(i);
            BlockHeader parentHeader = headers.get(i + 1);

            // Validate individual header
            if (!blockHeaderValidationRule.isValid(header)) {
                return false;
            }

            // Validate parent relationship
            if (!header.getParentHash().equals(parentHeader.getHash()) ||
                header.getNumber() != parentHeader.getNumber() + 1) {
                return false;
            }

            // Validate parent-dependent rules
            if (!blockParentValidationRule.validate(header, parentHeader)) {
                return false;
            }
        }

        // Validate the last header (the lowest number)
        return blockHeaderValidationRule.isValid(headers.get(headers.size() - 1));
    }

    private void checkHeadersValidationComplete() {
        if (pendingHeadersRequests.isEmpty()) {
            isWaitingForHeaders = false;
            selectBestPeerFromValidated();
        }
    }

    private void selectBestPeerFromValidated() {
        if (validatedPeers.isEmpty()) {
            logger.warn("No peers passed validation or all peers timed out");
            syncEventsHandler.stopSyncing();
            return;
        }

        if (validatedPeers.size() == 1) {
            Map.Entry<Peer, ValidatedHeader> selectedPeerEntry = validatedPeers.entrySet().iterator().next();
            logger.info("Only one peer passed validation: {}", selectedPeerEntry.getKey().getPeerNodeID());
            onPeerSelectionComplete(selectedPeerEntry.getKey(), selectedPeerEntry.getValue());
            return;
        }

        ValidatedPeerInfo bestPeerInfo = validatedPeers.entrySet().stream()
                .map(entry -> new ValidatedPeerInfo(entry.getKey(), peersInformation.getPeer(entry.getKey()), entry.getValue()))
                .filter(info -> isPeerStatusAvailable(info.peerStatus))
                .max(peerComparator())
                .orElse(null);
        if (bestPeerInfo == null) {
            logger.warn("No valid peers found after validation");
            syncEventsHandler.stopSyncing();
            return;
        }

        logger.info("Selected peer with highest total difficulty: {}", bestPeerInfo.peer.getPeerNodeID());
        onPeerSelectionComplete(bestPeerInfo.peer, bestPeerInfo.validatedHeader);
    }

    private Comparator<ValidatedPeerInfo> peerComparator() {
        // Select a peer with the highest cumulative difficulty adjusted with multiplying by the number of peers with the same checkpoint header
        Map<Keccak256, Integer> headerHashCounts = validatedPeers.values().stream()
                .map(vh -> vh.header.getHash())
                .collect(Collectors.toMap(
                        hash -> hash,
                        h -> 1,
                        Integer::sum
                ));

        return Comparator.comparing(
                (ValidatedPeerInfo info) -> info.validatedHeader.cumulativeDifficulty.asBigInteger()
                        .multiply(BigInteger.valueOf(headerHashCounts.get(info.validatedHeader.header.getHash())))
        ).thenComparing((ValidatedPeerInfo info) -> info.peerStatus.getStatus().getTotalDifficulty());
    }

    private void onPeerSelectionComplete(Peer selectedSnapCapablePeer, @Nullable ValidatedHeader validatedHeader) {
        this.syncEventsHandler.startSnapSync(selectedSnapCapablePeer, Optional.ofNullable(validatedHeader).map(ValidatedHeader::header).orElse(null));
    }

    @Override
    protected void onMessageTimeOut() {
        handleTimeout();
    }

    private void handleTimeout() {
        if (isWaitingForBlockHashes) {
            // Mark all peers that haven't responded as timed out
            pendingCheckpointHashRequests.forEach(peer -> {
                logger.warn("Peer {} timed out during block hash request", peer.getPeerNodeID());
                reportFailedPeer(peer, EventType.TIMEOUT_MESSAGE, "Timeout waiting for block hash response from {}", peer.getPeerNodeID());
            });
            pendingCheckpointHashRequests.clear();
            isWaitingForBlockHashes = false;

            if (peerCheckpointHashes.isEmpty()) {
                logger.warn("No valid checkpoint hashes received from any peer");
                syncEventsHandler.stopSyncing();
            } else {
                requestBlockHeaders(); // Requesting headers if we have valid checkpoint hashes
            }
        } else if (isWaitingForHeaders) {
            // Mark all peers that haven't responded as timed out
            pendingHeadersRequests.forEach(peer -> {
                logger.warn("Peer {} timed out during headers validation", peer.getPeerNodeID());
                reportFailedPeer(peer, EventType.TIMEOUT_MESSAGE, "Timeout waiting for block headers response from {}", peer.getPeerNodeID());
            });
            pendingHeadersRequests.clear();
            isWaitingForHeaders = false;
            selectBestPeerFromValidated();
        }
    }

    private boolean isPeerStatusAvailable(@Nullable SyncPeerStatus peerStatus) {
        return peerStatus != null && peerStatus.getStatus() != null;
    }

    private void reportFailedPeer(Peer peer, EventType eventType, String message, Object... arguments) {
        peersInformation.processSyncingError(peer, eventType, message, arguments);
    }

    private record ValidatedHeader(BlockHeader header, BlockDifficulty cumulativeDifficulty) { }
    private record ValidatedPeerInfo(Peer peer, SyncPeerStatus peerStatus, ValidatedHeader validatedHeader) { }
}
