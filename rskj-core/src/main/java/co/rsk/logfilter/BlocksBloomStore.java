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

package co.rsk.logfilter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStore {
    private final int noBlocks;
    private final Map<Long, BlocksBloom> blocksBloom = new ConcurrentHashMap<>();

    public BlocksBloomStore(int noBlocks) {
        this.noBlocks = noBlocks;
    }

    public BlocksBloom getBlocksBloomByNumber(long number) {
        return this.blocksBloom.get(firstNumberInRange(number));
    }

    public void setBlocksBloom(BlocksBloom blocksBloom) {
        this.blocksBloom.put(blocksBloom.fromBlock(), blocksBloom);
    }

    public long firstNumberInRange(long number) {
        return number - (number % this.noBlocks);
    }

    public long lastNumberInRange(long number) {
        return firstNumberInRange(number) + this.noBlocks - 1;
    }

    public int getNoBlocks() {
        return this.noBlocks;
    }
}
