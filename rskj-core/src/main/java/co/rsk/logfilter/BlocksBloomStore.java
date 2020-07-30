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

import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.vm.DataWord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStore {
    private final int noBlocks;
    private final int noConfirmations;
    private final Map<Long, BlocksBloom> blocksBloom = new ConcurrentHashMap<>();
    private final KeyValueDataSource dataSource;

    public BlocksBloomStore(int noBlocks, int noConfirmations, KeyValueDataSource dataSource) {
        this.noBlocks = noBlocks;
        this.noConfirmations = noConfirmations;
        this.dataSource = dataSource;
    }

    public boolean hasBlockNumber(long blockNumber) {
        if (this.blocksBloom.containsKey(this.firstNumberInRange(blockNumber))) {
            return true;
        }

        if (this.dataSource != null && this.dataSource.get(longToKey(blockNumber)) != null) {
            return true;
        }

        return false;
    }

    public BlocksBloom getBlocksBloomByNumber(long number) {
        long key = firstNumberInRange(number);

        BlocksBloom blocksBloom = this.blocksBloom.get(key);

        if (blocksBloom != null) {
            return blocksBloom;
        }

        if (this.dataSource == null) {
            return null;
        }

        byte[] data = this.dataSource.get(longToKey(key));

        if (data == null) {
            return null;
        }

        blocksBloom = BlocksBloomEncoder.decode(data);

        this.blocksBloom.put(key, blocksBloom);

        return blocksBloom;
    }

    public void setBlocksBloom(BlocksBloom blocksBloom) {
        this.blocksBloom.put(blocksBloom.fromBlock(), blocksBloom);

        if (this.dataSource != null) {
            this.dataSource.put(longToKey(blocksBloom.fromBlock()), BlocksBloomEncoder.encode(blocksBloom));
        }
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

    public int getNoConfirmations() { return this.noConfirmations; }

    public static byte[] longToKey(long value) {
        if (value == 0) {
            return new byte[0];
        }

        return DataWord.valueOf(value).getByteArrayForStorage();
    }
}
