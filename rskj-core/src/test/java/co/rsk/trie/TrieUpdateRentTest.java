package co.rsk.trie;

import org.junit.Test;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.Assert.assertEquals;

public class TrieUpdateRentTest {

    @Test
    public void updateRentTimestampBasicTest() {
        Trie trie = buildTestTrie();

        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a00")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a80")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a0000")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a0080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a008000")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a008080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a8080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a808000")).getLastRentPaidTimestamp());

        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(decode("0a008080")), 20);

        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a00")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a80")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a0000")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a0080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a008000")).getLastRentPaidTimestamp());
        assertEquals(20, trie.find(decode("0a008080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a8080")).getLastRentPaidTimestamp());
        assertEquals(NO_RENT_TIMESTAMP, trie.find(decode("0a808000")).getLastRentPaidTimestamp());
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
    private static Trie buildTestTrie() {
        Trie trie = new Trie();
        trie = trie.put(decode("0a"), new byte[] { 0x06 });
        trie = trie.put(decode("0a00"), new byte[] { 0x02 });
        trie = trie.put(decode("0a80"), new byte[] { 0x07 });
        trie = trie.put(decode("0a0000"), new byte[] { 0x01 });
        trie = trie.put(decode("0a0080"), new byte[] { 0x04 });
        trie = trie.put(decode("0a008000"), new byte[] { 0x03 });
        trie = trie.put(decode("0a008080"), new byte[] { 0x05 });
        trie = trie.put(decode("0a8080"), new byte[] { 0x08 });
        trie = trie.put(decode("0a808000"), new byte[] { 0x09 });

        return trie;
    }
}