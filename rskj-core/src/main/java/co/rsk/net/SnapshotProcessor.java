package co.rsk.net;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.compression.Compressor;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import com.google.common.collect.Lists;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SnapshotProcessor {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");
    private static final int UNCOMPRESSED_FLAG = -1;
    public static final int BLOCK_NUMBER_CHECKPOINT = 5000;
    public static final int BLOCK_CHUNK_SIZE = 400;
    public static final int BLOCKS_REQUIRED = 6000;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private int chunkSize;
    private final PeersInformation peersInformation;
    private final TransactionPool transactionPool;
    private final boolean isCompressionEnabled;

    private long messageId = 0;
    private boolean enabled = false;

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
    private List<Peer> peers = new ArrayList<>();


    // flag for parallel requests
    private boolean parallel = false;

    // executor service
    private ExecutorService executorService;
    private static final int SNAPSHOT_THREADS = 2;

    // keep track of active chunkTasks (concurrently)
    //private ConcurrentMap<Long, ChunkTask> activeTasks = new ConcurrentHashMap<>();


    public SnapshotProcessor(Blockchain blockchain,
            TrieStore trieStore,
            PeersInformation peersInformation,
            BlockStore blockStore,
            TransactionPool transactionPool,
            int chunkSize,
            boolean isParallelEnabled,
            boolean isCompressionEnabled) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peersInformation = peersInformation;
        this.chunkSize = chunkSize;
        this.executorService = Executors.newFixedThreadPool(SNAPSHOT_THREADS);
        this.isCompressionEnabled = isCompressionEnabled;
        this.allNodes = Lists.newArrayList();
        this.blockStore = blockStore;
        this.blocks = Lists.newArrayList();
        this.difficulties = Lists.newArrayList();
        this.transactionPool = transactionPool;
        this.parallel = isParallelEnabled;
    }

    public void startSyncing(List<Peer> peers, SnapSyncState snapSyncState) {
        // TODO(snap-poc) temporary hack, code in this should be moved to SnapSyncState probably
        this.snapSyncState = snapSyncState;
        this.peers = peers;
        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;
        // get more than one peer, use the peer queue
        // TODO(snap-poc) deal with multiple peers algorithm here
        Peer peer = peers.get(0);
        logger.info("CLIENT - Starting Snapshot sync.");
        requestSnapStatus(peer);
    }

    // TODO(snap-poc) should be called on errors too
    private void stopSyncing() {
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
        long bestBlockNumber = blockchain.getBestBlock().getNumber();
        long checkpointBlockNumber = bestBlockNumber - (bestBlockNumber % BLOCK_NUMBER_CHECKPOINT);
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        for (long i = checkpointBlockNumber - BLOCK_CHUNK_SIZE; i < checkpointBlockNumber; i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }

        Block checkpointBlock = blockchain.getBlockByNumber(checkpointBlockNumber);
        blocks.add(checkpointBlock);
        difficulties.add(blockStore.getTotalDifficultyForHash(checkpointBlock.getHash().getBytes()));
        byte[] rootHash = checkpointBlock.getStateRoot();
        Optional<TrieDTO> opt =  trieStore.retrieveDTO(rootHash);

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
        generateTasks();
        startProcessing();
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(Peer peer, long from, long blockNumber, int chunkSize) {
        logger.debug("CLIENT - Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);

        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);

        peer.sendMessage(message);
    }

    public void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage request) {
        long startChunk = System.currentTimeMillis();

        logger.debug("SERVER - Processing state chunk request from node {}", sender.getPeerNodeID());

        List<byte[]> trieEncoded = new ArrayList<>();
        Block block = blockchain.getBlockByNumber(request.getBlockNumber());
        final long to = request.getFrom() + (request.getChunkSize() * 1024);
        TrieDTOInOrderIterator it = new TrieDTOInOrderIterator(trieStore, block.getStateRoot(), request.getFrom(), to);

        long rawSize = 0L;
        long compressedSize = 0L;
        long totalCompressingTime = 0L;

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
                byte[] effectiveValue = e.getEncoded();
                int uncompressedSizeParam = UNCOMPRESSED_FLAG;
                if (effectiveValue != null && isCompressionEnabled) {
                    rawSize += effectiveValue.length;

                    long startCompress = System.currentTimeMillis();
                    byte[] compressedValue = Compressor.compressLz4(effectiveValue);
                    long totalCompress = System.currentTimeMillis() - startCompress;
                    totalCompressingTime += totalCompress;

                    if(logger.isTraceEnabled()){
                        boolean valid = Compressor.validateCompression(effectiveValue, compressedValue);
                        logger.trace("===== compressed validation = {}, for key {}", valid, ByteUtil.toHexString(effectiveValue));
                    }

                    boolean couldCompress = compressedValue.length < effectiveValue.length;
                    if (couldCompress) {
                        compressedSize += compressedValue.length;
                        uncompressedSizeParam = effectiveValue.length;
                        effectiveValue = compressedValue;
                    } else {
                        compressedSize += effectiveValue.length;
                    }
                }

                final byte[] element = RLP.encodeList(RLP.encodeElement(effectiveValue), RLP.encodeInt(uncompressedSizeParam));
                trieEncoded.add(element);

                if (logger.isTraceEnabled()) {
                    logger.trace("Single node calculated.");
                }
            }
        }
        byte[] firstNodeLeftHash = RLP.encodeElement(first.getLeftHash());
        byte[] nodesBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        byte[] lastNodeHashes = last != null ? RLP.encodeList(RLP.encodeElement(getBytes(last.getLeftHash())) , RLP.encodeElement(getBytes(last.getRightHash()))) : RLP.encodedEmptyList();

        // Last we add the root nodes on the right of the last visited node. They are used to validate the chunk.
        List<byte[]> postRootNodes = it.getNodesLeftVisiting().stream().map((t) -> RLP.encodeList(RLP.encodeElement(t.getEncoded()), RLP.encodeElement(getBytes(t.getRightHash())))).collect(Collectors.toList());
        byte[] postRootNodesBytes = !postRootNodes.isEmpty() ? RLP.encodeList(postRootNodes.toArray(new byte[0][0])) : RLP.encodedEmptyList();
        byte[] chunkBytes = RLP.encodeList(preRootNodesBytes, nodesBytes, firstNodeLeftHash, lastNodeHashes, postRootNodesBytes);

        SnapStateChunkResponseMessage responseMessage = new SnapStateChunkResponseMessage(request.getId(), chunkBytes, request.getBlockNumber(), request.getFrom(), to, it.isEmpty());

        long totalChunkTime = System.currentTimeMillis() - startChunk;

        double compressionFactor = (double) rawSize / (double) compressedSize;

        logger.debug("SERVER - Sending state chunk of {} bytes to node {}, compressing time {}ms, totalTime {}ms, compressionFactor {}", chunkBytes.length, sender.getPeerNodeID(), totalCompressingTime, totalChunkTime, compressionFactor);
        sender.sendMessage(responseMessage);
    }

    // priority queue for ordering chunk responses
    private final PriorityQueue<SnapStateChunkResponseMessage> responseQueue = new PriorityQueue<>(
            Comparator.comparingLong(SnapStateChunkResponseMessage::getFrom)
    );

    private final AtomicLong nextExpectedFrom = new AtomicLong(0L);

    public void processStateChunkResponse(Peer peer, SnapStateChunkResponseMessage message) {
        try {
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
                    final RLPList trieElement = (RLPList) trieElements.get(i);
                    final int rawSize = ByteUtil.byteArrayToInt(trieElement.get(1).getRLPData());
                    byte[] value = trieElement.get(0).getRLPData();

                    boolean isCompressed = rawSize != UNCOMPRESSED_FLAG;
                    if (isCompressed) {
                        value = Compressor.decompressLz4(value, rawSize);
                    }
                    nodes.add(TrieDTO.decodeFromSync(value));

                    if (logger.isTraceEnabled()) {
                        final String valueString = value == null ? "null" : ByteUtil.toHexString(value);
                        logger.trace("CLIENT - State chunk received - Value: {}", valueString);
                    }
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

            if (verifyChunk(this.remoteRootHash, preRootNodes, nodes, postRootNodes)) {
                this.allNodes.addAll(nodes);
                this.stateSize = this.stateSize.add(BigInteger.valueOf(trieElements.size()));
                this.stateChunkSize = this.stateChunkSize.add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length));
                logger.debug("CLIENT - State progress: {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
                if (!message.isComplete()) {
                    // request another chunk
                    continueWork();
                } else {
                    rebuildStateAndSave();
                    logger.info("CLIENT - Snapshot sync finished!");
                    this.stopSyncing();
                }
            } else {
                logger.error("Error while verifying chunk response: {}", message);
                throw new Exception("Error verifying chunk.");
            }

        } catch (Exception e) {
            logger.error("Error while processing chunk response.", e);
        }
    }

    public static boolean verifyChunk(byte[] remoteRootHash, List<TrieDTO> preRootNodes, List<TrieDTO> nodes, List<TrieDTO> postRootNodes) {
        List<TrieDTO> allNodes = Lists.newArrayList(preRootNodes);
        allNodes.addAll(nodes);
        allNodes.addAll(postRootNodes);
        if (allNodes.isEmpty()) {
            logger.error("CLIENT - Received empty chunk");
            return false;
        }
        TrieDTO[] nodeArray = allNodes.toArray(new TrieDTO[0]);
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, (t)->{});
        if (!result.isPresent() || !validateTrie(remoteRootHash, result.get().calculateHash())) {
            logger.error("CLIENT - Received chunk with wrong trie");
            return false;
        }
        logger.debug("CLIENT - Received chunk with correct trie");
        return true;
    }

    private void rebuildStateAndSave() {
        logger.debug("CLIENT - State Completed! {} chunks ({} bytes) - chunk size = {}",
                this.stateSize.toString(), this.stateChunkSize.toString(), this.chunkSize);
        logger.debug("CLIENT - Mapping elements...");
        final TrieDTO[] nodeArray = this.allNodes.toArray(new TrieDTO[0]);
        logger.debug("CLIENT - Recovering trie...");
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, this.trieStore::saveDTO);
        if (!result.isPresent() || !validateTrie(this.remoteRootHash, result.get().calculateHash())) {
            logger.error("CLIENT - State final validation FAILED");
        }else {
            logger.debug("CLIENT - State final validation OK!");
        }
        logger.debug("CLIENT - Saving previous blocks...");
        this.blockchain.removeBlocksByNumber(0l);
        for (int i = 0; i < this.blocks.size(); i++) {
            this.blockStore.saveBlock(this.blocks.get(i), this.difficulties.get(i), true);
        }
        logger.debug("CLIENT - Setting last block as best block...");
        this.blockchain.setStatus(this.lastBlock, this.lastBlockDifficulty);
        this.transactionPool.setBestBlock(this.lastBlock);
    }

    private static boolean validateTrie(byte[]remoteRootHash, byte[] rootHash) {
        logger.debug("CLIENT - Validating snapshot sync trie: {} , {}", HexUtils.toJsonHex(rootHash), HexUtils.toJsonHex(remoteRootHash));
        return Arrays.equals(rootHash, remoteRootHash);
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

    public class ChunkTask implements Runnable {
        private final long blockNumber;
        private final long from;
        private final int chunkSize;
        private final Peer peer;

        public ChunkTask(long blockNumber, long from, int chunkSize, Peer peer) {
            this.blockNumber = blockNumber;
            this.from = from;
            this.chunkSize = chunkSize;
            this.peer = peer;
        }
        @Override
        public void run() {
            execute();
        }
        public void execute() {
            requestStateChunk(peer, from, blockNumber, chunkSize);
        }

        public long getFrom() {
            return from;
        }
    }

    private void generateTasks() {
        long from = 0;

        logger.debug("generating snapshot chunk tasks");

        while (from < remoteTrieSize) {
            addChunkTask(from);
            from += chunkSize * 1024L;
        }

        logger.debug("generated: {} snapshot chunk tasks", chunkTasks.size());
    }

    private void startProcessing() {
        if (parallel) {
            for (Peer peer : peers) {
                assignNextTask();
            }
        } else {
            assignNextTask();
        }
    }
    private synchronized void assignNextTask() {
        if (!chunkTasks.isEmpty()) {
            ChunkTask task = chunkTasks.poll();
           // activeTasks.put(task.getFrom(), task);
            executorService.execute(task);
        } else {
            logger.debug("no more tasks");
        }
    }

    private int chunksProcessed = 0;
    private int currentPeerIndex = 0;

    private void continueWork() {
        if (peers.isEmpty()) {
            logger.error("All peers have disconnected. Cannot continue sync process.");
            // Perhaps trigger a reconnection attempt or pause the sync process
          //  reconnectOrPauseSync();
            return;
        }

        if (parallel) {
            assignNextTask();
        } else {
            if (chunksProcessed >= 100) {
                chunksProcessed = 0;
            }
            assignNextTask();
            chunksProcessed++;
        }
    }

    private Peer getNextPeer() {
        if (peers.isEmpty()) {
            logger.debug("snapshot: no more peers");
            return null;
        }
        currentPeerIndex = (currentPeerIndex + 1) % peers.size();
        return peers.get(currentPeerIndex);
    }
    private static byte[] getBytes(byte[] result) {
        return result != null ? result : new byte[0];
    }


    private void addChunkTask(long from) {
        ChunkTask task = new ChunkTask(this.lastBlock.getNumber(), from, chunkSize, getNextPeer());
        chunkTasks.add(task);
    }
}
