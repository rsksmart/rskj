package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockRootValidationRule;
import co.rsk.validators.BlockUnclesHashValidationRule;
import co.rsk.validators.BlockValidationRule;
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
    private BlockValidationRule blockUnclesHashValidationRule = new BlockUnclesHashValidationRule();
    private BlockValidationRule blockTransactionsValidationRule = new BlockRootValidationRule();

    // responses on wait
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    // messages on wait from a peer
    private Map<NodeID, Long> messagesByPeers;
    // chunks currently being downloaded
    private Map<NodeID, Integer> chunksBeingDownloaded;
    // segments currently being downloaded (many nodes can be downloading same segment)
    private Map<NodeID, Integer> segmentsBeingDownloaded;

    // headers waiting to be completed by bodies divided by chunks
    private List<Stack<BlockHeader>> pendingHeaders;

    // a skeleton from each suitable peer
    private Map<NodeID, List<BlockIdentifier>> skeletons;

    // segment a peer belongs
    private HashMap<NodeID, Integer> segmentByNode;


    // time elapse registered for each active peer
    private Map<NodeID, Duration> timeElapsedByPeer;

    // chunks divided by segments
    private List<Stack<Integer>> chunksBySegment;

    // peers that can be used to download blocks
    private List<NodeID> suitablePeers;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, List<Stack<BlockHeader>> pendingHeaders, Map<NodeID, List<BlockIdentifier>> skeletons) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.pendingHeaders = pendingHeaders;
        this.skeletons = skeletons;
        this.segmentByNode = new HashMap<>();
        this.suitablePeers = new ArrayList<>(skeletons.keySet());
        this.chunksBySegment = new ArrayList<>();
        this.chunksBeingDownloaded = new HashMap<>();
        this.segmentsBeingDownloaded = new HashMap<>();
        this.timeElapsedByPeer = new HashMap<>();
        this.messagesByPeers = new HashMap<>();
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
        if (!blockUnclesHashValidationRule.isValid(block) || !blockTransactionsValidationRule.isValid(block)) {
            handleInvalidMessage(peerId, header);
            return;
        }

        // handle block
        syncInformation.processBlock(block);
        // updates peer downloading information
        tryRequestNextBody(peerId);
        // check if this was the last block to download
        verifyDownloadIsFinished();
    }

    private void verifyDownloadIsFinished() {
        // all headers have been requested and there is not any chunk still in process
        if (chunksBeingDownloaded.size() == 0 &&
                pendingHeaders.stream().allMatch(stack -> stack.empty())) {
            // Finished syncing
            syncEventsHandler.onCompletedSyncing();
        }
    }

    private void tryRequestNextBody(NodeID peerId) {
        Optional<BlockHeader> nextHeader = updateHeadersAndChunks(peerId, chunksBeingDownloaded.get(peerId));
        if (nextHeader.isPresent()){
            requestBody(peerId, nextHeader.get());
        }
    }

    private void handleInvalidMessage(NodeID peerId, BlockHeader header) {
        syncInformation.reportEvent(
                "Invalid body received from node {}",
                EventType.INVALID_MESSAGE, peerId);

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
                EventType.UNEXPECTED_MESSAGE, peerId);

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
        Integer chunkNumber = chunksBeingDownloaded.remove(peerId);
        pendingHeaders.get(chunkNumber).push(header);
        Integer segmentNumber = segmentsBeingDownloaded.remove(peerId);
        chunksBySegment.get(segmentNumber).push(chunkNumber);
    }

    private void clearPeerInfo(NodeID peerId) {
        suitablePeers.remove(peerId);
        timeElapsedByPeer.remove(peerId);
    }

    private Optional<BlockHeader> updateHeadersAndChunks(NodeID peerId, Integer currentChunk) {
        Stack<BlockHeader> headers = pendingHeaders.get(currentChunk);
        if (!headers.empty()) {
            return Optional.of(headers.pop());
        }

        Optional<BlockHeader> header = tryFindBlockHeader(peerId);
        if (!header.isPresent()){
            chunksBeingDownloaded.remove(peerId);
            segmentsBeingDownloaded.remove(peerId);
            messagesByPeers.remove(peerId);
        }

        return header;
    }

    private Optional<BlockHeader> tryFindBlockHeader(NodeID peerId) {
        Integer segmentNumber = segmentByNode.get(peerId);
        // we start from the last chunk that can be downloaded
        while (segmentNumber >= 0){
            Stack<Integer> chunks = chunksBySegment.get(segmentNumber);
            // if the segment stack is empty then continue to next segment
            if (!chunks.empty()) {
                Integer chunkNumber = chunks.pop();
                Stack<BlockHeader> headers = pendingHeaders.get(chunkNumber);
                if (!headers.empty()) {
                    chunksBeingDownloaded.put(peerId, chunkNumber);
                    segmentsBeingDownloaded.put(peerId, segmentNumber);
                    return Optional.of(headers.pop());
                } else {
                    // log something we are in trouble
                }
            }
            segmentNumber--;
        }
        return Optional.empty();
    }

    @Override
    public void onEnter() {
        initializeSegments();
        startDownloading(suitablePeers);
    }

    private void startDownloading(List<NodeID> peers) {
        for (int i = 0; i < peers.size(); i++){
            NodeID peerId = peers.get(i);
            Optional<BlockHeader> nextHeader = tryFindBlockHeader(peerId);
            if (nextHeader.isPresent()){
                requestBody(peerId, nextHeader.get());
            }
        }
    }

    @Override
    public void tick(Duration duration) {
        List<NodeID> timeoutedNodes = timeElapsedByPeer.entrySet().stream()
                .filter(e -> chunksBeingDownloaded.containsKey(e.getKey()) &&
                        e.getValue().plus(duration).compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (NodeID peerId: timeoutedNodes){
            syncInformation.reportEvent("Timeout waiting requests from node {}",
                    EventType.TIMEOUT_MESSAGE, peerId);

            Long messageId = messagesByPeers.remove(peerId);
            BlockHeader header = pendingBodyResponses.remove(messageId).header;
            clearPeerInfo(peerId);
            resetChunkAndHeader(peerId, header);
        }

        if (suitablePeers.size() == 0){
            syncEventsHandler.stopSyncing();
            return;
        }

        startDownloading(getInactivePeers());

        if (chunksBeingDownloaded.size() == 0){
            syncEventsHandler.stopSyncing();
        }
    }

    private List<NodeID> getInactivePeers() {
        return suitablePeers.stream()
                .filter(p -> !chunksBeingDownloaded.containsKey(p))
                .collect(Collectors.toList());
    }

    private void initializeSegments() {
        Stack<Integer> segmentChunks = new Stack<>();
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
                List<NodeID> insertedNodes = prevNodes.stream().filter(k -> !filteringNodes.contains(k)).collect(Collectors.toList());
                Collections.reverse(segmentChunks);
                insertSegment(segmentChunks, insertedNodes, segmentNumber);
                segmentNumber++;
                prevNodes = nodes;
                segmentChunks = new Stack<>();
            }
            segmentChunks.push(chunkNumber);
        }

        // last segment should be added always
        Collections.reverse(segmentChunks);
        insertSegment(segmentChunks, nodes, segmentNumber);
    }

    private List<NodeID> getAvailableNodesIDSFor(Integer chunkNumber) {
        return skeletons.entrySet().stream()
                .filter(e -> e.getValue().size() > chunkNumber + 1 && ByteUtil.fastEquals(
                    // the hash of the start of next chunk
                    e.getValue().get(chunkNumber + 1).getHash(),
                    // the first header of chunk
                    pendingHeaders.get(chunkNumber).get(0).getHash()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void insertSegment(Stack<Integer> segmentChunks, List<NodeID> nodes, Integer segmentNumber) {
        chunksBySegment.add(segmentChunks);
        nodes.stream().forEach(nodeID -> segmentByNode.put(nodeID, segmentNumber));
    }

    private void requestBody(NodeID peerId, BlockHeader header){
        long messageId = syncEventsHandler.sendBodyRequest(header, peerId);
        pendingBodyResponses.put(messageId, new PendingBodyResponse(peerId, header));
        timeElapsedByPeer.put(peerId, Duration.ZERO);
        messagesByPeers.put(peerId, messageId);
    }

    public boolean isExpectedBody(long requestId, NodeID peerId) {
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
