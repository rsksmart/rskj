package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.net.messages.StateChunkRequestMessage;
import co.rsk.net.messages.StateChunkResponseMessage;
import org.ethereum.net.NodeHandler;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

public class StateRequester implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("staterequester");

    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;

    private boolean connected;
    private long messageId = 0;
    private boolean enabled = false;

    public StateRequester(
            NodeManager nodeManager,
            PeerClientFactory peerClientFactory) {
        this.nodeManager = nodeManager;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
    }

    @Override
    public void start() {
        logger.debug("Starting state requester...");
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
