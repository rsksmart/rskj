package org.ethereum.datasource;

import co.rsk.trie.TrieValueTest;
import org.ethereum.crypto.Keccak256Helper;
import org.junit.Assert;
import org.junit.Test;

public class FastTrieTest {
    @Test
    public void noLongValueInEmptyTrie() {
        FastTrie trie = new FastTrie();

        Assert.assertTrue(trie.getValue()<0);
    }

    @Test
    public void noLongValueInTrieWithShortValue() {
        byte[] key = new byte[] { 0x04, 0x05 };
        int value =100;
        FastTrie trie = new FastTrie().put(key, value);
        Assert.assertEquals(value, trie.getValue());
    }

    // Key tests
    @Test
    public void getNullForUnknownKey() {
        FastTrie trie = new FastTrie();

        Assert.assertEquals(FastTrie.nullValue,trie.get(new byte[] { 0x01, 0x02, 0x03 }));
        Assert.assertEquals(FastTrie.nullValue,trie.get(fooKey));
    }

    int foo = 100;
    int bar = 200;
    byte[] fooKey = "foo".getBytes();
    byte[] barKey = "bar".getBytes();

    @Test
    public void putAndGetKeyValue() {
        FastTrie trie = new FastTrie();

        trie = trie.put(fooKey, bar);
        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));
    }

    @Test
    public void putAndGetKeyValueTwice() {
        FastTrie trie = new FastTrie();
        FastTrie trie1 = trie.put(fooKey, bar);
        FastTrie trie2 = trie1.put(fooKey, bar);
        Assert.assertTrue(0<=trie1.get(fooKey));
        Assert.assertEquals(bar, trie1.get(fooKey));
        Assert.assertTrue(0<=trie2.get(fooKey));
        Assert.assertEquals(bar, trie2.get(fooKey));
        Assert.assertSame(trie1, trie2);
    }
    @Test
    public void putAndGetKeyValueTwiceWithDifferenteValues() {
        FastTrie trie = new FastTrie();
        FastTrie trie1 = trie.put(fooKey, bar+1);
        FastTrie trie2 = trie1.put(fooKey, bar+2);
        Assert.assertTrue(0<=trie1.get(fooKey));
        Assert.assertEquals(bar+1, trie1.get(fooKey));
        Assert.assertTrue(0<=trie2.get(fooKey));
        Assert.assertEquals(bar+2, trie2.get(fooKey));
    }


    @Test
    public void putAndGetKeyLongValue() {
        FastTrie trie = new FastTrie();
        int value = 100;
        trie = trie.put(fooKey, value);
        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(value, trie.get(fooKey));
    }

    @Test
    public void putKeyValueAndDeleteKey() {
        FastTrie trie = new FastTrie();

        trie = trie.put(fooKey, bar).delete(fooKey);
        Assert.assertEquals(trie.get(fooKey),-1);
    }

    @Test
    public void putAndGetEmptyKeyValue() {
        FastTrie trie = new FastTrie();

        trie = trie.put("", bar);
        Assert.assertTrue(0<=trie.get(""));
        Assert.assertEquals(bar, trie.get(""));
    }

    @Test
    public void putAndGetEmptyKeyLongValue() {
        FastTrie trie = new FastTrie();
        int  value = 100;

        trie = trie.put("", value);
        Assert.assertTrue(0<=trie.get(""));
        Assert.assertEquals(value, trie.get(""));
    }

    @Test
    public void putAndGetTwoKeyValues() {
        FastTrie trie = new FastTrie();

        trie = trie.put(fooKey, bar);
        trie = trie.put(barKey, foo);

        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));

        Assert.assertTrue(0<=trie.get(barKey));
        Assert.assertEquals(foo, trie.get(barKey));
    }

    @Test
    public void putAndGetTwoKeyLongValues() {
        FastTrie trie = new FastTrie();
        int value1 = 100;
        int value2 = 200;

        trie = trie.put(fooKey, value1);
        trie = trie.put(barKey, value2);

        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(value1, trie.get(fooKey));

        Assert.assertTrue(0<=trie.get(barKey));
        Assert.assertEquals(value2, trie.get(barKey));
    }

    @Test
    public void putAndGetKeyAndSubKeyValues() {
        FastTrie trie = new FastTrie();

        trie = trie.put(fooKey, bar);
        trie = trie.put("f", 42);

        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));

        Assert.assertTrue(0<=trie.get("f"));
        Assert.assertEquals(42, trie.get("f"));
    }


    @Test
    public void putAndGetKeyAndSubKeyValuesInverse() {
        FastTrie trie = new FastTrie();

        trie = trie.put("f", 42)
                .put("fo", bar);

        Assert.assertTrue(0<=trie.get("fo"));
        Assert.assertEquals(bar, trie.get("fo"));

        Assert.assertTrue(0<=trie.get("f"));
        Assert.assertEquals(42, trie.get("f"));
    }


    @Test
    public void putAndGetOneHundredKeyValues() {
        FastTrie trie = new FastTrie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", k);

        for (int k = 0; k < 100; k++) {
            int expected = k;
            String key = k + "";
            int value = trie.get(key);
            Assert.assertEquals(key, value, expected);
        }
    }
    @Test
    public void deleteValueGivintEmptyTrie() {
        FastTrie trie = new FastTrie().put("key", 1);

        trie = trie.delete("key".getBytes());

        Assert.assertTrue( trie.isEmpty());
    }

    @Test
    public void deleteOneValueGivesTheSameHash() {
        FastTrie trie1 = new FastTrie()
                .put("key1", gvalue1)
                .put("key2", gvalue2)
                .delete("key1");

        FastTrie trie2 = new FastTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    int gvalue1 = 1001;
    int gvalue2 = 1002;

    @Test
    public void deleteOneLongValueGivesTheSameHash() {
        FastTrie trie1 = new FastTrie()
                .put("key1",1024)
                .put("key2", gvalue2)
                .delete("key1");

        FastTrie trie2 = new FastTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteOneValueTwiceGivesTheSameHash() {
        FastTrie trie1 = new FastTrie()
                .put("key1", gvalue1)
                .put("key2", gvalue2)
                .put("key2", gvalue2)
                .delete("key1");

        FastTrie trie2 = new FastTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteOneHundredValuesGivesTheSameHash() {
        FastTrie trie1 = new FastTrie();

        int valueBase = 1000;
        for (int k = 0; k < 200; k++)
            trie1 = trie1.put("key" + k, valueBase + k);

        for (int k = 1; k < 200; k += 2)
            trie1 = trie1.delete("key" + k);

        FastTrie trie2 = new FastTrie();

        for (int k = 0; k < 200; k += 2)
            trie2 = trie2.put("key" + k,valueBase  + k);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteTwoHundredValuesGivesTheEmptyHash() {
        FastTrie trie1 = new FastTrie();
        int valueBase = 1;

        for (int k = 0; k < 200; k++)
            trie1 = trie1.put("key" + k, valueBase + k);

        for (int k = 0; k < 200; k++)
            trie1 = trie1.delete("key" + k);

        Assert.assertTrue( trie1.isEmpty());
    }

}
