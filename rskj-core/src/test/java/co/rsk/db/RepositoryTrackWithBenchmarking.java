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
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;

public class RepositoryTrackWithBenchmarking extends MutableRepository {
    public class Statistics {
        private final int SLOT_SIZE_BYTES = 32;
        private int slotsWritten;
        private int slotsCleared;

        public Statistics() {
            clear();
        }

        public void clear() {
            slotsWritten = 0;
            slotsCleared = 0;
        }

        public void recordWrite(byte[] oldValue, byte[] newValue) {
            int oldValueLength = oldValue == null ? 0 : oldValue.length;
            int newValueLength = newValue == null ? 0 : newValue.length;
            int delta = newValueLength - oldValueLength;
            int slots = (int) Math.ceil((double)Math.abs(delta) / (double)SLOT_SIZE_BYTES);
            if (delta > 0) {
                slotsWritten += slots;
            } else {
                slotsCleared += slots;
            }
        }

        public int getSlotsWritten() {
            return slotsWritten;
        }

        public int getSlotsCleared() {
            return slotsCleared;
        }
    }

    private final Statistics statistics;

    public RepositoryTrackWithBenchmarking(Repository repository) {
        super(new MutableTrieCache(repository.getMutableTrie()));
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
