package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
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
    private final int chunkSize;

    private boolean connected;
    private long messageId = 0;
    private boolean enabled = false;

    public SnapshotProcessor(
            NodeManager nodeManager,
            Blockchain blockchain,
            TrieStore trieStore,
            PeerClientFactory peerClientFactory,
            int chunkSize) {
        this.nodeManager = nodeManager;
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
        this.chunkSize = chunkSize;
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
            requestState(peer);
        }
    }

    public void processStateChunk(Peer peer, StateChunkResponseMessage message) {
        logger.debug(
                "Received state chunk of {} bytes from node {}",
                message.getChunkOfTrieKeyValue().length,
                peer.getPeerNodeID()
        );
        // request another chunk
        requestState(peer);
    }

    public void processStateChunkRequest(Peer sender, long requestId) {
        logger.debug("Processing state chunk request from node {}", sender.getPeerNodeID());

        Block bestBlock = blockchain.getBestBlock();
        Optional<Trie> retrieve = trieStore.retrieve(bestBlock.getStateRoot());

        if (!retrieve.isPresent()) {
            return;
        }

        Trie trie = retrieve.get();
        List<byte[]> trieEncoded = new ArrayList<>();
        Iterator<IterationElement> it = trie.getInOrderIterator();
        int i = 0;

        while (it.hasNext() && i < chunkSize) {
            IterationElement e = it.next();
            byte[] key = e.getNodeKey().encode();
            byte[] value = e.getNode().getValue();
            trieEncoded.add(RLP.encodeList(RLP.encodeElement(key), RLP.encodeElement(value)));
            i++;
        }

        byte[] chunkBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        StateChunkResponseMessage responseMessage = new StateChunkResponseMessage(requestId, chunkBytes);
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

    private void requestState(Peer peer) {
        logger.debug("Requesting state chunk to node {}", peer.getPeerNodeID());
        StateChunkRequestMessage message = new StateChunkRequestMessage(messageId++);
        peer.sendMessage(message);
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
