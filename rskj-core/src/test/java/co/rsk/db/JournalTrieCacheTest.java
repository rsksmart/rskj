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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

public class JournalTrieCacheTest {

    private byte[] toBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    private String getKeysFrom(MutableTrie mt) {
        return setToString(mt.collectKeys(Integer.MAX_VALUE));
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

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testPuts() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));

        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;",result);


        baseMutableTrie.put("BOB",toBytes("bob"));

        JournalTrieCache mtCache;
        mtCache = new JournalTrieCache(baseMutableTrie);

        // Now add two more
        mtCache.put("CAROL",toBytes("carol"));
        mtCache.put("ROBERT",toBytes("robert"));

        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;",result);

        result = getKeysFrom(mtCache);

        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        mtCache.commit();

        // Now the base trie must have all
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
    }
}