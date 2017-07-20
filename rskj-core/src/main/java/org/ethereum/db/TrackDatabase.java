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

package org.ethereum.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 11.06.2014
 */
public class TrackDatabase implements Database {

    private Database db;

    private boolean trackingChanges;
    private Map<ByteArrayWrapper, byte[]> changes;
    private List<ByteArrayWrapper> deletes;

    public TrackDatabase(Database db) {
        this.db = db;
    }

    public void startTrack() {
        changes = new HashMap<>();
        deletes = new ArrayList<>();
        trackingChanges = true;
    }

    public void commitTrack() {
        for (ByteArrayWrapper key : changes.keySet()) {
            db.put(key.getData(), changes.get(key));
        }
        for (ByteArrayWrapper key : deletes) {
            db.delete(key.getData());
        }
        changes = null;
        trackingChanges = false;
    }

    public void rollbackTrack() {
        changes = new HashMap<>();
        deletes = new ArrayList<>();
        changes = null;
        trackingChanges = false;
    }

    public void put(byte[] key, byte[] value) {
        if (trackingChanges) {
            ByteArrayWrapper wKey = new ByteArrayWrapper(key);
            changes.put(wKey, value);
        } else {
            db.put(key, value);
        }
    }

    public byte[] get(byte[] key) {
        if (trackingChanges) {
            ByteArrayWrapper wKey = new ByteArrayWrapper(key);
            if (deletes.contains(wKey)) {
                return null;
            }
            if (changes.get(wKey) != null) {
                return changes.get(wKey);
            }
        }
        return db.get(key);
    }

    /**
     * Delete object (key) from db *
     */
    public void delete(byte[] key) {
        if (trackingChanges) {
            ByteArrayWrapper wKey = new ByteArrayWrapper(key);
            deletes.add(wKey);
        } else {
            db.delete(key);
        }
    }

    @Override
    public void close() {
        db.close();
    }

    @Override
    public void init() {

    }
}
