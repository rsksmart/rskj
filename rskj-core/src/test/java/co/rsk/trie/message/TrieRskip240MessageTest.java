package co.rsk.trie.message;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.HashMapDB;
import org.junit.Test;

import java.util.Date;

import static co.rsk.trie.Trie.RSKIP240_TRIE_VERSION;
import static co.rsk.trie.Trie.fromMessage;
import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.Assert.*;

public class TrieRskip240MessageTest {

    private static final long TEST_TIMESTAMP = new Date().getTime();

    @Test
    public void trieWithLeftEmbeddedChildToMessageHop() {
        byte[] value1 = {1};
        byte[] value2 = {7};

        Trie trie = new Trie()
                .put(decode("0a"), value1)
                .put(decode("0a00"), value2);

        trie = trie
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a00")), TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        Trie deserialized = fromMessage(message, null);

        assertEquals(trie, deserialized);

        // describing the proposed trie
        assertTrue(isRskip240Node(message));

        assertTrue(deserialized.getLeft().isEmbeddable());
        assertTrue(isRskip240Node(deserialized.getLeft().getNode().get().toMessage()));

        // check contained values & timestamps
        assertArrayEquals(new byte[]{1}, deserialized.get(decode("0a")));
        assertArrayEquals(new byte[]{7}, deserialized.get(decode("0a00")));

        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a00")).getLastRentPaidTimestamp());
    }

