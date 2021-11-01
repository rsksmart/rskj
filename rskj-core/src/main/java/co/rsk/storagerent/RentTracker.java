package co.rsk.storagerent;

import co.rsk.trie.Trie;

import java.util.ArrayList;
import java.util.List;

public class RentTracker {

    private List<Trie> trackedNodes = new ArrayList<>(); // todo(fedejinich) this should be a set to avoid suplicates

    public void trackNode(Trie trie) {
        trackedNodes.add(trie);
    }
}
