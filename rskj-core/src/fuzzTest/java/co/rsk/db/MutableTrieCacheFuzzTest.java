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

import co.rsk.trie.Trie;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

/**
 * Created by SerAdmin on 9/26/2018.
 */
class MutableTrieCacheFuzzTest {

    @Tag("MutableTrieCacheFuzzPuts")
    @FuzzTest
    void testPuts(FuzzedDataProvider data) {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        for (int i = 0; i < 2000; i++) {
            String key = data.consumeString(256);
            byte[] bs = data.consumeBytes(256);
            baseMutableTrie.put(key, bs);
        }
    }
}
