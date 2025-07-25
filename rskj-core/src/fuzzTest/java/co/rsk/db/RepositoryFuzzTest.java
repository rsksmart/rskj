/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;

class RepositoryFuzzTest {

    @Tag("RepositoryFuzzStorageRoot")
    @FuzzTest
    void testStorageRoot(FuzzedDataProvider data) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        MutableRepository repository = new MutableRepository(mutableTrie);

        byte[] bs = data.consumeBytes(20);
        RskAddress addr = new RskAddress(ByteUtil.leftPadBytes(bs, 20));
        repository.createAccount(addr);
        repository.setupContract(addr);

        HashMap<DataWord, byte[]> map = new HashMap();

        for (int i = 0; i < 120; i++) {
            DataWord key = DataWord.valueOf(data.consumeBytes(32));
            byte[] value = data.consumeBytes(32);
            repository.addStorageBytes(addr, key, value);
            map.put(key, value);
        }

        for (Map.Entry<DataWord, byte[]> entry : map.entrySet()) {
            byte[] storageBytes = repository.getStorageBytes(addr, entry.getKey());
            if (storageBytes == null) {
                Assertions.assertTrue(ByteUtil.isAllZeroes(entry.getValue()));
            } else {
                Assertions.assertArrayEquals(entry.getValue(), storageBytes);
            }
        }
    }
}
