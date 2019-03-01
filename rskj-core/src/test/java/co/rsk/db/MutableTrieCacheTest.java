package co.rsk.db;

import co.rsk.trie.MutableTrie;
import co.rsk.trie.TrieImpl;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created by SerAdmin on 9/26/2018.
 */
public class MutableTrieCacheTest {

    private byte[] toBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    private String setToString(Set<ByteArrayWrapper> set) {
        String r ="";
        ArrayList<String> list = new ArrayList<>();

        for (ByteArrayWrapper item : set) {
            list.add(new String(item.getData(), StandardCharsets.UTF_8));

        }
        Collections.sort(list);
        for (String s : list ) {
            r = r+s+";";
        }

        return r;
    }

    private String getKeysFrom(MutableTrie mt) {
        return setToString(mt.collectKeys(Integer.MAX_VALUE));
    }

    @Test
    public void testPuts() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new TrieImpl(false));

        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));

        String result;
        result = getKeysFrom(baseMutableTrie);
        Assert.assertEquals("ALICE;",result);


        baseMutableTrie.put("BOB",toBytes("bob"));

        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // Now add two more
        mtCache.put("CAROL",toBytes("carol"));
        mtCache.put("ROBERT",toBytes("robert"));

        result = getKeysFrom(baseMutableTrie);
        Assert.assertEquals("ALICE;BOB;",result);

        result = getKeysFrom(mtCache);

        Assert.assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        mtCache.commit();

        // Now the base trie must have all
        result = getKeysFrom(baseMutableTrie);
        Assert.assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        mtCache.delete(toBytes("CAROL"));
        Assert.assertNull(mtCache.get(toBytes("CAROL")));
    }

    @Test
    public void testAccountBehavior(){
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new TrieImpl(false));
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        for (int i = 0; i < 30; i++) accountLikeKey.append("0");
        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        mtCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString())));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));

        // if a key is inserted after a recursive delete is visible
        mtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString())));

        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
    }

    @Test
    public void testNestedCaches() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new TrieImpl(false));
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        for (int i = 0; i < 30; i++) accountLikeKey.append("0");
        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        mtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        mtCache.deleteRecursive(toBytes(accountLikeKey.toString()));

        // after commit puts on superior levels are reflected on lower levels
        MutableTrieCache otherCache = new MutableTrieCache(mtCache);
        otherCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        Assert.assertNotNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        otherCache.commit();
        Assert.assertNotNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));

        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        otherCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "125")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString())));

        // before commit lower level cache is not affected
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        Assert.assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        Assert.assertNull(mtCache.get(toBytes(accountLikeKey.toString())));

        otherCache.commit();
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "125")));
        Assert.assertNull(otherCache.get(toBytes(accountLikeKey.toString())));
    }
}
