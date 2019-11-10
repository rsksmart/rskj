package co.rsk.net.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockSyncService;
import co.rsk.net.Peer;
import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockCompositeRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class DownloadingBodiesSyncState  extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("syncprocessor");

    private final PeersInformation peersInformation;
    private final Blockchain blockchain;
    private final BlockFactory blockFactory;

    // responses on wait
    private final Map<Long, PendingBodyResponse> pendingBodyResponses;

    // messages on wait from a peer
    private final Map<Peer, Long> messagesByPeers;
    // chunks currently being downloaded
    private final Map<Peer, Integer> chunksBeingDownloaded;
    // segments currently being downloaded (many nodes can be downloading same segment)
    private final Map<Peer, Integer> segmentsBeingDownloaded;

    // headers waiting to be completed by bodies divided by chunks
    private final List<Deque<BlockHeader>> pendingHeaders;

    // a skeleton from each suitable peer
    private final Map<Peer, List<BlockIdentifier>> skeletons;

    // segment a peer belongs to
    private final Map<Peer, Integer> segmentByNode;

    // time elapse registered for each active peer
    private final Map<Peer, Duration> timeElapsedByPeer;

    // chunks divided by segments
    private final List<Deque<Integer>> chunksBySegment;

    // peers that can be used to download blocks
    private final List<Peer> suitablePeers;
    // maximum time waiting for a peer to answer
    private final Duration limit;
    private final BlockCompositeRule blockValidationRule;
    private final BlockSyncService blockSyncService;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration,
                                      SyncEventsHandler syncEventsHandler,
                                      PeersInformation peersInformation,
                                      Blockchain blockchain,
                                      BlockFactory blockFactory,
                                      BlockSyncService blockSyncService,
                                      BlockCompositeRule blockValidationRule,
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
        this.chunksBeingDownloaded = new HashMap<>();
        this.segmentsBeingDownloaded = new HashMap<>();
        this.timeElapsedByPeer = new HashMap<>();
        this.messagesByPeers = new HashMap<>();

        initializeSegments();
        this.suitablePeers = new ArrayList<>(segmentByNode.keySet());
    }

    @Override
    public void newBody(BodyResponseMessage message, Peer peer) {
        NodeID peerId = peer.getPeerNodeID();
        long requestId = message.getId();
        if (!isExpectedBody(requestId, peerId)) {
            handleUnexpectedBody(peer);
            return;
        }

        List<BlockHeader> headers = pendingBodyResponses.remove(requestId).headers;

        for (int i = 0 ; i < message.getBlocks().size() ; i++) {
            BlockBody receivedBlock = message.getBlocks().get(i);
            if (receivedBlock == null) {
                continue;
            }
            BlockHeader header = headers.get(i);
            Block block;
            try {
                block = blockFactory.newBlock(header, receivedBlock.getTransactionsList(), receivedBlock.getUncleList());
                block.seal();
            } catch (IllegalArgumentException ex) {
                handleInvalidMessage(peer, header);
                return;
            }

            if (!blockValidationRule.isValid(block)) {
                handleInvalidMessage(peer, header);
                return;
            }

            // handle block
            // this is a controled place where we ask for blocks, we never should look for missing hashes
            if (blockSyncService.processBlock(block, peer, true).isInvalidBlock()) {
                handleInvalidBlock(peer, header);
            }
        }
        // updates peer downloading information
        tryRequestNextBody(peer);
        // check if this was the last block to download
        verifyDownloadIsFinished();
    }

    private void verifyDownloadIsFinished() {
        // all headers have been requested and there is not any chunk still in process
        if (chunksBeingDownloaded.isEmpty() &&
                pendingHeaders.stream().allMatch(Collection::isEmpty)) {
            // Finished syncing
            logger.info("Completed syncing phase");
            syncEventsHandler.stopSyncing();

        }
    }


    private void tryRequestNextBody(Peer peer) {
        List<BlockHeader> headers = updateHeadersAndChunks(peer);
        tryRequestBodies(peer, headers);
    }

    private void handleInvalidBlock(Peer peer, BlockHeader header) {
        peersInformation.reportEventWithLog(
                "Invalid block received from node {} {} {}",
                peer.getPeerNodeID(), EventType.INVALID_BLOCK,
                peer.getPeerNodeID(), header.getNumber(), header.getShortHash());

        clearPeerInfo(peer);
        if (suitablePeers.isEmpty()){
            syncEventsHandler.stopSyncing();
            return;
        }
        messagesByPeers.remove(peer);
        resetChunkAndHeader(peer, header);
        startDownloading(getInactivePeers());
    }

    private void handleInvalidMessage(Peer peer, BlockHeader header) {
        peersInformation.reportEventWithLog(
                "Invalid body received from node {} {} {}",
                peer.getPeerNodeID(), EventType.INVALID_MESSAGE,
                peer.getPeerNodeID(), header.getNumber(), header.getShortHash());

        clearPeerInfo(peer);
        if (suitablePeers.isEmpty()){
            syncEventsHandler.stopSyncing();
            return;
        }
        messagesByPeers.remove(peer);
        resetChunkAndHeader(peer, header);
        startDownloading(getInactivePeers());
    }

    private void handleUnexpectedBody(Peer peer) {
        peersInformation.reportEventWithLog(
                "Unexpected body received from node {}",
                peer.getPeerNodeID(), EventType.UNEXPECTED_MESSAGE, peer.getPeerNodeID());

        clearPeerInfo(peer);
        if (suitablePeers.isEmpty()) {
            syncEventsHandler.stopSyncing();
            return;
        }
        // if this peer has another different message pending then its restored to the stack
        Long messageId = messagesByPeers.remove(peer);
        if (messageId != null) {
            for (BlockHeader header : pendingBodyResponses.remove(messageId).headers) {
                resetChunkAndHeader(peer, header);
            }
        }
        startDownloading(getInactivePeers());
    }

    private void resetChunkAndHeader(Peer peer, BlockHeader header) {
        int chunkNumber = chunksBeingDownloaded.remove(peer);
        pendingHeaders.get(chunkNumber).addLast(header);
        int segmentNumber = segmentsBeingDownloaded.remove(peer);
        chunksBySegment.get(segmentNumber).push(chunkNumber);
    }

    private void clearPeerInfo(Peer peer) {
        suitablePeers.remove(peer);
        timeElapsedByPeer.remove(peer);
    }

    private List<BlockHeader> updateHeadersAndChunks(Peer peer) {
        List<BlockHeader> blockHeaders = tryFindBlockHeaders(peer);
        if (blockHeaders.isEmpty()) {
            chunksBeingDownloaded.remove(peer);
            segmentsBeingDownloaded.remove(peer);
            messagesByPeers.remove(peer);
        }

        return blockHeaders;
    }

    private boolean isKnownBlock(Keccak256 hash) {
        return blockchain.getBlockByHash(hash.getBytes()) != null;
    }

    private List<BlockHeader> tryFindBlockHeaders(Peer peer) {
        List<BlockHeader> found = new ArrayList<>();
        // we start from the last chunk that can be downloaded
        for (int segmentNumber = segmentByNode.get(peer); segmentNumber >= 0; segmentNumber--){
            Deque<Integer> chunks = chunksBySegment.get(segmentNumber);
            // if the segment stack is empty then continue to next segment
            if (!chunks.isEmpty()) {
                Deque<BlockHeader> headers = null;
                int chunkNumber = 0;
                while((headers == null || headers.isEmpty()) && !chunks.isEmpty()) {
                    chunkNumber = chunks.peekLast();
                    headers = pendingHeaders.get(chunkNumber);
                    if (headers == null || headers.isEmpty()) {
                        chunks.pollLast();
                    }
                }
                if (headers == null) {
                    continue;
                }

                BlockHeader header = headers.poll();
                while (header != null) {
                    // we double check if the header was not downloaded or obtained by another way
                    if (!isBlockKnown(header.getHash())) {
                        chunksBeingDownloaded.put(peer, chunkNumber);
                        segmentsBeingDownloaded.put(peer, segmentNumber);
                        found.add(header);
                        if (found.size() == syncConfiguration.getMaxMessageCount()) {
                            break;
                        }
                    }
                    header = headers.poll();
                }
            }
            if (!found.isEmpty()) {
                return found;
            }
        }
        return found;
    }

    private boolean isBlockKnown(Keccak256 hash) {
        return blockchain.getBlockByHash(hash.getBytes()) != null;
    }

    @Override
    public void onEnter() {
        startDownloading(suitablePeers);
    }

    private void startDownloading(List<Peer> peers) {
        peers.forEach(p -> tryRequestBodies(p, tryFindBlockHeaders(p)));
    }

    @Override
    public void tick(Duration duration) {
        // first we update all the nodes that are expected to be working
        List<Peer> updatedNodes = timeElapsedByPeer.keySet().stream()
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

    private void handleTimeoutMessage(Peer peer) {
        peersInformation.reportEventWithLog("Timeout waiting body from node {}",
        peer.getPeerNodeID(), EventType.TIMEOUT_MESSAGE, peer);
        Long messageId = messagesByPeers.remove(peer);
        List<BlockHeader> headers = pendingBodyResponses.remove(messageId).headers;
        clearPeerInfo(peer);
        for (BlockHeader header : headers) {
            resetChunkAndHeader(peer, header);
        }
    }

    private List<Peer> getInactivePeers() {
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
        List<Peer> nodes = getAvailableNodesIDSFor(chunkNumber);
        List<Peer> prevNodes = nodes;
        segmentChunks.push(chunkNumber);
        chunkNumber++;

        for (; chunkNumber < pendingHeaders.size(); chunkNumber++){
            nodes = getAvailableNodesIDSFor(chunkNumber);
            if (prevNodes.size() != nodes.size()){
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
        nodes.forEach(peer -> segmentByNode.put(peer, segmentNumber));
    }

    private void tryRequestBodies(Peer peer, List<BlockHeader> headers){
        if (headers.isEmpty()) {
            return;
        }
        long messageId = syncEventsHandler.sendBodyRequest(peer, headers);
        pendingBodyResponses.put(messageId, new PendingBodyResponse(peer.getPeerNodeID(), headers));
        timeElapsedByPeer.put(peer, Duration.ZERO);
        messagesByPeers.put(peer, messageId);
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
    public void expectBodyResponseFor(long requestId, NodeID nodeID, List<BlockHeader> headers) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, headers));
    }

    private static class PendingBodyResponse {
        private NodeID nodeID;
        private List<BlockHeader> headers;

        PendingBodyResponse(NodeID nodeID, List<BlockHeader> headers) {
            this.nodeID = nodeID;
            this.headers = headers;
        }
    }
}
