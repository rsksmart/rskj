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

import co.rsk.core.commons.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 08.01.2015
 */
public interface BlockStore {

    Keccak256 getBlockHashByNumber(long blockNumber);

    /**
     * Gets the block hash by its index.
     * When more than one block with the specified index exists (forks)
     * the select the block which is ancestor of the branchBlockHash
     */
    Keccak256 getBlockHashByNumber(long blockNumber, Keccak256 branchBlockHash);

    Block getChainBlockByNumber(long blockNumber);

    @Nonnull
    List<Block> getChainBlocksByNumber(long blockNumber);

    void removeBlock(Block block);

    Block getBlockByHash(Keccak256 hash);

    Block getBlockByHashAndDepth(Keccak256 hash, long depth);

    boolean isBlockExist(Keccak256 hash);

    List<Keccak256> getListHashesEndWith(Keccak256 hash, long qty);

    List<BlockHeader> getListHeadersEndWith(Keccak256 hash, long qty);

    List<Block> getListBlocksEndWith(Keccak256 hash, long qty);

    void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain);

    BigInteger getTotalDifficultyForHash(Keccak256 hash);

    Block getBestBlock();

    long getMaxNumber();

    void flush();

    void reBranch(Block forkBlock);

    void load();

    List<BlockInformation> getBlocksInformationByNumber(long number);
}
