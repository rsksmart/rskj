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

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;

import java.math.BigInteger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 10.02.2015
 */
public class BlockStoreDummy implements BlockStore {

    @Override
    public Keccak256 getBlockHashByNumber(long blockNumber) {
        byte[] data = String.valueOf(blockNumber).getBytes(StandardCharsets.UTF_8);
        return new Keccak256(HashUtil.keccak256(data));
    }

    @Override
    public Keccak256 getBlockHashByNumber(long blockNumber, Keccak256 branchBlockHash) {
        return getBlockHashByNumber(blockNumber);
    }

    @Override
    public Block getChainBlockByNumber(long blockNumber) {
        return null;
    }

    @Override
    public Block getBlockByHash(Keccak256 hash) {
        return null;
    }

    @Override
    public Block getBlockByHashAndDepth(Keccak256 hash, long depth) {
        return null;
    }

    @Override
    public boolean isBlockExist(Keccak256 hash) {
        return false;
    }

    @Override
    public List<Keccak256> getListHashesEndWith(Keccak256 hash, long qty) {
        return null;
    }

    @Override
    public List<BlockHeader> getListHeadersEndWith(Keccak256 hash, long qty) {
        return null;
    }

    @Override
    public List<Block> getListBlocksEndWith(Keccak256 hash, long qty) {
        return null;
    }

    @Override
    public void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain) {

    }

    @Override
    public void removeBlock(Block block) {
        // unused
    }

    @Override
    public Block getBestBlock() {
        return null;
    }

    @Override
    public void flush() {
    }

    @Override
    public void load() {
    }

    @Override
    public long getMaxNumber() {
        return 0;
    }


    @Override
    public void reBranch(Block forkBlock) {

    }

    @Override
    public BigInteger getTotalDifficultyForHash(Keccak256 hash) {
        return null;
    }

    @Override
    public List<Block> getChainBlocksByNumber(long blockNumber) {
        return new ArrayList<>();
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long blockNumber) { return null; }
}
