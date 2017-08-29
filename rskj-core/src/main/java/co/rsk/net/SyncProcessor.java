package co.rsk.net;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 29/08/2017.
 */
public class SyncProcessor {
    private Map<NodeID, Status> peers = new HashMap<>();

    public int getNoPeers() {
        return this.peers.size();
    }

    public void processStatus(MessageSender sender, Status status) {
        peers.put(sender.getNodeID(), status);
    }
}
