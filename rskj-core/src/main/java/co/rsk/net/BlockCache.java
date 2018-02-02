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

import org.ethereum.core.Block;
import org.ethereum.db.ByteArrayWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache {
    private final LinkedHashMap<ByteArrayWrapper, Block> linkedHashMap;

    public BlockCache(int cacheSize) {
        this.linkedHashMap = new LinkedHashMap<ByteArrayWrapper, Block>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ByteArrayWrapper, Block> eldest) {
                return size() > cacheSize;
            }
        };
    }

    public void removeBlock(Block block) {
        ByteArrayWrapper key = block.getWrappedHash();

        linkedHashMap.remove(key);
    }

    public void addBlock(Block block) {
        ByteArrayWrapper key = block.getWrappedHash();

        linkedHashMap.put(key, block);
    }

    public Block getBlockByHash(byte[] hash) {
        ByteArrayWrapper key = new ByteArrayWrapper(hash);

        return linkedHashMap.get(key);
    }

    public Block put(ByteArrayWrapper key, Block block) {
        return linkedHashMap.put(key, block);
    }
}
