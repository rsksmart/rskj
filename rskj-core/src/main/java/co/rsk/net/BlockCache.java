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

import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache {
    private final Map<Keccak256, Block> blockMap;

    public BlockCache(int cacheSize) {
        this.blockMap = new MaxSizeHashMap<>(cacheSize, true);
    }

    public void removeBlock(Block block) {
        blockMap.remove(block.getHash());
    }

    public void addBlock(Block block) {
        blockMap.put(block.getHash(), block);
    }

    public Block getBlockByHash(byte[] hash) {
        return blockMap.get(new Keccak256(hash));
    }
}
