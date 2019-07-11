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

package org.ethereum.db;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.util.Optional;

public interface BlockHeaderStore {
    /**
     * Retrieves a block header by its hash.
     *
     * @param hash The hash of the block to look up, cannot be null.
     * @return An optional containing the block header if found, empty if not.
     */
    Optional<BlockHeader> getBlockHeaderByHash(@Nonnull Keccak256 hash);

    /**
     * Saves a block header to the block storage.
     *
     * @param blockHeader The block header to save, cannot be null.
     */
    void saveBlockHeader(@Nonnull BlockHeader blockHeader);
}
