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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.TransactionInfo;

import javax.annotation.Nullable;
import java.util.List;

public interface Blockchain {

    /**
     * Get block by number from the best chain
     * @param number - number of the block
     * @return block by that number
     */
    Block getBlockByNumber(long number);

    /**
     * Get block by hash
     * @param hash - hash of the block
     * @return - bloc by that hash
     */
    Block getBlockByHash(byte[] hash);

    /**
     * Get total difficulty from the start
     * and until the head of the chain
     *
     * @return - total difficulty
     */
    BlockDifficulty getTotalDifficulty();

    /**
     * @return - last added block from blockchain
     */
    Block getBestBlock();

    long getSize();

    ImportResult tryToConnect(Block block);

    void setStatus(Block block, BlockDifficulty totalDifficulty);

    BlockChainStatus getStatus();

    @Nullable
    TransactionInfo getTransactionInfo(byte[] hash);

    @Nullable
    TransactionInfo getTransactionInfoForMainChainBlock(Transaction tx, Block blockMainChain);

    byte[] getBestBlockHash();

    List<Block> getBlocksByNumber(long blockNr);

    void removeBlocksByNumber(long blockNr);

    List<BlockInformation> getBlocksInformationByNumber(long number);

    boolean hasBlockInSomeBlockchain(byte[] hash);
}
