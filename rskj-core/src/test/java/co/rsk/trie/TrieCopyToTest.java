package co.rsk.trie;

import org.ethereum.datasource.HashMapDB;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 28/01/2018.
 */
public class TrieCopyToTest {
    @Test
    public void copyEmptyTrie() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);

        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        Trie trie = new TrieImpl(store1, true);

        trie.save();

        trie.copyTo(store2);

        Trie result = store2.retrieve(trie.getHash());

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(trie.getHash(), result.getHash());
    }

    @Test
    public void copyTrieWithOneHundredValues() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);

        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        Trie trie = new TrieImpl(store1, true);

        for (int k = 0; k < 100; k++) {
            trie = trie.put(k + "", (k + "").getBytes());
        }

        trie.save();

        trie.copyTo(store2);

        Trie result = store2.retrieve(trie.getHash());

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(trie.getHash(), result.getHash());

        for (int k = 0; k < 100; k++) {
            Assert.assertArrayEquals((k + "").getBytes(), result.get(k + ""));
        }
    }

    @Test
    public void copyTwoTriesWithOneHundredValues() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);

        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        Trie trie1 = new TrieImpl(store1, true);
        Trie trie2 = new TrieImpl(store1, true);

        for (int k = 0; k < 100; k++) {
            trie1 = trie1.put(k + "", (k + "").getBytes());
            trie2 = trie2.put(k + 100 + "", (k + 100 + "").getBytes());
        }

        trie1.save();
        trie2.save();

        trie1.copyTo(store2);
        trie2.copyTo(store2);

        Trie result1 = store2.retrieve(trie1.getHash());

        Assert.assertNotNull(result1);
        Assert.assertArrayEquals(trie1.getHash(), result1.getHash());

        for (int k = 0; k < 100; k++) {
            Assert.assertArrayEquals((k + "").getBytes(), result1.get(k + ""));
        }

        Trie result2 = store2.retrieve(trie2.getHash());

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(trie2.getHash(), result2.getHash());

        for (int k = 0; k < 100; k++) {
            Assert.assertArrayEquals((k + 100 + "").getBytes(), result2.get(k + 100 + ""));
        }
    }
}
