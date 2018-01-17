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

import co.rsk.crypto.Sha3Hash;
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

    Sha3Hash getBlockHashByNumber(long blockNumber);

    /**
     * Gets the block hash by its index.
     * When more than one block with the specified index exists (forks)
     * the select the block which is ancestor of the branchBlockHash
     */
    Sha3Hash getBlockHashByNumber(long blockNumber, Sha3Hash branchBlockHash);

    Block getChainBlockByNumber(long blockNumber);

    @Nonnull
    List<Block> getChainBlocksByNumber(long blockNumber);

    void removeBlock(Block block);

    Block getBlockByHash(Sha3Hash hash);

    Block getBlockByHashAndDepth(Sha3Hash hash, long depth);

    boolean isBlockExist(Sha3Hash hash);

    List<Sha3Hash> getListHashesEndWith(Sha3Hash hash, long qty);

    List<BlockHeader> getListHeadersEndWith(Sha3Hash hash, long qty);

    List<Block> getListBlocksEndWith(Sha3Hash hash, long qty);

    void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain);

    BigInteger getTotalDifficultyForHash(Sha3Hash hash);

    Block getBestBlock();

    long getMaxNumber();

    void flush();

    void reBranch(Block forkBlock);

    void load();

    List<BlockInformation> getBlocksInformationByNumber(long number);
}
