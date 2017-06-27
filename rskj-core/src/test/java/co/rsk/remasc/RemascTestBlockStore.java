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

package co.rsk.remasc;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.AbstractBlockstore;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.ByteArrayWrapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.BigInteger.ZERO;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * Created by oscar and mario on 13/12/2016.
 */
public class RemascTestBlockStore extends AbstractBlockstore {

    Map<ByteArrayWrapper, Block> hashIndex = new HashMap<>();
    Map<Long, Block> numberIndex = new HashMap<>();
    List<Block> blocks = new ArrayList<>();
    BigInteger totalDifficulty = ZERO;

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block getChainBlockByNumber(long blockNumber) {
        return numberIndex.get(blockNumber);
    }

    @Override
    public List<Block> getChainBlocksByNumber(long blockNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return hashIndex.get(wrap(hash));
    }

    @Override
    public boolean isBlockExist(byte[] hash) {
        Block block = hashIndex.get(wrap(hash));
        return block != null;
    }

    @Override
    public List<byte[]> getListHashesEndWith(byte[] hash, long qty) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Block> getListBlocksEndWith(byte[] hash, long qty) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain) {
        ByteArrayWrapper wHash = wrap(block.getHash());
        blocks.add(block);
        hashIndex.put(wHash, block);
        numberIndex.put(block.getNumber(), block);
        totalDifficulty = totalDifficulty.add(block.getCumulativeDifficulty());
    }

    @Override
    public BigInteger getTotalDifficultyForHash(byte[] hash) {
        return getBlockByHash(hash).getCumulativeDifficulty();
    }

    @Override
    public Block getBestBlock() {
        if (blocks.size() == 0) return null;
        return blocks.get(blocks.size() - 1);
    }

    @Override
    public long getMaxNumber() {
        long maxNumber = 0;
        for (Long number : numberIndex.keySet()) {
            if (number>maxNumber) maxNumber = number;
        }
        return maxNumber;
    }

    @Override
    public void flush() {
        // throw new UnsupportedOperationException();
    }

    @Override
    public void reBranch(Block forkBlock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void load() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeBlock(Block block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long blockNumber) { return null; }
}
