package co.rsk.trie;

/**
 * Created by SerAdmin on 11/14/2018.
 */
public interface NodeIteratorListener {
    public int processNode(ExpandedKey key,TrieImpl node);
}
