package co.rsk.net;

import co.rsk.config.InternalService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StateRequester implements InternalService {

    private static final long WORKER_TIMEOUT = 60; // seconds
    private static final Logger logger = LoggerFactory.getLogger("net");

    private final NodeManager nodeManager;
    private final PeerClientFactory peerClientFactory;

    private ScheduledExecutorService executor;
    private boolean connected;
    private Channel peer;

    public StateRequester(
            NodeManager nodeManager,
            PeerClientFactory peerClientFactory) {
        this.nodeManager = nodeManager;
        this.peerClientFactory = peerClientFactory;
        this.connected = false;
    }

    @Override
    public void start() {
        this.executor = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "stateRequester"));
        executor.scheduleWithFixedDelay(
                () -> {
                    try {
                        if (!connected) {
                            connect();
                        }
                        requestState();
                    } catch (Throwable t) {
                        logger.error("Unhandled exception", t);
                    }
                }, WORKER_TIMEOUT, WORKER_TIMEOUT, TimeUnit.SECONDS
        );
    }

    @Override
    public void stop() {
        this.executor.shutdown();
    }

    public synchronized void add(Channel peer) {
        if (!connected) {
            this.peer = peer;
            this.connected = true;
            notify();
        }
    }

    private synchronized void connect() {
        // Connect to the first node
        List<NodeHandler> nodes = nodeManager.getNodes(new HashSet<>());
        Node node = nodes.get(0).getNode();
        String ip = node.getHost();
        int port = node.getPort();
        String remoteId = ByteUtil.toHexString(node.getId().getID());
        logger.info("Connecting to: {}:{}", ip, port);
        PeerClient peerClient = peerClientFactory.newInstance();
        peerClient.connectAsync(ip, port, remoteId);

        try {
            wait();
            logger.debug("Thread woke up");
        } catch (InterruptedException e) {
            throw new RuntimeException("Waiting interrupted");
        }
    }

    private void requestState() {
        // Send request state
        // peer.sendMessage()
    }

    public interface PeerClientFactory {
        PeerClient newInstance();
    }
}
