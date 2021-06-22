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

import co.rsk.crypto.Keccak256;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.HashMap;
import java.util.Map;

public class StateRootTranslator {

    private final Map<Keccak256, Keccak256> stateRootCache = new HashMap<>();

    private final KeyValueDataSource stateRootDB;

    public StateRootTranslator(KeyValueDataSource stateRootDB) {
        this.stateRootDB = stateRootDB;
    }

    public synchronized Keccak256 get(Keccak256 oldStateRoot) {
        Keccak256 stateRoot = stateRootCache.get(oldStateRoot);

        if (stateRoot != null) {
            return stateRoot;
        }

        byte[] stateRootBytes = stateRootDB.get(oldStateRoot.getBytes());
        if (stateRootBytes == null) {
            return null;
        }

        Keccak256 newStateRoot = new Keccak256(stateRootBytes);
        stateRootCache.put(oldStateRoot, newStateRoot);

        return newStateRoot;
    }

    public synchronized void put(Keccak256 oldStateRoot, Keccak256 newStateRoot) {
        stateRootCache.put(oldStateRoot, newStateRoot);
        stateRootDB.put(oldStateRoot.getBytes(), newStateRoot.getBytes());
    }
}
