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

package co.rsk.core.bc;

import org.ethereum.db.ByteArrayWrapper;

import java.util.HashSet;
import java.util.Set;

//TODO(JULI):
// * Next step should be to check whether a key is written in the cache but also deleted in the same transaction. This key shouldn't be considered as a written key.

public class ReadWrittenKeysTracker {
    private Set<ByteArrayWrapper> temporalReadKeys;
    private Set<ByteArrayWrapper> temporalWrittenKeys;

    public ReadWrittenKeysTracker() {
        this.temporalReadKeys = new HashSet<>();
        this.temporalWrittenKeys = new HashSet<>();
    }

    public Set<ByteArrayWrapper> getTemporalReadKeys(){
        return this.temporalReadKeys;
    }

    public Set<ByteArrayWrapper> getTemporalWrittenKeys(){
        return this.temporalWrittenKeys;
    }

    public void addNewReadKey(ByteArrayWrapper key) {
        temporalReadKeys.add(key);
    }

    public void addNewWrittenKey(ByteArrayWrapper key) {
        temporalWrittenKeys.add(key);
    }

    public void clear() {
        this.temporalReadKeys = new HashSet<>();
        this.temporalWrittenKeys = new HashSet<>();

    }
}
