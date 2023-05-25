package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.net.NodeHandler;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SnapshotProcessor implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");

    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;
    private final Blockchain blockchain;
    private final TrieStore trieStore;

    private boolean connected;
    private long messageId = 0;
    private int lastKey = 0;
    private boolean enabled = false;

    public SnapshotProcessor(
            NodeManager nodeManager,
            Blockchain blockchain,
            TrieStore trieStore,
            PeerClientFactory peerClientFactory) {
        this.nodeManager = nodeManager;
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
    }

    @Override
    public void start() {
        logger.debug("Starting snapshot processor...");
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
            requestState(peer);
        }
    }

    public void processStateChunk(Peer peer, StateChunkResponseMessage message) {
        logger.debug("Received state chunk of {} bytes", message.getChunkOfTrieKeyValue().length);
        // request another chunk
        requestState(peer);
    }

    public void processStateChunkRequest(Peer sender, long requestId) {
        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());

        Block bestBlock = blockchain.getBestBlock();

        logger.debug("Retreiving trie. Trie store is: {}", trieStore);

        Optional<Trie> retrieve = trieStore.retrieve(bestBlock.getStateRoot());

        if (!retrieve.isPresent()) {
            return;
        }

        logger.debug("Trie exists");

        Trie trie = retrieve.get();
        List<ByteArrayWrapper> trieKeys = new ArrayList<>(trie.collectKeys(Integer.MAX_VALUE));
        lastKey = 0;

        logger.debug("Getting nodes");

        int chunk_size = 100;
        List<ByteArrayWrapper> sublistOfKeys = trieKeys.subList(lastKey, chunk_size);
        lastKey += chunk_size;
        List<byte[]> trieEncoded = new ArrayList<>();

        logger.debug("Encoding nodes");

        for (ByteArrayWrapper key : sublistOfKeys
        ) {
            byte[] value = trie.get(key.getData());
            trieEncoded.add(RLP.encodeList(RLP.encodeElement(key.getData()), RLP.encode(value)));
        }

        logger.debug("Sending message", sender.getPeerNodeID());

        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(requestId, RLP.encode(trieEncoded));

        logger.debug("Sending state chunk request to node {}", sender.getPeerNodeID());
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

    private void requestState(Peer peer) {
        logger.debug("Requesting state chunk to node {}", peer.getPeerNodeID());
        StateChunkRequestMessage message = new StateChunkRequestMessage(++messageId);
        peer.sendMessage(message);
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
