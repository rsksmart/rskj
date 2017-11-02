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
    private Map<NodeID, Long> pendingMessages;

    // headers waiting to be completed by bodies divided by chunks
    private List<Stack<BlockHeader>> pendingHeaders;

    // a skeleton from each suitable peer
    private Map<NodeID, List<BlockIdentifier>> skeletons;

    // segment a peer belongs
    private HashMap<NodeID, Integer> segmentByNode;

    // chunks currently being downloaded
    private Map<NodeID, Integer> chunksBeingDownloaded;

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
        this.timeElapsedByPeer = new HashMap<>();
        this.pendingMessages = new HashMap<>();
    }

    @Override
    public void newBody(BodyResponseMessage message, MessageChannel peer) {
        NodeID peerId = peer.getPeerNodeID();
        if (!isExpectedBody(message.getId(), peerId)) {
            syncInformation.reportEvent(
                    "Unexpected body received from node {}",
                    EventType.UNEXPECTED_MESSAGE, peerId);

            suitablePeers.remove(peerId);
            if (suitablePeers.isEmpty()){
                syncEventsHandler.stopSyncing();
            }
            return;
        }

        // we know it exists because it was called from a SyncEvent
        BlockHeader header = pendingBodyResponses.get(message.getId()).header;
        Block block = Block.fromValidData(header, message.getTransactions(), message.getUncles());
        if (!blockUnclesHashValidationRule.isValid(block) || !blockTransactionsValidationRule.isValid(block)) {
            syncInformation.reportEvent(
                    "Invalid body received from node {}",
                    EventType.INVALID_MESSAGE, peerId);

            suitablePeers.remove(peerId);
            if (suitablePeers.isEmpty()){
                syncEventsHandler.stopSyncing();
            }
            return;
        }

        pendingBodyResponses.remove(message.getId());
        pendingMessages.remove(peerId);
        syncInformation.processBlock(block);

        Optional<BlockHeader> nextHeader = updateHeadersAndChunks(peerId);
        if (nextHeader.isPresent()){
            requestBody(peerId, nextHeader.get());
        }

        // all headers have been requested and there isnt any chunk still in process
        if (pendingHeaders.stream().allMatch(stack -> stack.empty()) &&
                chunksBeingDownloaded.size() == 0) {
            // Finished syncing
            syncEventsHandler.onCompletedSyncing();
        }
    }

    private Optional<BlockHeader> updateHeadersAndChunks(NodeID peerId) {
        Integer chunkNumber = chunksBeingDownloaded.get(peerId);
        Stack<BlockHeader> headers = pendingHeaders.get(chunkNumber);
        if (!headers.empty()) {
            return Optional.of(headers.pop());
        }

        Integer segmentNumber = segmentByNode.get(peerId);
        // we start from the last chunk that can be downloaded
        while (segmentNumber >= 0){
            Stack<Integer> chunks = chunksBySegment.get(segmentNumber);
            // if the segment stack is empty then continue to next segment
            if (!chunks.empty()) {
                chunkNumber = chunks.pop();
                headers = pendingHeaders.get(chunkNumber);
                if (!headers.empty()) {
                    chunksBeingDownloaded.replace(peerId, chunkNumber);
                    return Optional.of(headers.pop());
                } else {
                    // log something we are in trouble
                }
            }
            segmentNumber--;
        }

        chunksBeingDownloaded.remove(peerId);
        return Optional.empty();
    }

    @Override
    public void onEnter() {
        initializeSegments();
        startDownloading(suitablePeers);
    }

    private void startDownloading(List<NodeID> peers) {
        Duration start = Duration.ZERO;
        for (int i = 0; i < chunksBySegment.size() && i < suitablePeers.size(); i++){
            NodeID peerId = peers.get(i);
            BlockHeader header = getStartingHeader(start, peerId);
            requestBody(peerId, header);
        }
    }

    private BlockHeader getStartingHeader(Duration start, NodeID peerId) {
        Integer segmentNumber = segmentByNode.get(peerId);
        Integer chunkNumber = chunksBySegment.get(segmentNumber).pop();
        chunksBeingDownloaded.put(peerId, chunkNumber);
        timeElapsedByPeer.put(peerId, start);
        return pendingHeaders.get(chunkNumber).pop();
    }

    @Override
    public void tick(Duration duration) {
        List<NodeID> timeoutedNodes = timeElapsedByPeer.entrySet().stream()
                .filter(e -> e.getValue().plus(duration).compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (NodeID peerId: timeoutedNodes){
            syncInformation.reportEvent("Timeout waiting requests from node {}",
                    EventType.TIMEOUT_MESSAGE, peerId);
            timeElapsedByPeer.remove(peerId);
            suitablePeers.remove(peerId);
            Long messageId = pendingMessages.remove(peerId);
            pendingBodyResponses.remove(messageId);
        }

        if (suitablePeers.size() == 0){
            syncEventsHandler.stopSyncing();
        }

        List<NodeID> inactivePeers = suitablePeers.stream()
                .filter(p -> chunksBeingDownloaded.containsKey(p))
                .collect(Collectors.toList());

        startDownloading(inactivePeers);

        if (chunksBeingDownloaded.size() == 0){
            syncEventsHandler.stopSyncing();
        }
    }

    private void initializeSegments() {
        Stack<Integer> segmentChunks = new Stack<>();
        Integer segmentNumber = 0;
        int currentSize = skeletons.size();

        for (Integer chunkNumber = 0; chunkNumber < pendingHeaders.size(); chunkNumber++){
            final Integer currentChunk = chunkNumber;
            final Integer currentSegment = segmentNumber;
            segmentChunks.push(chunkNumber);
            List<NodeID> nodes = skeletons.entrySet().stream()
                    .filter(e -> ByteUtil.fastEquals(
                            // the hash of the start of next chunk
                            e.getValue().get(currentChunk + 1).getHash(),
                            // the first header of chunk
                            pendingHeaders.get(currentChunk).get(0).getHash()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (currentSize != nodes.size() || chunkNumber == pendingHeaders.size() - 1){
                Collections.reverse(segmentChunks);
                insertSegment(segmentChunks, nodes, chunkNumber, currentSegment);
                segmentChunks = new Stack<>();
                segmentNumber++;
                currentSize = nodes.size();
            }
        }
    }

    private void insertSegment(Stack<Integer> segmentChunks, List<NodeID> nodes, Integer chunkNumber, Integer segmentIndex) {
        chunksBySegment.add(segmentChunks);
        nodes.stream().forEach(nodeID -> segmentByNode.put(nodeID, segmentIndex));
    }

    private void requestBody(NodeID peerId, BlockHeader header){
        long messageId = syncEventsHandler.sendBodyRequest(header);
        pendingBodyResponses.put(messageId, new PendingBodyResponse(peerId, header));
        pendingMessages.put(peerId, messageId);
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
