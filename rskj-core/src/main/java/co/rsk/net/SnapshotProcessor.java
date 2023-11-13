package co.rsk.net;

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
    private List<byte[]> elements;

    private long remoteTrieSize;
    private byte[] remoteRootHash;
    private List<Block> blocks;
    private Block lastBlock;

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
        this.elements = Lists.newArrayList();
        this.blockStore = blockStore;
        this.blocks = Lists.newArrayList();
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
        for (long i = checkpointBlockNumber - BLOCK_CHUNK_SIZE; i < checkpointBlockNumber; i++) {
            blocks.add(blockchain.getBlockByNumber(i));
        }

        Block checkpointBlock = blockchain.getBlockByNumber(checkpointBlockNumber);
        blocks.add(checkpointBlock);
        byte[] rootHash = checkpointBlock.getStateRoot();
        Optional<TrieDTO> opt =  trieStore.retrieveDTO(rootHash);

        long trieSize = 0;
        if (opt.isPresent()) {
            trieSize = opt.get().getTotalSize();
        } else {
            logger.debug("SERVER - trie is notPresent");
        }
        logger.debug("SERVER - processing snapshot status request - rootHash: {} trieSize: {}", rootHash, trieSize);
        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(blocks, trieSize);
        sender.sendMessage(responseMessage);
    }
    public void processSnapStatusResponse(Peer sender, SnapStatusResponseMessage responseMessage) {
        List<Block> blocks = responseMessage.getBlocks();
        Block lastBlock = blocks.get(blocks.size() - 1);
        this.lastBlock = lastBlock;
        this.remoteRootHash = lastBlock.getStateRoot();
        this.remoteTrieSize = responseMessage.getTrieSize();
        this.blocks.addAll(blocks);
        logger.debug("CLIENT - Processing snapshot status response - blockNumber: {} rootHash: {} trieSize: {}", lastBlock.getNumber(), remoteRootHash, remoteTrieSize);
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

        while (it.hasNext()) {
            TrieDTO e = it.next();
            if (it.hasNext() || it.isEmpty()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Single node read.");
                }
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

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
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
        synchronized (responseQueue) {
            responseQueue.add(message);
            processResponseQueue(peer);
        }
    }

    private void processResponseQueue(Peer peer) {
        while (true) {
            SnapStateChunkResponseMessage nextMessage = responseQueue.peek();
            if (nextMessage != null && nextMessage.getFrom() == nextExpectedFrom.get()) {
                processOrderedStateChunkResponse(peer, responseQueue.poll());
                nextExpectedFrom.addAndGet(chunkSize * 1024L);
            } else {
                break;
            }
        }
    }

    public void processOrderedStateChunkResponse(Peer peer, SnapStateChunkResponseMessage message) {
        peersInformation.getOrRegisterPeer(peer);

        snapSyncState.newChunk();

        final RLPList trieElements = RLP.decodeList(message.getChunkOfTrieKeyValue());
        logger.debug(
                "CLIENT - Received state chunk of {} elements ({} bytes).",
                trieElements.size(),
                message.getChunkOfTrieKeyValue().length
        );

        // TODO(snap-poc) do whatever it's needed, reading just to check load
        for (int i = 0; i < trieElements.size(); i++) {
            final RLPList trieElement = (RLPList) trieElements.get(i);
            final int rawSize = ByteUtil.byteArrayToInt(trieElement.get(1).getRLPData());
            byte[] value = trieElement.get(0).getRLPData();

            boolean isCompressed = rawSize != UNCOMPRESSED_FLAG;
            if (isCompressed) {
                value = Compressor.decompressLz4(value, rawSize);
            }
            this.elements.add(value);
            if (logger.isTraceEnabled()) {
                final String valueString = value == null ? "null" : ByteUtil.toHexString(value);
                logger.trace("CLIENT - State chunk received - Value: {}", valueString);
            }
        }
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
        //activeTasks.remove(message.getFrom());
    }

    private void rebuildStateAndSave() {
        logger.debug("CLIENT - State Completed! {} chunks ({} bytes) - chunk size = {}",
                this.stateSize.toString(), this.stateChunkSize.toString(), this.chunkSize);
        logger.debug("CLIENT - Mapping elements...");
        final TrieDTO[] nodeArray = this.elements.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
        logger.debug("CLIENT - Recovering trie...");
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, this.trieStore::saveDTO);
        if (!result.isPresent() || !validateTrie(result.get().calculateHash())) {
            logger.error("CLIENT - State final validation FAILED");
        }else {
            logger.debug("CLIENT - State final validation OK!");
        }
        logger.debug("CLIENT - Saving previous blocks...");
        this.blocks.forEach((block -> {
            this.blockStore.saveBlock(block, block.getDifficulty(), true);
        }));
        logger.debug("CLIENT - Setting last block as best block...");
        this.blockchain.setStatus(this.lastBlock, this.lastBlock.getDifficulty());
        this.transactionPool.setBestBlock(this.lastBlock);
    }

    private boolean validateTrie(byte[] rootHash) {
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
        for (long i = snapBlocksRequestMessage.getBlockNumber() - BLOCK_CHUNK_SIZE; i < snapBlocksRequestMessage.getBlockNumber(); i++) {
            blocks.add(blockchain.getBlockByNumber(i));
        }
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(blocks);
        sender.sendMessage(responseMessage);
    }

    public void processSnapBlocksResponse(Peer sender, SnapBlocksResponseMessage snapBlocksResponseMessage) {
        logger.debug("CLIENT - Processing snap blocks response");
        List<Block> blocks = snapBlocksResponseMessage.getBlocks();
        this.blocks.addAll(blocks);
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

        public Peer getPeer() {
            return peer;
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

    private void addChunkTask(long from) {
        ChunkTask task = new ChunkTask(this.lastBlock.getNumber(), from, chunkSize, getNextPeer());
        chunkTasks.add(task);
    }
}
