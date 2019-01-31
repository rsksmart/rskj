/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db;

import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
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
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new Trie());

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

    @Test
    public void testAccountBehavior(){
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.DOMAIN_PREFIX.length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");
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
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.DOMAIN_PREFIX.length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");
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