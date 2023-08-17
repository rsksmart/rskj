package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import com.google.common.collect.Lists;
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
    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final int chunkSize;
    private final String chunkSizeType;

    private boolean connected;
    private long messageId = 0;
    private boolean enabled = false;
    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;
    private List<byte[]> elements;

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
        this.elements = Lists.newArrayList();
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
            requestState(peer, 0l, 5544285l);
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
        for (int i = 0; i < trieElements.size(); i++) {
            this.elements.add(trieElements.get(i).getRLPData());
        }
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
            logger.debug("Starting again the infinite loop!");
            this.elements = Lists.newArrayList();
            this.stateSize = BigInteger.ZERO;
            this.stateChunkSize = BigInteger.ZERO;
            requestState(peer, 0l, 5544285l);
        }
    }

    public void processStateChunkRequest(Peer sender, StateChunkRequestMessage request) {
        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());

        Long blockNumber = request.getBlockNumber() > 0L ? request.getBlockNumber() : blockchain.getBestBlock().getNumber() - 10;

        List<byte[]> trieEncoded = new ArrayList<>();
        Block block = blockchain.getBlockByNumber(blockNumber);
        final long to = request.getFrom() + (request.getChunkSize() * 1024);
        TrieDTOInOrderIterator it = new TrieDTOInOrderIterator(trieStore, block.getStateRoot(), request.getFrom(), to);

        while (it.hasNext() && !it.isEmpty()) {
            TrieDTO e = it.next();
            if (it.hasNext() || it.isEmpty()) {
                byte[] value = e.getEncoded();
                final byte[] element = RLP.encodeElement(value);
                trieEncoded.add(element);
            }
        }

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(request.getId(), chunkBytes, blockNumber, request.getFrom(), to, it.isEmpty());
        logger.debug("Sending state chunk of {} bytes to node {}", chunkBytes.length, sender.getPeerNodeID());
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
        StateChunkRequestMessage message = new StateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);
        peer.sendMessage(message);
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
