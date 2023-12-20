package co.rsk.net;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Lists;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Snapshot Synchronization consist in 3 steps:
 * 1. Status: exchange message with the server, to know which block we are going to sync and whats the size of the Unitrie of that block.
 * it also exchanges previous blocks (4k) and the block of the snapshot, which has a root hash of the state.
 * 2. State chunks: share the state in chunks of N nodes. Each chunk is independently verifiable.
 * 3. Rebuild the state: Rebuild the state in the client side, save it to the db and save also the blocks corresponding to the snapshot.
 * <p>
 * After this process, the node should be able to start the long sync to the tip and then the backward sync to the genesis.
 */
public class SnapshotProcessor {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");
    public static final int BLOCK_NUMBER_CHECKPOINT = 5000;
    public static final int BLOCK_CHUNK_SIZE = 400;
    public static final int BLOCKS_REQUIRED = 6000;
    private static final long DELAY_BTW_RUNS = 5 * 60 * 1000; // 20 minutes
    private static final int CHUNK_MAX = 400;
    private static final int CHUNK_MIN = 25;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private int chunkSize;
    private final PeersInformation peersInformation;
    private final TransactionPool transactionPool;
    private long messageId = 0;
    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;
    private SnapSyncState snapSyncState;
    private List<TrieDTO> allNodes;

    private long remoteTrieSize;
    private byte[] remoteRootHash;
    private List<Block> blocks;
    private List<BlockDifficulty> difficulties;
    private Block lastBlock;
    private BlockDifficulty lastBlockDifficulty;

    private final ConcurrentLinkedQueue<ChunkTask> chunkTasks = new ConcurrentLinkedQueue<>();
    private PeersInformation peers;

    // flag for parallel requests
    private final boolean parallel;

    // priority queue for ordering chunk responses
    private final PriorityQueue<SnapStateChunkResponseMessage> responseQueue = new PriorityQueue<>(
            Comparator.comparingLong(SnapStateChunkResponseMessage::getFrom)
    );

    private long nextExpectedFrom = 0L;

    private boolean syncing;

