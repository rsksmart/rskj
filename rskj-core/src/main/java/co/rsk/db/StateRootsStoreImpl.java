/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class StateRootsStoreImpl implements StateRootsStore {

    private final KeyValueDataSource stateRootsDB;

    public StateRootsStoreImpl(@Nonnull KeyValueDataSource stateRootsDB) {
        this.stateRootsDB = Objects.requireNonNull(stateRootsDB);
    }

    @Nullable
    @Override
    public Keccak256 get(@Nonnull byte[] blockStateRoot) {
        byte[] trieHash = stateRootsDB.get(blockStateRoot);
        if (trieHash == null) {
            return null;
        }

        return new Keccak256(trieHash);
    }

    @Override
    public void put(@Nonnull byte[] blockStateRoot, @Nonnull Keccak256 trieHash) {
        stateRootsDB.put(blockStateRoot, trieHash.getBytes());
    }

    @Override
    public void flush() {
        stateRootsDB.flush();
    }

    @Override
    public void close() {
        stateRootsDB.close();
    }
}
