package co.rsk.net;

import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SnapSyncState;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Lists;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
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
    private static final String KBYTES = "kbytes";
    private static final int UNCOMPRESSED_FLAG = -1;

    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final int chunkSize;
    private final String chunkSizeType;
    private final PeersInformation peersInformation;

    private final boolean isCompressionEnabled;

    private long messageId = 0;
    private boolean enabled = false;
    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;
    private SnapSyncState snapSyncState;
    private List<byte[]> elements;

    private long remoteTrieSize;
    private byte[] remoteRootHash;

    public SnapshotProcessor(Blockchain blockchain,
            TrieStore trieStore,
            PeersInformation peersInformation,
            int chunkSize, String chunkSizeType,
            boolean isCompressionEnabled) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peersInformation = peersInformation;
        this.chunkSize = chunkSize;
        this.chunkSizeType = chunkSizeType;
        this.isCompressionEnabled = isCompressionEnabled;
        this.elements = Lists.newArrayList();
    }

    public void startSyncing(List<Peer> peers, SnapSyncState snapSyncState) {
        // TODO(snap-poc) temporary hack, code in this should be moved to SnapSyncState probably
        this.snapSyncState = snapSyncState;

        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;

        // TODO(snap-poc) deal with multiple peers algorithm here
        // currentChunk arranca en 0
        // if (!peers.isEmpty()) {
        //      peer = peers.getPeer();
        //      requestState(peer, currentChunk, blockNumber)
        //}
        Peer peer = peers.get(0);
        logger.debug("start snapshot sync");
        requestSnapStatus(peer);
    }

    // TODO(snap-poc) should be called on errors too
    private void stopSyncing() {
        this.stateSize = BigInteger.ZERO;
        this.stateChunkSize = BigInteger.ZERO;

        this.snapSyncState.finished();
    }

    public void processStateChunkResponse(Peer peer, StateChunkResponseMessage message) {
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
                value = decompressLz4(value, rawSize);
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
            requestState(peer, message.getTo(), message.getBlockNumber());
        } else {
            logger.debug("State Completed! {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
            logger.debug("Mapping elements...");
            final TrieDTO[] nodeArray = this.elements.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
            logger.debug("Recovering trie...");
            Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray);
            logger.debug("Recovered root: {}", result.get().calculateHash());
            if (!validateTrie(result.get().calculateHash().getBytes(), result.get().getTotalSize())) {
                logger.debug("trie final validation failed");
            }
            logger.debug("Starting again the infinite loop!");
            this.elements = Lists.newArrayList();
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, 5544285l);
        }
    }

    public void processStateChunkRequest(Peer sender, StateChunkRequestMessage request) {
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
                    byte[] compressedValue = compressLz4(effectiveValue);
                    long totalCompress = System.currentTimeMillis() - startCompress;
                    totalCompressingTime += totalCompress;

                    validateCompression(effectiveValue, compressedValue);

                    boolean couldCompress = compressedValue.length < effectiveValue.length;
                    if (couldCompress) {
                        compressedSize += compressedValue.length;
                        uncompressedSizeParam = effectiveValue.length;
                    } else {
                        compressedSize += effectiveValue.length;
                    }

                    effectiveValue = compressedValue;

                }

                final byte[] element = RLP.encodeList(RLP.encodeElement(effectiveValue), RLP.encodeInt(uncompressedSizeParam));
                trieEncoded.add(element);

                if (logger.isTraceEnabled()) {
                    logger.trace("Single node calculated.");
                }
            }
        }

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(request.getId(), chunkBytes, request.getBlockNumber(), request.getFrom(), to, it.isEmpty());

        long totalChunkTime = System.currentTimeMillis() - startChunk;

        double compressionFactor = (double) rawSize / (double) compressedSize;

        logger.debug("Sending state chunk of {} bytes to node {}, compressing time {}ms, totalTime {}ms, compressionFactor {}", chunkBytes.length, sender.getPeerNodeID(), totalCompressingTime, totalChunkTime, compressionFactor);
        sender.sendMessage(responseMessage);
    }

    private static void validateCompression(byte[] value, byte[] compressedValue) {
        // TODO(snap-poc) remove this when finishing with the compression validations
        if (logger.isTraceEnabled()) {
            if (Arrays.equals(decompressLz4(compressedValue, value.length), value)) {
                logger.trace("===== compressed value is equal to original value for key {}", ByteUtil.toHexString(value));
            } else {
                logger.trace("===== compressed value is different from original value for key {}", ByteUtil.toHexString(value));
            }
        }
    }

    private static byte[] compressLz4(byte[] src) {
        LZ4Factory lz4Factory = LZ4Factory.safeInstance();
        LZ4Compressor fastCompressor = lz4Factory.fastCompressor();
        int maxCompressedLength = fastCompressor.maxCompressedLength(src.length);
        byte[] dst = new byte[maxCompressedLength];
        int compressedLength = fastCompressor.compress(src, 0, src.length, dst, 0, maxCompressedLength);
        return Arrays.copyOf(dst, compressedLength);
    }

    private static byte[] decompressLz4(byte[] src, int expandedSize) {
        LZ4SafeDecompressor decompressor = LZ4Factory.safeInstance().safeDecompressor();
        byte[] dst = new byte[expandedSize];
        decompressor.decompress(src, dst);
        return  dst;
    }

    private void requestSnapStatus(Peer peer) {
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(5544285l);

        logger.debug("requesting snapshot status");

        peer.sendMessage(message);
    }
    private void requestState(Peer peer, long from, long blockNumber) {
        logger.debug("Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);

        StateChunkRequestMessage message = new StateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);

        peer.sendMessage(message);
    }

    public void processSnapStatusResponse(Peer sender, SnapStatusResponseMessage responseMessage) {
        this.remoteRootHash = responseMessage.getRootHash();
        this.remoteTrieSize = responseMessage.getTrieSize();

        logger.debug("processing snapshot status response");

        requestState(sender, 0L, 5544285l);
    }

    public void processSnapStatusRequest(Peer sender) {
        long trieSize = 0;
        logger.debug("procesing snapshot status request 1");

        Block block = blockchain.getBlockByNumber(5544285l);

        byte[] rootHash = block.getStateRoot();
        Optional<TrieDTO> opt =  trieStore.retrieveDTO(rootHash);

        // chequear si es getTotal o getSize
        if (opt.isPresent()) {
            trieSize = opt.get().getTotalSize();
        } else {
            logger.debug("trie is notPresent");
        }
        logger.debug("procesing snapshot status request 2");
        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(trieSize, rootHash);
        sender.sendMessage(responseMessage);
    }

    private boolean validateTrie(byte[] rootHash, long trieSize) {
        logger.debug("validating snapshot sync trie");
        return trieSize == remoteTrieSize && Arrays.equals(rootHash, remoteRootHash);
    }
}
