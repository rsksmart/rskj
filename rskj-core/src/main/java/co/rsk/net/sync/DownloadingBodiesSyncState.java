package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockCompositeRule;
import co.rsk.validators.BlockRootValidationRule;
import co.rsk.validators.BlockUnclesHashValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.util.ByteUtil;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class DownloadingBodiesSyncState  extends BaseSyncState {

    // validation rules for bodies
    private final BlockCompositeRule blockValidationRules;

    // responses on wait
    private final Map<Long, PendingBodyResponse> pendingBodyResponses;

    // messages on wait from a peer
    private final Map<NodeID, Long> messagesByPeers;
    // chunks currently being downloaded
    private final Map<NodeID, Integer> chunksBeingDownloaded;
    // segments currently being downloaded (many nodes can be downloading same segment)
    private final Map<NodeID, Integer> segmentsBeingDownloaded;

    // headers waiting to be completed by bodies divided by chunks
    private final List<Deque<BlockHeader>> pendingHeaders;

    // a skeleton from each suitable peer
    private final Map<NodeID, List<BlockIdentifier>> skeletons;

    // segment a peer belongs to
    private final Map<NodeID, Integer> segmentByNode;

    // time elapse registered for each active peer
    private final Map<NodeID, Duration> timeElapsedByPeer;

    // chunks divided by segments
    private final List<Deque<Integer>> chunksBySegment;

    // peers that can be used to download blocks
    private final List<NodeID> suitablePeers;
    // maximum time waiting for a peer to answer
    private final Duration limit;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration,
                                      SyncEventsHandler syncEventsHandler,
                                      SyncInformation syncInformation,
                                      List<Deque<BlockHeader>> pendingHeaders,
                                      Map<NodeID, List<BlockIdentifier>> skeletons) {

        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.limit = syncConfiguration.getTimeoutWaitingRequest();
        this.blockValidationRules = new BlockCompositeRule(
                new BlockUnclesHashValidationRule(),
                new BlockRootValidationRule()
        );
        this.pendingBodyResponses = new HashMap<>();
        this.pendingHeaders = pendingHeaders;
        this.skeletons = skeletons;
        this.segmentByNode = new HashMap<>();
        this.chunksBySegment = new ArrayList<>();
        this.chunksBeingDownloaded = new HashMap<>();
        this.segmentsBeingDownloaded = new HashMap<>();
        this.timeElapsedByPeer = new HashMap<>();
        this.messagesByPeers = new HashMap<>();

        initializeSegments();
        this.suitablePeers = new ArrayList<>(segmentByNode.keySet());
    }

    @Override
    public void newBody(BodyResponseMessage message, MessageChannel peer) {
        NodeID peerId = peer.getPeerNodeID();
        if (!isExpectedBody(message.getId(), peerId)) {
            handleUnexpectedBody(peerId);
            return;
        }

        // we already checked that this message was expected
        BlockHeader header = pendingBodyResponses.remove(message.getId()).header;
        Block block = Block.fromValidData(header, message.getTransactions(), message.getUncles());
        if (!blockValidationRules.isValid(block)) {
            handleInvalidMessage(peerId, header);
            return;
        }

        // handle block
        if (syncInformation.processBlock(block, peer).isInvalidBlock()){
            handleInvalidBlock(peerId, header);
            return;
        }
        // updates peer downloading information
        tryRequestNextBody(peerId);
        // check if this was the last block to download
        verifyDownloadIsFinished();
    }

    private void verifyDownloadIsFinished() {
        // all headers have been requested and there is not any chunk still in process
        if (chunksBeingDownloaded.isEmpty() &&
                pendingHeaders.stream().allMatch(Collection::isEmpty)) {
            // Finished syncing
            syncEventsHandler.onCompletedSyncing();
        }
    }

    private void tryRequestNextBody(NodeID peerId) {
        updateHeadersAndChunks(peerId, chunksBeingDownloaded.get(peerId))
                .ifPresent(blockHeader -> tryRequestBody(peerId, blockHeader));
    }

    private void handleInvalidBlock(NodeID peerId, BlockHeader header) {
        syncInformation.reportEvent(
                "Invalid block received from node {} {} {}",
                EventType.INVALID_BLOCK, peerId,
                peerId, header.getNumber(), header.getShortHash());

        clearPeerInfo(peerId);
        if (suitablePeers.isEmpty()){
            syncEventsHandler.stopSyncing();
            return;
        }
        messagesByPeers.remove(peerId);
        resetChunkAndHeader(peerId, header);
        startDownloading(getInactivePeers());
    }

    private void handleInvalidMessage(NodeID peerId, BlockHeader header) {
        syncInformation.reportEvent(
                "Invalid body received from node {} {} {}",
                EventType.INVALID_MESSAGE, peerId,
                peerId, header.getNumber(), header.getShortHash());

        clearPeerInfo(peerId);
        if (suitablePeers.isEmpty()){
            syncEventsHandler.stopSyncing();
            return;
        }
        messagesByPeers.remove(peerId);
        resetChunkAndHeader(peerId, header);
        startDownloading(getInactivePeers());
    }

    private void handleUnexpectedBody(NodeID peerId) {
        syncInformation.reportEvent(
                "Unexpected body received from node {}",
                EventType.UNEXPECTED_MESSAGE, peerId, peerId);

        clearPeerInfo(peerId);
        if (suitablePeers.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }
        // if this peer has another different message pending then its restored to the stack
        Long messageId = messagesByPeers.remove(peerId);
        if (messageId != null) {
            resetChunkAndHeader(peerId, pendingBodyResponses.remove(messageId).header);
        }
        startDownloading(getInactivePeers());
    }

    private void resetChunkAndHeader(NodeID peerId, BlockHeader header) {
        int chunkNumber = chunksBeingDownloaded.remove(peerId);
        pendingHeaders.get(chunkNumber).addLast(header);
        int segmentNumber = segmentsBeingDownloaded.remove(peerId);
        chunksBySegment.get(segmentNumber).push(chunkNumber);
    }

    private void clearPeerInfo(NodeID peerId) {
        suitablePeers.remove(peerId);
        timeElapsedByPeer.remove(peerId);
    }

    private Optional<BlockHeader> updateHeadersAndChunks(NodeID peerId, Integer currentChunk) {
        Deque<BlockHeader> headers = pendingHeaders.get(currentChunk);
        BlockHeader header = headers.poll();
        while (header != null) {
            // we double check if the header was not downloaded or obtained by another way
            if (!syncInformation.isKnownBlock(header.getHash().getBytes())) {
                return Optional.of(header);
            }
            header = headers.poll();
        }

        Optional<BlockHeader> blockHeader = tryFindBlockHeader(peerId);
        if (!blockHeader.isPresent()){
            chunksBeingDownloaded.remove(peerId);
            segmentsBeingDownloaded.remove(peerId);
            messagesByPeers.remove(peerId);
        }

        return blockHeader;
    }

    private Optional<BlockHeader> tryFindBlockHeader(NodeID peerId) {
        // we start from the last chunk that can be downloaded
        for (int segmentNumber = segmentByNode.get(peerId); segmentNumber >= 0; segmentNumber--){
            Deque<Integer> chunks = chunksBySegment.get(segmentNumber);
            // if the segment stack is empty then continue to next segment
            if (!chunks.isEmpty()) {
                int chunkNumber = chunks.pollLast();
                Deque<BlockHeader> headers = pendingHeaders.get(chunkNumber);
                BlockHeader header = headers.poll();
                while (header != null) {
                    // we double check if the header was not downloaded or obtained by another way
                    if (!syncInformation.isKnownBlock(header.getHash().getBytes())) {
                        chunksBeingDownloaded.put(peerId, chunkNumber);
                        segmentsBeingDownloaded.put(peerId, segmentNumber);
                        return Optional.of(header);
                    }
                    header = headers.poll();
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void onEnter() {
        startDownloading(suitablePeers);
    }

    private void startDownloading(List<NodeID> peers) {
        peers.forEach(p -> tryFindBlockHeader(p).ifPresent(header -> tryRequestBody(p, header)));
    }

    @Override
    public void tick(Duration duration) {
        // first we update all the nodes that are expected to be working
        List<NodeID> updatedNodes = timeElapsedByPeer.keySet().stream()
            .filter(chunksBeingDownloaded::containsKey)
            .collect(Collectors.toList());

        updatedNodes.forEach(k -> timeElapsedByPeer.put(k, timeElapsedByPeer.get(k).plus(duration)));

        // we get the nodes that got beyond timeout limit and remove them
        updatedNodes.stream()
            .filter(k -> timeElapsedByPeer.get(k).compareTo(limit) >= 0)
            .forEach(this::handleTimeoutMessage);

        if (suitablePeers.isEmpty()){
            syncEventsHandler.stopSyncing();
            return;
        }

        startDownloading(getInactivePeers());

        if (chunksBeingDownloaded.isEmpty()){
            syncEventsHandler.stopSyncing();
        }
    }

    private void handleTimeoutMessage(NodeID peerId) {
        syncInformation.reportEvent("Timeout waiting body from node {}",
                EventType.TIMEOUT_MESSAGE, peerId, peerId);
        Long messageId = messagesByPeers.remove(peerId);
        BlockHeader header = pendingBodyResponses.remove(messageId).header;
        clearPeerInfo(peerId);
        resetChunkAndHeader(peerId, header);
    }

    private List<NodeID> getInactivePeers() {
        return suitablePeers.stream()
                .filter(p -> !chunksBeingDownloaded.containsKey(p))
                .collect(Collectors.toList());
    }

    /**
     * This method finds with the skeletons from each node, the segments we can divide chunks.
     * Each chunk belongs to a single segment, and each node associated to a segment can answer
     * for each block inside the chunks belonging to a segment.
     * Also each node on a superior segment can answer for every block on lower segments.
     * The idea is to find the "min common chunks" between nodes to find when a new segment starts
     */
    private void initializeSegments() {
        Deque<Integer> segmentChunks = new ArrayDeque<>();
        Integer segmentNumber = 0;
        Integer chunkNumber = 0;
        List<NodeID> nodes = getAvailableNodesIDSFor(chunkNumber);
        List<NodeID> prevNodes = nodes;
        segmentChunks.push(chunkNumber);
        chunkNumber++;

        for (; chunkNumber < pendingHeaders.size(); chunkNumber++){
            nodes = getAvailableNodesIDSFor(chunkNumber);
            if (prevNodes.size() != nodes.size()){
                final List<NodeID> filteringNodes = nodes;
                List<NodeID> insertedNodes = prevNodes.stream()
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

    private List<NodeID> getAvailableNodesIDSFor(Integer chunkNumber) {
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

    private void insertSegment(Deque<Integer> segmentChunks, List<NodeID> nodes, Integer segmentNumber) {
        chunksBySegment.add(segmentChunks);
        nodes.forEach(nodeID -> segmentByNode.put(nodeID, segmentNumber));
    }

    private void tryRequestBody(NodeID peerId, BlockHeader header){
        Long messageId = syncEventsHandler.sendBodyRequest(header, peerId);
        if (messageId != null){
            pendingBodyResponses.put(messageId, new PendingBodyResponse(peerId, header));
            timeElapsedByPeer.put(peerId, Duration.ZERO);
            messagesByPeers.put(peerId, messageId);
        } else {
            // since a message could fail to be delivered we have to discard peer if can't be reached
            clearPeerInfo(peerId);
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), peerId);
        }
    }

    private boolean isExpectedBody(long requestId, NodeID peerId) {
        PendingBodyResponse expected = pendingBodyResponses.get(requestId);
        return expected != null && expected.nodeID.equals(peerId);
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    private static class PendingBodyResponse {
        private NodeID nodeID;
        private BlockHeader header;

        PendingBodyResponse(NodeID nodeID, BlockHeader header) {
            this.nodeID = nodeID;
            this.header = header;
        }
    }
}
