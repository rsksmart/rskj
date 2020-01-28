/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core.bc;

import org.ethereum.core.Block;

import java.util.Collections;
import java.util.List;

/**
 * Created by ajlopez on 09/08/2016.
 */
public class BlockFork {
    private final Block commonAncestor;
    private final List<Block> oldBlocks;
    private final List<Block> newBlocks;

    public BlockFork(Block commonAncestor, List<Block> oldBlocks, List<Block> newBlocks) {
        this.commonAncestor = commonAncestor;
        this.oldBlocks = Collections.unmodifiableList(oldBlocks);
        this.newBlocks = Collections.unmodifiableList(newBlocks);
    }

    /**
     * getCommonAncestor gets the common ancestor of the two chains.
     * @return highest block such that is an ancestor of all the blocks in oldBlocks and in newBlocks.
     */
    public Block getCommonAncestor() {
        return commonAncestor;
    }

    /**
     * getOldBlocks returns the blocks from the old chain, not including the common ancestor.
     * if oldBlock is the common ancestor, it won't be included in oldBlocks.
     * @return If oldBlock is the commonAncestor: the empty list. Otherwise, it will return a list containing all chain
     * from commonAncestor (not included) to oldBlock (included), in ascending number order.
     */
    public List<Block> getOldBlocks() {
        return oldBlocks;
    }

    /**
     * getNewBlocks returns the blocks from the new chain, not including the common ancestor.
     * if newBlock is the common ancestor, it won't be included in newBlocks.
     * @return If newBlock is the commonAncestor: the empty list. Otherwise, it will return a list containing all chain
     * from commonAncestor (not included) to newBlock (included), in ascending number order.
     */
    public List<Block> getNewBlocks() {
        return newBlocks;
    }
}
