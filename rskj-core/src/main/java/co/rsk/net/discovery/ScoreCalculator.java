package co.rsk.net.discovery;

import co.rsk.net.NodeID;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.net.rlpx.Node;

import java.net.InetAddress;

/**
 * Created by ajlopez on 20/07/2017.
 */
public class ScoreCalculator {
    private PeerScoringManager peerScoringManager;

    public ScoreCalculator(PeerScoringManager peerScoringManager) {
        this.peerScoringManager = peerScoringManager;
    }

    public int calculateScore(Node node) {
        if (this.peerScoringManager == null)
            return 0;

        NodeID nodeID = new NodeID(node.getId());
        int nodeScore = this.peerScoringManager.getPeerScoring(nodeID).getScore();

        InetAddress address = node.getAddress().getAddress();
        int ipScore = this.peerScoringManager.getPeerScoring(address).getScore();

        return Math.min(nodeScore, ipScore);
    }
}
