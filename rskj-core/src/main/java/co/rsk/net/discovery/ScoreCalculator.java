package co.rsk.net.discovery;

import co.rsk.scoring.PeerScoringManager;
import org.ethereum.net.rlpx.Node;

/**
 * Created by ajlopez on 20/07/2017.
 */
public class ScoreCalculator {
    private PeerScoringManager peerScoringManager;

    public ScoreCalculator(PeerScoringManager peerScoringManager) {
        this.peerScoringManager = peerScoringManager;
    }

    public int calculateScore(Node node) {
        return 0;
    }
}
