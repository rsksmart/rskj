package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Maps;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.net.NodeHandler;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class SnapshotProcessor implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");
    private static final String KBYTES = "kbytes";

    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final int chunkSize;
    private final String chunkSizeType;

    private boolean connected;
    private long messageId = 0;
    private boolean enabled = false;
    private final Map<String, Iterator<IterationElement>> iterators;
    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;

    public SnapshotProcessor(
            NodeManager nodeManager,
            Blockchain blockchain,
            TrieStore trieStore,
            PeerClientFactory peerClientFactory,
            int chunkSize, String chunkSizeType) {
        this.nodeManager = nodeManager;
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
        this.chunkSize = chunkSize;
        this.chunkSizeType = chunkSizeType;
        this.iterators = Maps.newConcurrentMap();
    }

    @Override
    public void start() {
        enabled = true;
        connect();
    }

    @Override
    public void stop() {

    }

    public synchronized void add(Channel peer) {
        if (!enabled) {
            return;
        }
        if (!connected) {
            this.connected = true;
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, 0l);
        }
    }

    public void processStateChunk(Peer peer, StateChunkResponseMessage message) {
        final RLPList trieElements = RLP.decodeList(message.getChunkOfTrieKeyValue());
        logger.debug(
                "Received state chunk of {} elements ({} bytes).",
                trieElements.size(),
                message.getChunkOfTrieKeyValue().length
        );

        // TODO(iago) do whatever it's needed, reading just to check load
        for (int i = 0; i < trieElements.size(); i++) {
            final RLPList trieElement = (RLPList) trieElements.get(i);
            final byte[] key = trieElement.get(0).getRLPData();
            final int uncompressedSize = ByteUtil.byteArrayToInt(trieElement.get(2).getRLPData());
            final byte[] value = decompressLz4(trieElement.get(1).getRLPData(), uncompressedSize);
            final String keyString = ByteUtil.toHexString(key);
            final String valueString = ByteUtil.toHexString(value);

            if (logger.isTraceEnabled()) {
                logger.trace("State chunk received - Key: {}, Value: {}", keyString, valueString);
            }
        }

        this.stateSize = this.stateSize.add(BigInteger.valueOf(trieElements.size()));
        this.stateChunkSize = this.stateChunkSize.add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length));
        logger.debug("State progress: {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
        if(!message.isComplete()) {
            // request another chunk
            requestState(peer, message.getFrom() + trieElements.size(), message.getBlockNumber());
        } else {
            logger.debug("State Completed! {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());

            logger.debug("Starting again the infinite loop!");
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, 0l);
        }
    }

    public void processStateChunkRequest(Peer sender, StateChunkRequestMessage request) {
        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());

        Long blockNumber = request.getBlockNumber() > 0L ? request.getBlockNumber() : blockchain.getBestBlock().getNumber() - 10;

        List<byte[]> trieEncoded = new ArrayList<>();
        Iterator<IterationElement> it = iterators.get(sender.getPeerNodeID().toString());
        if (it == null || request.getFrom() == 0l) {
            Block block = blockchain.getBlockByNumber(blockNumber);
            Optional<Trie> retrieve = trieStore.retrieve(block.getStateRoot());
            if (!retrieve.isPresent()) {
                return;
            }
            Trie trie = retrieve.get();
            it = trie.getPreOrderIterator();
            iterators.put(sender.getPeerNodeID().toString(), it);
        }

        long i = KBYTES.equals(this.chunkSizeType)? 0l : request.getFrom();
        long limit = KBYTES.equals(this.chunkSizeType)? chunkSize * 1024 : i + chunkSize;
        while (it.hasNext() && i < limit) {
            IterationElement e = it.next();
            logger.info("Single node read.");
            byte[] key = e.getNodeKey().encode();
            byte[] value = e.getNode().getValue();
            if (value == null) {
                logger.debug("State value is null for key {}", ByteUtil.toHexString(key));
                // TODO(iago) revisit this
                continue;
            }

            byte[] compressedValue = compressLz4(value);

            // TODO(iago) remove this
            if (logger.isTraceEnabled()) {
                if (Arrays.equals(decompressLz4(compressedValue, value.length), value)) {
                    logger.trace("===== compressed value is equal to original value for key {}", ByteUtil.toHexString(key));
                } else {
                    logger.trace("===== compressed value is different from original value for key {}", ByteUtil.toHexString(key));
                }
            }

            final byte[] element = RLP.encodeList(RLP.encodeElement(key), RLP.encodeElement(compressedValue), RLP.encodeInt(value.length));
            trieEncoded.add(element);
            logger.info("Single node calculated.");
            i = KBYTES.equals(this.chunkSizeType)? i + element.length : i+1;
        }

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(request.getId(), chunkBytes, blockNumber, request.getFrom(), !it.hasNext());
        logger.debug("Sending state chunk of {} bytes to node {}", chunkBytes.length, sender.getPeerNodeID());
        sender.sendMessage(responseMessage);
    }

    private static byte[] compressLz4(byte[] src) {
        // TODO(iago) share instances
        LZ4Factory lz4Factory = LZ4Factory.safeInstance();
        LZ4Compressor fastCompressor = lz4Factory.fastCompressor();
        int maxCompressedLength = fastCompressor.maxCompressedLength(src.length);
        byte[] dst = new byte[maxCompressedLength];
        int compressedLength = fastCompressor.compress(src, 0, src.length, dst, 0, maxCompressedLength);
        return Arrays.copyOf(dst, compressedLength);
    }

    private static byte[] decompressLz4(byte[] src, int originalSize) {
        LZ4SafeDecompressor decompressor = LZ4Factory.safeInstance().safeDecompressor();

        // TODO(iago) try to not use ByteBuffer
        ByteBuffer decompressedBuffer = ByteBuffer.allocate(originalSize);
        decompressor.decompress(ByteBuffer.wrap(src), decompressedBuffer);

        // TODO(iago) revisit this
        if (!decompressedBuffer.hasArray()) {
            throw new IllegalStateException("Decompressed buffer does not have backing array");
        }

        return decompressedBuffer.array();
    }

    private void connect() {
        // Connect to the first node
        List<NodeHandler> nodes = nodeManager.getNodes(new HashSet<>());
        Node node = nodes.get(0).getNode();
        String ip = node.getHost();
        int port = node.getPort();
        String remoteId = ByteUtil.toHexString(node.getId().getID());
        logger.info("Connecting to: {}:{}", ip, port);
        PeerClient peerClient = peerClientFactory.newInstance();
        peerClient.connectAsync(ip, port, remoteId);
    }

    private void requestState(Peer peer, long from, long blockNumber) {
        logger.debug("Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);
        StateChunkRequestMessage message = new StateChunkRequestMessage(messageId++, blockNumber, from);
        peer.sendMessage(message);
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