    @Test
    public void trieWithLeftChildToMessageHop() {
        byte[] value = new byte[]{1};
        TrieStoreImpl store = new TrieStoreImpl(new HashMapDB()); // store to save values

        Trie trie = new Trie(store)
                .put(decode("0a"), value)
                .put(decode("0a01"), value)
                .put(decode("0a0110"), value);

        trie = trie
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a01")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a0110")), TEST_TIMESTAMP);

        // save into the store, necessary for deserialization
        store.save(trie);

        byte[] message = trie.toMessage();

        Trie deserialized = fromMessage(message, store);

        assertEquals(trie, deserialized);

        // describing the proposed trie
        assertTrue(isRskip240Node(message));

        assertTrue(!deserialized.getLeft().isEmpty());
        assertTrue(deserialized.getRight().isEmpty());
        assertTrue(!deserialized.getLeft().getNode().get().getLeft().isEmpty());

        // check contained values & timestamps
        assertArrayEquals(value, deserialized.get(decode("0a")));
        assertArrayEquals(value, deserialized.get(decode("0a01")));
        assertArrayEquals(value, deserialized.get(decode("0a0110")));

        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a01")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a0110")).getLastRentPaidTimestamp());
    }

    @Test
    public void trieWithEmbeddedLeftRightChildsToMessageHop() {
        byte[] value = new byte[]{1};
        TrieStoreImpl store = new TrieStoreImpl(new HashMapDB()); // store to save values

        Trie trie = new Trie(store)
                .put(decode("1a"), value)
                .put(decode("0a"), value)
                .put(decode("1a10"), value);

        trie = trie
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("1a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("1a10")), TEST_TIMESTAMP);

        // save into the store, necessary for deserialization
        store.save(trie);

        byte[] message = trie.toMessage();

        Trie deserialized = fromMessage(message, store);

        assertEquals(trie, deserialized);

        // describing the proposed trie
        assertTrue(isRskip240Node(message));

        assertTrue(!deserialized.getLeft().isEmpty());
        assertTrue(deserialized.getLeft().isEmbeddable());
        Trie leftEmbeddedChild = deserialized.getLeft().getNode().get();
        assertTrue(leftEmbeddedChild.isTerminal());
        assertTrue(isRskip240Node(leftEmbeddedChild.toMessage()));

        assertTrue(!deserialized.getRight().isEmpty());
        Trie rightChild = deserialized.getRight().getNode().get();
        assertTrue(!rightChild.getLeft().isEmpty());
        assertTrue(isRskip240Node(rightChild.toMessage()));

        assertTrue(rightChild.getRight().isEmpty());
        Trie rightChildChild = rightChild.getLeft().getNode().get();
        assertTrue(rightChildChild.isTerminal());
        assertTrue(isRskip240Node(rightChildChild.toMessage()));

        // check contained values & timestamps
        assertArrayEquals(value, deserialized.get(decode("1a")));
        assertArrayEquals(value, deserialized.get(decode("0a")));
        assertArrayEquals(value, deserialized.get(decode("1a10")));

        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("1a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("1a10")).getLastRentPaidTimestamp());
    }

    @Test
    public void trieWithLeftEmbeddedRightChildsToMessageHop() {
        byte[] value = new byte[]{1};
        TrieStoreImpl store = new TrieStoreImpl(new HashMapDB()); // store to save values

        Trie trie = new Trie()
                .put(decode("1a"), value)
                .put(decode("0a"), value)
                .put(decode("0a10"), value);

        trie = trie
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("1a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a10")), TEST_TIMESTAMP);

        // save into the store, necessary for deserialization
        store.save(trie);

        byte[] message = trie.toMessage();

        Trie deserialized = fromMessage(message, store);

        assertEquals(trie, deserialized);

        // describing the proposed trie
        assertTrue(isRskip240Node(message));

        assertTrue(!deserialized.getLeft().isEmbeddable());
        Trie leftChild = deserialized.getLeft().getNode().get();
        assertTrue(!leftChild.isTerminal());
        assertTrue(isRskip240Node(leftChild.toMessage()));

        assertTrue(leftChild.getLeft().isEmbeddable());
        Trie leftEmbededChildChild = leftChild.getLeft().getNode().get();
        assertTrue(leftEmbededChildChild.isTerminal());
        assertTrue(isRskip240Node(leftEmbededChildChild.toMessage()));

        assertTrue(deserialized.getRight().isEmbeddable());
        Trie rightEmbeddedChild = deserialized.getRight().getNode().get();
        assertTrue(rightEmbeddedChild.isTerminal());
        assertTrue(isRskip240Node(rightEmbeddedChild.toMessage()));

        // check contained values & timestamps
        assertArrayEquals(value, deserialized.get(decode("1a")));
        assertArrayEquals(value, deserialized.get(decode("0a")));
        assertArrayEquals(value, deserialized.get(decode("0a10")));

        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("1a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a10")).getLastRentPaidTimestamp());
    }

    @Test
    public void trieWithBothEmbeddedChildsToMessageHop() {
        Trie trie = new Trie()
                .put(decode("0a"), new byte[]{1})
                .put(decode("10"), new byte[]{9});

        trie = trie
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a")), TEST_TIMESTAMP)
                .updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("10")), TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        Trie deserialized = fromMessage(message, null);

        assertEquals(trie, deserialized);

        // describing the proposed trie
        assertTrue(isRskip240Node(message));

        assertTrue(deserialized.getLeft().isEmbeddable());
        Trie leftEmbeddedChild = deserialized.getLeft().getNode().get();
        assertTrue(leftEmbeddedChild.isTerminal());
        assertTrue(isRskip240Node(leftEmbeddedChild.toMessage()));

        assertTrue(deserialized.getRight().isEmbeddable());
        Trie rightEmbeddedChild = deserialized.getRight().getNode().get();
        assertTrue(rightEmbeddedChild.isTerminal());
        assertTrue(isRskip240Node(rightEmbeddedChild.toMessage()));

        // check contained values & timestamps
        assertArrayEquals(new byte[]{1}, deserialized.get(decode("0a")));
        assertArrayEquals(new byte[]{9}, deserialized.get(decode("10")));

        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(TEST_TIMESTAMP, deserialized.find(decode("10")).getLastRentPaidTimestamp());
    }

    private boolean isRskip240Node(byte[] message) {
        byte flags = message[0];

        return (flags & RSKIP240_TRIE_VERSION) == RSKIP240_TRIE_VERSION;
    }
}