    public SnapshotProcessor(Blockchain blockchain,
                             TrieStore trieStore,
                             PeersInformation peersInformation,
                             BlockStore blockStore,
                             TransactionPool transactionPool,
                             int chunkSize,
                             boolean isParallelEnabled) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peersInformation = peersInformation;
        this.chunkSize = CHUNK_MIN; //TODO (pato): chunkSize;
        this.allNodes = Lists.newArrayList();
        this.blockStore = blockStore;
        this.blocks = Lists.newArrayList();
        this.difficulties = Lists.newArrayList();
        this.transactionPool = transactionPool;
        this.parallel = isParallelEnabled;
        this.syncing = false;
    }

    public void startSyncing(PeersInformation peers, SnapSyncState snapSyncState) {
        // TODO(snap-poc) temporary hack, code in this should be moved to SnapSyncState probably
        if (this.syncing) {
            return;
        }
        this.syncing = true;
        this.snapSyncState = snapSyncState;
        this.peers = peers;
        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;
        // get more than one peer, use the peer queue
        // TODO(snap-poc) deal with multiple peers algorithm here
        Peer peer = peers.getBestPeerCandidates().get(0);
        logger.info("CLIENT - Starting Snapshot sync.");
        requestSnapStatus(peer);
    }

    // TODO(snap-poc) should be called on errors too
    private void stopSyncing() {
        this.syncing = false;
        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;
        this.snapSyncState.finished();
    }

    /**
     * STATUS
     */
    private void requestSnapStatus(Peer peer) {
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        peer.sendMessage(message);
    }

    public void processSnapStatusRequest(Peer sender) {
        logger.debug("SERVER - Processing snapshot status request.");
//        long bestBlockNumber = blockchain.getBestBlock().getNumber();
//        long checkpointBlockNumber = bestBlockNumber - (bestBlockNumber % BLOCK_NUMBER_CHECKPOINT);
        long bestBlockNumber = 5637110L;
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
//        for (long i = checkpointBlockNumber - BLOCK_CHUNK_SIZE; i < checkpointBlockNumber; i++) {
//            Block block = blockchain.getBlockByNumber(i);
//            blocks.add(block);
//            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
//        }
//
//        Block checkpointBlock = blockchain.getBlockByNumber(checkpointBlockNumber);
//        blocks.add(checkpointBlock);
//        difficulties.add(blockStore.getTotalDifficultyForHash(checkpointBlock.getHash().getBytes()));
        Block checkpointBlock = blockchain.getBlockByNumber(bestBlockNumber);
        byte[] rootHash = checkpointBlock.getStateRoot();
        Optional<TrieDTO> opt = trieStore.retrieveDTO(rootHash);

        long trieSize = 0;
        if (opt.isPresent()) {
            trieSize = opt.get().getTotalSize();
        } else {
            logger.debug("SERVER - trie is notPresent");
        }
        logger.debug("SERVER - processing snapshot status request - rootHash: {} trieSize: {}", rootHash, trieSize);
        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(blocks, difficulties, trieSize);
        sender.sendMessage(responseMessage);
    }

    public void processSnapStatusResponse(Peer sender, SnapStatusResponseMessage responseMessage) {
        List<Block> blocks = responseMessage.getBlocks();
        List<BlockDifficulty> difficulties = responseMessage.getDifficulties();
        this.lastBlock = blocks.get(blocks.size() - 1);
        this.lastBlockDifficulty = lastBlock.getCumulativeDifficulty();
        this.remoteRootHash = this.lastBlock.getStateRoot();
        this.remoteTrieSize = responseMessage.getTrieSize();
        this.blocks.addAll(blocks);
        this.difficulties.addAll(difficulties);
        logger.debug("CLIENT - Processing snapshot status response - blockNumber: {} rootHash: {} triesize: {}", lastBlock.getNumber(), remoteRootHash, remoteTrieSize);
        requestBlocksChunk(sender, blocks.get(0).getNumber());
        generateChunkRequestTasks();
        startRequestingChunks();
    }

    /**
     * BLOCK CHUNK
     */
    private void requestBlocksChunk(Peer sender, long blockNumber) {
        logger.debug("CLIENT - Requesting block chunk to node {} - block {}", sender.getPeerNodeID(), blockNumber);
        sender.sendMessage(new SnapBlocksRequestMessage(blockNumber));
    }

    public void processSnapBlocksRequest(Peer sender, SnapBlocksRequestMessage snapBlocksRequestMessage) {
        logger.debug("SERVER - Processing snap blocks request");
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        for (long i = snapBlocksRequestMessage.getBlockNumber() - BLOCK_CHUNK_SIZE; i < snapBlocksRequestMessage.getBlockNumber(); i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(blocks, difficulties);
        sender.sendMessage(responseMessage);
    }

    public void processSnapBlocksResponse(Peer sender, SnapBlocksResponseMessage snapBlocksResponseMessage) {
        logger.debug("CLIENT - Processing snap blocks response");
        List<Block> blocks = snapBlocksResponseMessage.getBlocks();
        List<BlockDifficulty> difficulties = snapBlocksResponseMessage.getDifficulties();
        this.blocks.addAll(blocks);
        this.difficulties.addAll(difficulties);
        long nextChunk = blocks.get(0).getNumber();
        if (nextChunk > this.lastBlock.getNumber() - BLOCKS_REQUIRED) {
            requestBlocksChunk(sender, nextChunk);
        } else {
            logger.info("CLIENT - Finished Snap blocks sync.");
        }
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(Peer peer, long from, long blockNumber, int chunkSize) {
        logger.debug("CLIENT - Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);
        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);
        peer.sendMessage(message);
        logger.debug("CLIENT - Request sent state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);
    }

    public void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage request) {
        new Thread(() -> {
            long startChunk = System.currentTimeMillis();
            logger.debug("SERVER - Processing state chunk request from node {}", sender.getPeerNodeID());
            List<byte[]> trieEncoded = new ArrayList<>();
            Block block = blockchain.getBlockByNumber(request.getBlockNumber());
            final long to = request.getFrom() + (request.getChunkSize() * 1024);
            TrieDTOInOrderIterator it = new TrieDTOInOrderIterator(trieStore, block.getStateRoot(), request.getFrom(), to);

            // First we add the root nodes on the left of the current node. They are used to validate the chunk.
            List<byte[]> preRootNodes = it.getPreRootNodes().stream().map((t) -> RLP.encodeList(RLP.encodeElement(t.getEncoded()), RLP.encodeElement(getBytes(t.getLeftHash())))).collect(Collectors.toList());
            byte[] preRootNodesBytes = !preRootNodes.isEmpty() ? RLP.encodeList(preRootNodes.toArray(new byte[0][0])) : RLP.encodedEmptyList();

            // Then we add the nodes corresponding to the chunk.
            TrieDTO first = it.peek();
            TrieDTO last = null;
            while (it.hasNext()) {
                TrieDTO e = it.next();
                if (it.hasNext() || it.isEmpty()) {
                    last = e;
                    trieEncoded.add(RLP.encodeElement(e.getEncoded()));
                }
            }
            byte[] firstNodeLeftHash = RLP.encodeElement(first.getLeftHash());
            byte[] nodesBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
            byte[] lastNodeHashes = last != null ? RLP.encodeList(RLP.encodeElement(getBytes(last.getLeftHash())), RLP.encodeElement(getBytes(last.getRightHash()))) : RLP.encodedEmptyList();

            // Last we add the root nodes on the right of the last visited node. They are used to validate the chunk.
            List<byte[]> postRootNodes = it.getNodesLeftVisiting().stream().map((t) -> RLP.encodeList(RLP.encodeElement(t.getEncoded()), RLP.encodeElement(getBytes(t.getRightHash())))).collect(Collectors.toList());
            byte[] postRootNodesBytes = !postRootNodes.isEmpty() ? RLP.encodeList(postRootNodes.toArray(new byte[0][0])) : RLP.encodedEmptyList();
            byte[] chunkBytes = RLP.encodeList(preRootNodesBytes, nodesBytes, firstNodeLeftHash, lastNodeHashes, postRootNodesBytes);

            SnapStateChunkResponseMessage responseMessage = new SnapStateChunkResponseMessage(request.getId(), chunkBytes, request.getBlockNumber(), request.getFrom(), to, it.isEmpty());

            long totalChunkTime = System.currentTimeMillis() - startChunk;

            logger.debug("SERVER - Sending state chunk from {} of {} bytes to node {}, totalTime {}ms", request.getFrom(), chunkBytes.length, sender.getPeerNodeID(), totalChunkTime);
            sender.sendMessage(responseMessage);
        }).start();
    }

    public void processStateChunkResponse(Peer peer, SnapStateChunkResponseMessage message) {
        responseQueue.add(message);
        logger.debug("CLIENT - State chunk received from: {}", message.getFrom());
        while (!responseQueue.isEmpty()) {
            SnapStateChunkResponseMessage nextMessage = responseQueue.peek();
            logger.debug("CLIENT - State chunk dequeued from: {} - expected: {}", nextMessage.getFrom(), nextExpectedFrom);
            if (nextMessage.getFrom() == nextExpectedFrom) {
                processOrderedStateChunkResponse(peer, responseQueue.poll());
                nextExpectedFrom += chunkSize * 1024L;
            } else {
                break;
            }
        }
        if (!message.isComplete()) {
            executeNextChunkRequestTask(peer);
        }
    }

    public void processOrderedStateChunkResponse(Peer peer, SnapStateChunkResponseMessage message) {
        try {
            logger.debug("CLIENT - Processing State chunk received from: {}", message.getFrom());
            peersInformation.getOrRegisterPeer(peer);
            snapSyncState.newChunk();

            RLPList nodeLists = RLP.decodeList(message.getChunkOfTrieKeyValue());
            final RLPList preRootElements = RLP.decodeList(nodeLists.get(0).getRLPData());
            final RLPList trieElements = RLP.decodeList(nodeLists.get(1).getRLPData());
            byte[] firstNodeLeftHash = nodeLists.get(2).getRLPData();
            final RLPList lastNodeHashes = RLP.decodeList(nodeLists.get(3).getRLPData());
            final RLPList postRootElements = RLP.decodeList(nodeLists.get(4).getRLPData());
            logger.debug(
                    "CLIENT - Received state chunk of {} elements ({} bytes).",
                    trieElements.size(),
                    message.getChunkOfTrieKeyValue().length
            );
            List<TrieDTO> preRootNodes = new ArrayList<>();
            List<TrieDTO> nodes = new ArrayList<>();
            List<TrieDTO> postRootNodes = new ArrayList<>();


            for (int i = 0; i < preRootElements.size(); i++) {
                final RLPList trieElement = (RLPList) preRootElements.get(i);
                final byte[] value = trieElement.get(0).getRLPData();
                final byte[] leftHash = trieElement.get(1).getRLPData();
                TrieDTO node = TrieDTO.decodeFromSync(value);
                node.setLeftHash(leftHash);
                preRootNodes.add(node);
            }

            if (trieElements.size() > 0) {
                for (int i = 0; i < trieElements.size(); i++) {
                    final RLPElement trieElement = trieElements.get(i);
                    byte[] value = trieElement.getRLPData();
                    nodes.add(TrieDTO.decodeFromSync(value));
                }
                nodes.get(0).setLeftHash(firstNodeLeftHash);
            }

            if (lastNodeHashes.size() > 0) {
                TrieDTO lastNode = nodes.get(nodes.size() - 1);
                lastNode.setLeftHash(lastNodeHashes.get(0).getRLPData());
                lastNode.setRightHash(lastNodeHashes.get(1).getRLPData());
            }

            for (int i = 0; i < postRootElements.size(); i++) {
                final RLPList trieElement = (RLPList) postRootElements.get(i);
                final byte[] value = trieElement.get(0).getRLPData();
                final byte[] rightHash = trieElement.get(1).getRLPData();
                TrieDTO node = TrieDTO.decodeFromSync(value);
                node.setRightHash(rightHash);
                postRootNodes.add(node);
            }

            if (TrieDTOInOrderRecoverer.verifyChunk(this.remoteRootHash, preRootNodes, nodes, postRootNodes)) {
                this.allNodes.addAll(nodes);
                this.stateSize = this.stateSize.add(BigInteger.valueOf(trieElements.size()));
                this.stateChunkSize = this.stateChunkSize.add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length));
                logger.debug("CLIENT - State progress: {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
                if (!message.isComplete()) {
                    //executeNextChunkRequestTask(peer);
                } else {
                    new Thread(() -> {
                        rebuildStateAndSave();
                        logger.info("CLIENT - Snapshot sync finished!");
                        //TODO (pato): this.stopSyncing();
                        try {
                            Thread.sleep(DELAY_BTW_RUNS);
                        } catch (InterruptedException ignored) {
                        }
                        SnapshotProcessor.this.allNodes = Lists.newArrayList();
                        SnapshotProcessor.this.stateSize = BigInteger.ZERO;
                        SnapshotProcessor.this.stateChunkSize = BigInteger.ZERO;
                        SnapshotProcessor.this.chunkTasks.clear();
                        SnapshotProcessor.this.nextExpectedFrom = 0;
                        //duplicateTheChunkSize();
                        logger.debug("Starting again the infinite loop! With chunk size = {}", SnapshotProcessor.this.chunkSize);
                        generateChunkRequestTasks();
                        startRequestingChunks();
                    }).start();
                }
            } else {
                logger.error("Error while verifying chunk response: {}", message);
                throw new Exception("Error verifying chunk.");
            }

        } catch (Exception e) {
            logger.error("Error while processing chunk response.", e);
        }
    }


    private void duplicateTheChunkSize() {
        this.chunkSize = this.chunkSize * 2 > CHUNK_MAX ? CHUNK_MIN : this.chunkSize * 2;
    }

    /**
     * Once state share is received, rebuild the trie, save it in db and save all the blocks.
     */
    private void rebuildStateAndSave() {
        logger.debug("CLIENT - State Completed! {} chunks ({} bytes) - chunk size = {}",
                this.stateSize.toString(), this.stateChunkSize.toString(), this.chunkSize);
        final TrieDTO[] nodeArray = this.allNodes.toArray(new TrieDTO[0]);
        logger.debug("CLIENT - Recovering trie...");
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, (t) -> {
        });//TODO (pato): this.trieStore::saveDTO);
        if (!result.isPresent() || !Arrays.equals(this.remoteRootHash, result.get().calculateHash())) {
            logger.error("CLIENT - State final validation FAILED");
        } else {
            logger.debug("CLIENT - State final validation OK!");
        }
        /* TODO (pato):
            logger.debug("CLIENT - Saving previous blocks...");
            this.blockchain.removeBlocksByNumber(0l);
            for (int i = 0; i < this.blocks.size(); i++) {
                this.blockStore.saveBlock(this.blocks.get(i), this.difficulties.get(i), true);
            }
            logger.debug("CLIENT - Setting last block as best block...");
            this.blockchain.setStatus(this.lastBlock, this.lastBlockDifficulty);
            this.transactionPool.setBestBlock(this.lastBlock);
        */
    }


    private void generateChunkRequestTasks() {
        long from = 0;
        logger.debug("Generating chunk request tasks...");
        while (from < remoteTrieSize) {
            ChunkTask task = new ChunkTask(this.lastBlock.getNumber(), from, chunkSize);
            chunkTasks.add(task);
            from += chunkSize * 1024L;
        }
        logger.debug("Generated: {} chunk request tasks.", chunkTasks.size());
    }

    private void startRequestingChunks() {
        List<Peer> bestPeerCandidates = peers.getBestPeerCandidates();
        List<Peer> peerList = bestPeerCandidates.subList(0, !parallel ? 1 : bestPeerCandidates.size());
        for (Peer peer : peerList) {
            executeNextChunkRequestTask(peer);
        }
    }

    private void executeNextChunkRequestTask(Peer peer) {
        if (!chunkTasks.isEmpty()) {
            ChunkTask task = chunkTasks.poll();
            task.setPeer(peer);
            task.execute();
        } else {
            logger.warn("No more chunk request tasks.");
        }
    }

    public class ChunkTask {
        private final long blockNumber;
        private final long from;
        private final int chunkSize;
        private Peer peer;

        public ChunkTask(long blockNumber, long from, int chunkSize) {
            this.blockNumber = blockNumber;
            this.from = from;
            this.chunkSize = chunkSize;
        }

        public void setPeer(Peer peer) {
            this.peer = peer;
        }

        public void execute() {
            requestStateChunk(peer, from, blockNumber, chunkSize);
        }
    }

    private static byte[] getBytes(byte[] result) {
        return result != null ? result : new byte[0];
    }

}
