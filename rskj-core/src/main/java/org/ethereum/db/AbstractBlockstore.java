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

/**
 * Created by Anton Nashatyrev on 29.10.2015.
 */
public abstract class AbstractBlockstore implements BlockStore {

    @Override
    public Keccak256 getBlockHashByNumber(long blockNumber, Keccak256 branchBlockHash) {
        Block branchBlock = getBlockByHash(branchBlockHash);
        if (branchBlock.getNumber() < blockNumber) {
            throw new IllegalArgumentException("Requested block number > branch hash number: " + blockNumber + " < " + branchBlock.getNumber());
        }
        while(branchBlock.getNumber() > blockNumber) {
            branchBlock = getBlockByHash(branchBlock.getParentHash());
        }
        return branchBlock.getHash();
    }

    @Override
    public Block getBlockByHashAndDepth(Keccak256 hash, long depth) {
        Block block = this.getBlockByHash(hash);

        for (long i = 0; i < depth; i++) {
            block = this.getBlockByHash(block.getParentHash());
        }

        return block;
    }
}
