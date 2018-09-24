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


    }
}
