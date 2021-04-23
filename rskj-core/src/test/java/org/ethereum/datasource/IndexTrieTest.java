package org.ethereum.datasource;

import org.junit.Assert;
import org.junit.Test;

public class IndexTrieTest {
    @Test
    public void noLongValueInEmptyTrie() {
        IndexTrie trie = new IndexTrie();

        Assert.assertTrue(trie.getValue()<0);
    }

    @Test
    public void noLongValueInTrieWithShortValue() {
        byte[] key = new byte[] { 0x04, 0x05 };
        int value =100;
        IndexTrie trie = new IndexTrie().put(key, value);
        Assert.assertEquals(value, trie.getValue());
    }

    @Test
    public void noWithoutPath() {
        byte[] key1 = new byte[] { 0x00};
        byte[] key2 = new byte[] { (byte) 0x80};
        int value1 =100;
        int value2 =101;
        IndexTrie trie = new IndexTrie().put(key1, value1).put(key2,value2);

        Assert.assertEquals(IndexTrie.nullValue, trie.getValue());
        Assert.assertEquals(0, trie.getSharedPath().length());

    }

    // Key tests
    @Test
    public void getNullForUnknownKey() {
        IndexTrie trie = new IndexTrie();

        Assert.assertEquals(IndexTrie.nullValue,trie.get(new byte[] { 0x01, 0x02, 0x03 }));
        Assert.assertEquals(IndexTrie.nullValue,trie.get(fooKey));
    }

    int foo = 100;
    int bar = 200;
    byte[] fooKey = "foo".getBytes();
    byte[] barKey = "bar".getBytes();

    @Test
    public void putAndGetKeyValue() {
        IndexTrie trie = new IndexTrie();

        trie = trie.put(fooKey, bar);
        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));
    }

    @Test
    public void putAndGetKeyValueTwice() {
        IndexTrie trie = new IndexTrie();
        IndexTrie trie1 = trie.put(fooKey, bar);
        IndexTrie trie2 = trie1.put(fooKey, bar);
        Assert.assertTrue(0<=trie1.get(fooKey));
        Assert.assertEquals(bar, trie1.get(fooKey));
        Assert.assertTrue(0<=trie2.get(fooKey));
        Assert.assertEquals(bar, trie2.get(fooKey));
        Assert.assertSame(trie1, trie2);
    }
    @Test
    public void putAndGetKeyValueTwiceWithDifferenteValues() {
        IndexTrie trie = new IndexTrie();
        IndexTrie trie1 = trie.put(fooKey, bar+1);
        IndexTrie trie2 = trie1.put(fooKey, bar+2);
        Assert.assertTrue(0<=trie1.get(fooKey));
        Assert.assertEquals(bar+1, trie1.get(fooKey));
        Assert.assertTrue(0<=trie2.get(fooKey));
        Assert.assertEquals(bar+2, trie2.get(fooKey));
    }


    @Test
    public void putAndGetKeyLongValue() {
        IndexTrie trie = new IndexTrie();
        int value = 100;
        trie = trie.put(fooKey, value);
        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(value, trie.get(fooKey));
    }

    @Test
    public void putKeyValueAndDeleteKey() {
        IndexTrie trie = new IndexTrie();

        trie = trie.put(fooKey, bar).delete(fooKey);
        Assert.assertEquals(trie.get(fooKey),-1);
    }

    @Test
    public void putAndGetEmptyKeyValue() {
        IndexTrie trie = new IndexTrie();

        trie = trie.put("", bar);
        Assert.assertTrue(0<=trie.get(""));
        Assert.assertEquals(bar, trie.get(""));
    }

    @Test
    public void putAndGetEmptyKeyLongValue() {
        IndexTrie trie = new IndexTrie();
        int  value = 100;

        trie = trie.put("", value);
        Assert.assertTrue(0<=trie.get(""));
        Assert.assertEquals(value, trie.get(""));
    }

    @Test
    public void putAndGetTwoKeyValues() {
        IndexTrie trie = new IndexTrie();

        trie = trie.put(fooKey, bar);
        trie = trie.put(barKey, foo);

        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));

        Assert.assertTrue(0<=trie.get(barKey));
        Assert.assertEquals(foo, trie.get(barKey));
    }

    @Test
    public void putAndGetTwoKeyLongValues() {
        IndexTrie trie = new IndexTrie();
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
        IndexTrie trie = new IndexTrie();

        trie = trie.put(fooKey, bar);
        trie = trie.put("f", 42);

        Assert.assertTrue(0<=trie.get(fooKey));
        Assert.assertEquals(bar, trie.get(fooKey));

        Assert.assertTrue(0<=trie.get("f"));
        Assert.assertEquals(42, trie.get("f"));
    }


    @Test
    public void putAndGetKeyAndSubKeyValuesInverse() {
        IndexTrie trie = new IndexTrie();

        trie = trie.put("f", 42)
                .put("fo", bar);

        Assert.assertTrue(0<=trie.get("fo"));
        Assert.assertEquals(bar, trie.get("fo"));

        Assert.assertTrue(0<=trie.get("f"));
        Assert.assertEquals(42, trie.get("f"));
    }


    @Test
    public void putAndGetOneHundredKeyValues() {
        IndexTrie trie = new IndexTrie();

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
        IndexTrie trie = new IndexTrie().put("key", 1);

        trie = trie.delete("key".getBytes());

        Assert.assertTrue( trie.isEmpty());
    }

    @Test
    public void deleteOneValueGivesTheSameHash() {
        IndexTrie trie1 = new IndexTrie()
                .put("key1", gvalue1)
                .put("key2", gvalue2)
                .delete("key1");

        IndexTrie trie2 = new IndexTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    int gvalue1 = 1001;
    int gvalue2 = 1002;

    @Test
    public void deleteOneLongValueGivesTheSameHash() {
        IndexTrie trie1 = new IndexTrie()
                .put("key1",1024)
                .put("key2", gvalue2)
                .delete("key1");

        IndexTrie trie2 = new IndexTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteOneValueTwiceGivesTheSameHash() {
        IndexTrie trie1 = new IndexTrie()
                .put("key1", gvalue1)
                .put("key2", gvalue2)
                .put("key2", gvalue2)
                .delete("key1");

        IndexTrie trie2 = new IndexTrie().put("key2", gvalue2);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteOneHundredValuesGivesTheSameHash() {
        IndexTrie trie1 = new IndexTrie();

        int valueBase = 1000;
        for (int k = 0; k < 200; k++)
            trie1 = trie1.put("key" + k, valueBase + k);

        for (int k = 1; k < 200; k += 2)
            trie1 = trie1.delete("key" + k);

        IndexTrie trie2 = new IndexTrie();

        for (int k = 0; k < 200; k += 2)
            trie2 = trie2.put("key" + k,valueBase  + k);

        Assert.assertTrue(trie1.equals(trie2));
    }

    @Test
    public void deleteTwoHundredValuesGivesTheEmptyHash() {
        IndexTrie trie1 = new IndexTrie();
        int valueBase = 1;

        for (int k = 0; k < 200; k++)
            trie1 = trie1.put("key" + k, valueBase + k);

        for (int k = 0; k < 200; k++)
            trie1 = trie1.delete("key" + k);

        Assert.assertTrue( trie1.isEmpty());
    }

}
