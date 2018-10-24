package co.rsk.db;

import java.util.Optional;

/**
 * Defines the Trie data structure.
 * Should be as abstract as possible and not know about any domain object.
 */
public interface Trie {
    Optional<TrieValue> find(TrieKey key);
}
