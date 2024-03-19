package co.rsk.trie;

import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.*;

class TrieDTOInOrderIteratorTest {

    private HashMapDB map;
    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        this.map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }


    @Test
    void basicTest() {
        Trie trie = buildTestTrie(trieStore);
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 3);
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertFalse(iterator.isEmpty());
        assertEquals(0, iterator.getFrom());
    }

    @Test
    void emptyTrie() {
        Trie trie = new Trie(trieStore);
        trie.put("foo", new byte[]{0x01});
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 3);
        assertNotNull(iterator);

        TrieDTO trieDTO = iterator.next();
        assertNotNull(trieDTO);
        assertFalse(iterator.hasNext());
    }


    /**
     * @return the following tree
     * <p>
     * 6
     * / \
     * /   \
     * /     7
     * 2       \
     * / \       \
     * 1   \       8
     * 4     /
     * / \   9
     * 3   5
     */
    private static Trie buildTestTrie(TrieStore trieStore) {
        Trie trie = new Trie(trieStore);
        trie = trie.put(decode("0a"), new byte[]{0x06});
        trie = trie.put(decode("0a00"), new byte[]{0x02});
        trie = trie.put(decode("0a80"), new byte[]{0x07});
        trie = trie.put(decode("0a0000"), new byte[]{0x01});
        trie = trie.put(decode("0a0080"), new byte[]{0x04});
        trie = trie.put(decode("0a008000"), new byte[]{0x03});
        trie = trie.put(decode("0a008080"), new byte[]{0x05});
        trie = trie.put(decode("0a8080"), new byte[]{0x08});
        trie = trie.put(decode("0a808000"), new byte[]{0x09});
        return trie;
    }
}