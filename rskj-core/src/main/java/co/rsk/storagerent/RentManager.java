package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RentManager {

    private static final Logger logger = LoggerFactory.getLogger(RentManager.class);

    private final Set<Trie> trackedNodes = new HashSet<>();
    private final List<TrieKeySlice> mismatches = new ArrayList<>();

    /**
     * Tracks nodes from a Trie instance
     * */
    public void trackNodes(TrieKeySlice trieKeySlice, Trie trie) {
        if(trie != null) {
            // logger.error("SR - trying to find involved nodes");
            List<Trie> nodes = trie.findNodes(trieKeySlice);
            if(nodes != null) {
                Set<Trie> previousNodes = new HashSet<>(this.trackedNodes); // todo(fedejinich) this is for debugging purposes
                this.trackedNodes.addAll(nodes);
                // logger.error("SR - tracked {} node/s. added nodes = {}", newNodesCount(nodes, previousNodes), printableTrackedNodesSet(newNodes(nodes, previousNodes)));
            } else {
                // track the mismatched key if is not present in trie
                mismatches.add(trieKeySlice);
            }
        } else {
            // logger.error("SR - no nodes to track (key {})", trieKeySlice);
        }
        // logger.error("SR - trackedNodes size = {}, nodes = {}", trackedNodes.size(), printableTrackedNodesSet(trackedNodes));
    }

    @VisibleForTesting
    public Set<Trie> getTrackedNodes() {
        return trackedNodes;
    }

    @VisibleForTesting
    public List<TrieKeySlice> getMismatches() {
        return mismatches;
    }

    private List<String> printableTrackedNodesSet(Set<Trie> trackedNodes) { // todo(fedejinich) this method it's just for debugging purposes
        return trackedNodes.stream()
                .map(RentManager::printableString)
                .collect(Collectors.toList());
    }

    private static String printableString(Trie trie) { // todo(fedejinich) this method it's just for debugging purposes
        return trie.getHash().toHexString().substring(60);
    }

    private int newNodesCount(List<Trie> involvedNodes, Set<Trie> previousNodes) { // todo(fedejinich) this method it's just for debugging purposes
        return newNodes(involvedNodes, previousNodes).size();
    }

    private Set<Trie> newNodes(List<Trie> involvedNodes, Set<Trie> previousNodes) { // todo(fedejinich) this method it's just for debugging purposes
        Set<Trie> newNodes = new HashSet<>(involvedNodes);
        newNodes.removeAll(previousNodes);

        return newNodes;
    }
}
