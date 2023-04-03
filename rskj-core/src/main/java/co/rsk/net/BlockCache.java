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

package co.rsk.net;

import co.rsk.crypto.Keccak256;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache {
    private final Map<Keccak256, Block> frequentBlockMap;
    private final Map<Keccak256, Block> recentBlockMap;
    private final int cacheSize;

    private static final Logger logger = LoggerFactory.getLogger("testiago");

    public BlockCache(int cacheSize) {
        this.cacheSize = cacheSize;
        this.frequentBlockMap = new MaxSizeHashMap<>(this.cacheSize, true);
        this.recentBlockMap = new MaxSizeHashMap<>(this.cacheSize, false);
    }

    public synchronized void removeBlock(Block block) {
        frequentBlockMap.remove(block.getHash());
        recentBlockMap.remove(block.getHash());
    }

    public synchronized void addBlock(Block block, long bestBlockNumber) {
        boolean isRecent = this.cacheSize > bestBlockNumber - block.getNumber();
        if (isRecent) {
            recentBlockMap.put(block.getHash(), block);
        } else {
            frequentBlockMap.put(block.getHash(), block);
        }
    }

    public synchronized Block getBlockByHash(byte[] hash) {
        Block blockFromRecent = recentBlockMap.get(new Keccak256(hash));
        if (blockFromRecent != null) {
            logger.info("======= block {} found on recent with size {} ", blockFromRecent.getNumber(), recentBlockMap.size());
        }

        Block blockFromFrequent = frequentBlockMap.get(new Keccak256(hash));
        if (blockFromFrequent != null) {
            logger.info("======= block {} found on frequent with size {} ", blockFromFrequent.getNumber(), frequentBlockMap.size());
        }

        if (blockFromRecent == null && blockFromFrequent == null) {
            logger.info("======= block not found");
        }

        Block block = recentBlockMap.get(new Keccak256(hash));
        if (block != null) {
            return block;
        }

        return frequentBlockMap.get(new Keccak256(hash));
    }
}
