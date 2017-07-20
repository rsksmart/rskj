package co.rsk.net.discovery;

import org.ethereum.net.rlpx.Node;

/**
 * Created by ajlopez on 20/07/2017.
 */
public class NodeInformation {
    private Node node;
    private int distance;
    private int score;

    public NodeInformation(Node node, int distance, int score) {
        this.node = node;
        this.distance = distance;
        this.score = score;
    }
}
