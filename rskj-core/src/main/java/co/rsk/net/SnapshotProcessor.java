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
import com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SnapshotProcessor {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");
    private static final int UNCOMPRESSED_FLAG = -1;
    public static final long BLOCK_NUMBER = 5630113l;
    public static final long DELAY_BTW_RUNS = 20 * 60 * 1000;
    public static final int CHUNK_MAX = 5000;
    public static final int CHUNK_MIN = 2000;

    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private int chunkSize;
    private final PeersInformation peersInformation;
    private final TransactionPool transactionPool;
    private final boolean isCompressionEnabled;

    private long messageId = 0;
    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;
    private SnapSyncState snapSyncState;
    private List<byte[]> elements;

    private long remoteTrieSize;
    private byte[] remoteRootHash;
    private List<Block> blocks;
    private Block lastBlock;

    public SnapshotProcessor(Blockchain blockchain,
                             TrieStore trieStore,
                             PeersInformation peersInformation,
                             BlockStore blckStore,
                             TransactionPool transactionPool,
                             int chunkSize,
                             boolean isCompressionEnabled) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peersInformation = peersInformation;
        this.chunkSize = CHUNK_MIN;
        this.isCompressionEnabled = isCompressionEnabled;
        this.elements = Lists.newArrayList();
        this.blockStore = blckStore;
        this.blocks = Lists.newArrayList();
        this.transactionPool = transactionPool;
    }

    public void startSyncing(List<Peer> peers, SnapSyncState snapSyncState) {
        // TODO(snap-poc) temporary hack, code in this should be moved to SnapSyncState probably
        this.snapSyncState = snapSyncState;

        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;
        // get more than one peer, use the peer queue
        // TODO(snap-poc) deal with multiple peers algorithm here
        Peer peer = peers.get(0);
        logger.debug("start snapshot sync");
        requestSnapStatus(peer, BLOCK_NUMBER);
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
    private void requestSnapStatus(Peer peer, long blockNumber) {
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(blockNumber);
        peer.sendMessage(message);
    }

    public void processSnapStatusRequest(Peer sender) {
        long trieSize = 0;
        logger.debug("procesing snapshot status request 1");

        List<Block> blocks = Lists.newArrayList();
        for (long i = BLOCK_NUMBER - 400; i < BLOCK_NUMBER; i++) {
            blocks.add(blockchain.getBlockByNumber(i));
        }

        Block block = blockchain.getBlockByNumber(BLOCK_NUMBER);
        blocks.add(block);
        byte[] rootHash = block.getStateRoot();
        Optional<TrieDTO> opt =  trieStore.retrieveDTO(rootHash);

        if (opt.isPresent()) {
            trieSize = opt.get().getTotalSize();
        } else {
            logger.debug("trie is notPresent");
        }
        logger.debug("procesing snapshot status request 2: roothash: {} triesize: {}", rootHash, trieSize);
        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(blocks, trieSize);
        sender.sendMessage(responseMessage);
    }
    public void processSnapStatusResponse(Peer sender, SnapStatusResponseMessage responseMessage) {
        List<Block> blocks = responseMessage.getBlocks();
        Block lastblock = blocks.get(blocks.size() - 1);
        this.lastBlock = lastblock;
        this.remoteRootHash = lastblock.getStateRoot();
        this.remoteTrieSize = responseMessage.getTrieSize();
        this.blocks.addAll(blocks);
        logger.debug("processing snapshot status response rootHash: {} triesize: {}", remoteRootHash, remoteTrieSize);
        requestBlocksChunk(sender, blocks.get(0).getNumber());
        requestStateChunk(sender, 0L, BLOCK_NUMBER);
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(Peer peer, long from, long blockNumber) {
        logger.debug("Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);

        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);

        peer.sendMessage(message);
    }

    public void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage request) {
        long startChunk = System.currentTimeMillis();

        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());

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

        logger.debug("Sending state chunk of {} bytes to node {}, compressing time {}ms, totalTime {}ms, compressionFactor {}", chunkBytes.length, sender.getPeerNodeID(), totalCompressingTime, totalChunkTime, compressionFactor);
        sender.sendMessage(responseMessage);
    }

    public void processStateChunkResponse(Peer peer, SnapStateChunkResponseMessage message) {
        peersInformation.getOrRegisterPeer(peer);

        snapSyncState.newChunk();

        final RLPList trieElements = RLP.decodeList(message.getChunkOfTrieKeyValue());
        logger.debug(
                "Received state chunk of {} elements ({} bytes).",
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
                logger.trace("State chunk received - Value: {}", valueString);
            }
        }

        this.stateSize = this.stateSize.add(BigInteger.valueOf(trieElements.size()));
        this.stateChunkSize = this.stateChunkSize.add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length));
        logger.debug("State progress: {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
        if (!message.isComplete()) {
            // request another chunk
            requestStateChunk(peer, message.getTo(), message.getBlockNumber());
        } else {
            rebuildStateAndSave();
            this.stopSyncing();
            /*
            try {
                Thread.sleep(DELAY_BTW_RUNS);
            } catch (InterruptedException ignored) {
            }
            this.chunkSize = this.chunkSize * 2;
            this.chunkSize = this.chunkSize > CHUNK_MAX ? CHUNK_MIN : this.chunkSize;
            logger.debug("Starting again the infinite loop! With chunk size = {}", this.chunkSize);
            this.elements = Lists.newArrayList();
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, BLOCK_NUMBER);*/

        }
    }

    private void rebuildStateAndSave() {
        logger.debug("State Completed! {} chunks ({} bytes) - chunk size = {}",
                this.stateSize.toString(), this.stateChunkSize.toString(), this.chunkSize);
        logger.debug("Mapping elements...");
        final TrieDTO[] nodeArray = this.elements.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
        logger.debug("Recovering trie...");
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, this.trieStore::saveDTO);
        if (!validateTrie(result.get().calculateHash())) {
            logger.warn("trie final validation failed");
        }else {
            logger.debug("trie final validation ok!");
        }
        this.blocks.forEach((block -> {
            this.blockStore.saveBlock(block, block.getDifficulty(), true);
        }));
        this.blockchain.setStatus(this.lastBlock, this.lastBlock.getDifficulty());
        this.transactionPool.setBestBlock(this.lastBlock);
    }

    private boolean validateTrie(byte[] rootHash) {
        logger.debug("validating snapshot sync trie: {} , {}", HexUtils.toJsonHex(rootHash), HexUtils.toJsonHex(remoteRootHash));
        return Arrays.equals(rootHash, remoteRootHash);
    }

    /**
     * BLOCK CHUNK
     */

    private void requestBlocksChunk(Peer sender, long blockNumber) {
        logger.debug("Requesting block chunk to node {} - block {}", sender.getPeerNodeID(), blockNumber);
        sender.sendMessage(new SnapBlocksRequestMessage(blockNumber));
    }
    public void processSnapBlocksRequest(Peer sender, SnapBlocksRequestMessage snapBlocksRequestMessage) {
        logger.debug("processing snap blocks request");
        List<Block> blocks = Lists.newArrayList();
        for (long i = snapBlocksRequestMessage.getBlockNumber() - 400; i < snapBlocksRequestMessage.getBlockNumber(); i++) {
            blocks.add(blockchain.getBlockByNumber(i));
        }
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(blocks);
        sender.sendMessage(responseMessage);
    }

    public void processSnapBlocksResponse(Peer sender, SnapBlocksResponseMessage snapBlocksResponseMessage) {
        logger.debug("processing snap blocks response");
        List<Block> blocks = snapBlocksResponseMessage.getBlocks();
        this.blocks.addAll(blocks);
        long nextChunk = blocks.get(0).getNumber();
        if (nextChunk > BLOCK_NUMBER - 6000) {
            requestBlocksChunk(sender, nextChunk);
        } else {
            logger.debug("finished snap blocks sync.");
        }
    }
}
