package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Maps;
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
import java.util.*;

public class SnapshotProcessor implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");
    private static final String KBYTES = "kbytes";
    public static final long DELAY_BTW_RUNS = 20 * 60 * 1000;
    public static final int CHUNK_MAX = 1600;
    public static final int CHUNK_MIN = 100;

    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private int chunkSize;
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
            String chunkSizeType) {
        this.nodeManager = nodeManager;
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
        this.chunkSize = 100;
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

        this.stateSize = this.stateSize.add(BigInteger.valueOf(trieElements.size()));
        this.stateChunkSize = this.stateChunkSize.add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length));
        logger.debug("State progress: {} chunks ({} bytes)", this.stateSize.toString(), this.stateChunkSize.toString());
        if(!message.isComplete()) {
            // request another chunk
            requestState(peer, message.getFrom() + trieElements.size(), message.getBlockNumber());
        } else {
            logger.debug("State Completed! {} chunks ({} bytes) - chunk size = {}", this.stateSize.toString(), this.stateChunkSize.toString(), this.chunkSize);

            try {
                Thread.sleep(DELAY_BTW_RUNS);
            } catch (InterruptedException ignored) {
            }
            duplicateTheChunkSize();
            logger.debug("Starting again the infinite loop! With chunk size = {}", this.chunkSize);
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, 0l);
        }
    }

    private void duplicateTheChunkSize() {
        this.chunkSize = this.chunkSize * 2;
        this.chunkSize = this.chunkSize > CHUNK_MAX ? CHUNK_MIN : this.chunkSize;
    }

    public void processStateChunkRequest(Peer sender, StateChunkRequestMessage request) {
        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());
        long startProcessingTime = System.currentTimeMillis();
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
            byte[] key = e.getNodeKey().encode();
            byte[] value = e.getNode().getValue();
            final byte[] element = RLP.encodeList(RLP.encodeElement(key), RLP.encodeElement(value));
            trieEncoded.add(element);
            i = KBYTES.equals(this.chunkSizeType)? i + element.length : i+1;
        }

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        boolean isComplete = !it.hasNext();
        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(request.getId(), chunkBytes, blockNumber, request.getFrom(), isComplete);

        if (isComplete) {
            duplicateTheChunkSize();
        }

        logger.debug("Sending state chunk of {} bytes to node {}", chunkBytes.length, sender.getPeerNodeID());
        long deltaTime = System.currentTimeMillis() - startProcessingTime;
        logger.debug("Processing StateChunkRequest time: [{}]", deltaTime);
        sender.sendMessage(responseMessage);
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
