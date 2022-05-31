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

import co.rsk.core.BlockDifficulty;
import co.rsk.db.RemascCache;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 08.01.2015
 */
public interface BlockStore extends RemascCache {

    /**
     * Gets the block hash by its index.
     * When more than one block with the specified index exists (forks)
     * the select the block which is ancestor of the branchBlockHash
     */
    byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash);

    Block getChainBlockByNumber(long blockNumber);

    @Nonnull
    List<Block> getChainBlocksByNumber(long blockNumber);

    void removeBlock(Block block);

    Block getBlockByHash(byte[] hash);

    boolean hasBlockByHash(byte[] hash);

    Block getBlockAtDepthStartingAt(long depth, byte[] hash);

    boolean isBlockExist(byte[] hash);

    List<byte[]> getListHashesEndWith(byte[] hash, long qty);

    void saveBlock(Block block, BlockDifficulty cummDifficulty, boolean mainChain);

    BlockDifficulty getTotalDifficultyForHash(byte[] hash);

    Block getBestBlock();

    /**
     * @return The highest block number stored.
     * @throws IllegalStateException if the blockstore is empty.
     */
    long getMaxNumber();

    /**
     * @return The smallest block number stored.
     * @throws IllegalStateException if the blockstore is empty.
     */
    long getMinNumber();

    void flush();

    void reBranch(Block forkBlock);

    List<BlockInformation> getBlocksInformationByNumber(long number);

    boolean isEmpty();

    void close();

    Bloom bloomByBlockNumber(long blockNumber);

    void rewind(long blockNumber);

    long getBlockNumber(byte[] hash);
}
