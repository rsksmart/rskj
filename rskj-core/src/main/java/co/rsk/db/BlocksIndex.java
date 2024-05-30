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
import org.ethereum.db.IndexedBlockStore;

import java.util.List;

/**
 * BlocksIndex handles block accesses by their block number.
 * <p>
 * Many different blocks might contain the same block number but only one of the belongs to the main chain.
 * (The one with the most difficulty)
 * <p>
 * The blocks themselves are not stored, only the smallest information required to access them by hash.
 */
//TODO(im): Better public methods, this storage should enforce its own invariants.
public interface BlocksIndex {

    /**
     * @return True iif the index is empty.
     */
    boolean isEmpty();

    /**
     * Retrieves the max block number stored in the index. It might not belong to the main chain.
     * @return The max block number if it exists.
     * @throws IllegalStateException if the blockstore is empty.
     */
    long getMaxNumber();

    /**
     * Retrieves the min block number stored in the index. It should always belong to the main chain.
     * @return The min block number.
     * @throws IllegalStateException if the blockstore is empty.
     */
    long getMinNumber();

    /**
     * Checks if a block number was stored previously.
     *
     * @return True iif there's at least one block stored with that number.
     */
    boolean contains(long blockNumber);

    /**
     * Retrieves a list in no particular order of the stored blocks that go by that number.
     *
     * @return A list containing the found blocks.
     */
    List<IndexedBlockStore.BlockInfo> getBlocksByNumber(long blockNumber);

    /**
     * Stores a list of blocks by their number, overwriting the previous existing ones.
     *
     * @param blocks A non null, non empty list of blocks.
     */
    void putBlocks(long blockNumber, List<IndexedBlockStore.BlockInfo> blocks);

    /**
     * Removes the blocks with the highest block number from the storage.
     *
     * @return The removed blocks.
     */
    List<IndexedBlockStore.BlockInfo> removeLast();

    /**
     * Commits the changes to the underlying permanent storage.
     */
    void flush();

    void close();

    /**
     * Removes the block with the given number and hash from the storage.
     *
     * @param blockNumber The block number.
     * @param blockHash The block hash.
     */
    void removeBlock(long blockNumber, Keccak256 blockHash);
}
