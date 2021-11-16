package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.Assert.*;

public class RentManagerTest {

    private RentManager rentManager;

    @Before
    public void setup() {
        this.rentManager = new RentManager();
    }

    @Test
    public void trackNodes() {
        Trie trie = buildTestTrie();

        rentManager.trackNodes(TrieKeySlice.fromKey(decode("0a008000")),trie);
        List<Trie> trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertFalse(trackedNodes.isEmpty());
        assertEquals(4, trackedNodes.size());
        assertArrayEquals(new byte[] { 0x04 }, trackedNodes.get(0).getValue()); // key 0a0080
        assertArrayEquals(new byte[] { 0x03 }, trackedNodes.get(1).getValue()); // key 0a008000
        assertArrayEquals(new byte[] { 0x06 }, trackedNodes.get(2).getValue()); // key 0a
        assertArrayEquals(new byte[] { 0x02 }, trackedNodes.get(3).getValue()); // key 0a00
    }

    @Test
    public void trackNodes_nonExistentKey() {
        Trie trie = buildTestTrie();

        rentManager.trackNodes(TrieKeySlice.fromKey(decode("1a")), trie);
        List<Trie> trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertTrue(trackedNodes.isEmpty());
        assertEquals(1, rentManager.getMismatches().size());
        assertEquals(TrieKeySlice.fromKey(decode("1a")), rentManager.getMismatches().get(0));
    }

    @Test
    public void trackNodes_containsSomePartOfTheKey() { // but in the end it's a nonexistent key
        Trie trie = buildTestTrie();

        rentManager.trackNodes(TrieKeySlice.fromKey(decode("0a008001")), trie);
        List<Trie> trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertTrue(trackedNodes.isEmpty());
        assertEquals(1, rentManager.getMismatches().size());
        assertEquals(TrieKeySlice.fromKey(decode("0a008001")), rentManager.getMismatches().get(0));
    }

    @Test
    public void trackNodes_nullTrie() {
        rentManager.trackNodes(TrieKeySlice.fromKey(decode("0a008000")), null);
        List<Trie> trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertTrue(trackedNodes.isEmpty());
        assertEquals(0, rentManager.getMismatches().size());
        assertEquals(TrieKeySlice.fromKey(decode("0a008000")), rentManager.getMismatches());
    }

    @Test
    public void trackNodes_alreadyTrackedNodes() {
        Trie trie = buildTestTrie();

        rentManager.trackNodes(TrieKeySlice.fromKey(decode("0a008000")),trie);
        List<Trie> trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertFalse(trackedNodes.isEmpty());
        assertEquals(4, trackedNodes.size());
        assertArrayEquals(new byte[] { 0x04 }, trackedNodes.get(0).getValue()); // key 0a0080
        assertArrayEquals(new byte[] { 0x03 }, trackedNodes.get(1).getValue()); // key 0a008000
        assertArrayEquals(new byte[] { 0x06 }, trackedNodes.get(2).getValue()); // key 0a
        assertArrayEquals(new byte[] { 0x02 }, trackedNodes.get(3).getValue()); // key 0a00

        rentManager.trackNodes(TrieKeySlice.fromKey(decode("0a008000")),trie);
        trackedNodes = new ArrayList<>(rentManager.getTrackedNodes());

        assertFalse(trackedNodes.isEmpty());
        assertEquals(4, trackedNodes.size());
        assertArrayEquals(new byte[] { 0x04 }, trackedNodes.get(0).getValue()); // key 0a0080
        assertArrayEquals(new byte[] { 0x03 }, trackedNodes.get(1).getValue()); // key 0a008000
        assertArrayEquals(new byte[] { 0x06 }, trackedNodes.get(2).getValue()); // key 0a
        assertArrayEquals(new byte[] { 0x02 }, trackedNodes.get(3).getValue()); // key 0a00
    }

    /**
     * @return the following tree
     *
     *       6
     *      / \
     *     /   \
     *    /     7
     *   2       \
     *  / \       \
     * 1   \       8
     *      4     /
     *     / \   9
     *    3   5
     */
    private Trie buildTestTrie() { // todo(fedejinich) duplicated code, extract this to a TrieTestUtil
        Trie trie = new Trie();
        trie = trie.put(decode("0a"), new byte[] { 0x06 });
        trie = trie.put(decode("0a00"), new byte[] { 0x02 });
        trie = trie.put(decode("0a80"), new byte[] { 0x07 });
        trie = trie.put(decode("0a0000"), new byte[] { 0x01 });
        trie = trie.put(decode("0a0080"), new byte[] { 0x04 });
        trie = trie.put(decode("0a8080"), new byte[] { 0x08 });
        trie = trie.put(decode("0a008000"), new byte[] { 0x03 });
        trie = trie.put(decode("0a008080"), new byte[] { 0x05 });
        trie = trie.put(decode("0a808000"), new byte[] { 0x09 });

        return trie;
    }
}
