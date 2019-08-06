/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;

public class RepositoryTrackWithBenchmarking extends MutableRepository implements BenchmarkedRepository {
    protected final Statistics statistics;

    public RepositoryTrackWithBenchmarking(TrieStore trieStore, Trie trie) {
        super(new MutableTrieCache(new MutableTrieImpl(trieStore, trie)));
        statistics = new Statistics();
    }

    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        byte[] oldValue = getStorageBytes(addr, key);
        statistics.recordWrite(oldValue, value);

        super.addStorageBytes(addr, key, value);
    }
}
