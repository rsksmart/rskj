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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;

/**
 * {@link StateRootsStore} is a storage with mappings between a block's state root hash and a trie hash.
 */
public interface StateRootsStore extends Closeable {

    /**
     * Returns a hash of a trie for some particular block's state root.
     * {@code null} will be returned, if there's no mapping in the store.
     */
    @Nullable
    Keccak256 get(@Nonnull byte[] blockStateRoot);

    /**
     * Allows to put a block's state root hash to a trie hash mapping into the store.
     */
    void put(@Nonnull byte[] blockStateRoot, @Nonnull Keccak256 trieHash);

    /**
     * Allows to flush data to a disk, if an implementation supports it.
     */
    void flush();

    /**
     * Allows to close an underlying storage. This store cannot be used afterwards.
     */
    void close();

}
