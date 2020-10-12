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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStore {
    private static final Logger logger = LoggerFactory.getLogger("blooms");

    private final int noBlocks;
    private final int noConfirmations;
    private final Map<Long, BlocksBloom> blocksBloomCache = new ConcurrentHashMap<>();
    private final KeyValueDataSource dataSource;

    public BlocksBloomStore(int noBlocks, int noConfirmations, KeyValueDataSource dataSource) {
        this.noBlocks = noBlocks;
        this.noConfirmations = noConfirmations;
        this.dataSource = dataSource;
    }

    public synchronized boolean hasBlockNumber(long blockNumber) {
        long key = this.firstNumberInRange(blockNumber);

        return hasBlockNumberInCache(key)
            || hasBlockNumberInStore(key);
    }

    private boolean hasBlockNumberInStore(long key) {
        return this.dataSource != null && this.dataSource.get(longToKey(key)) != null;
    }

    private boolean hasBlockNumberInCache(long key) {
        return this.blocksBloomCache.containsKey(key);
    }

    public synchronized BlocksBloom getBlocksBloomByNumber(long number) {
        long key = firstNumberInRange(number);

        BlocksBloom blocksBloom = this.blocksBloomCache.get(key);

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

        this.blocksBloomCache.put(key, blocksBloom);

        return blocksBloom;
    }

    public synchronized void addBlocksBloom(BlocksBloom blocksBloom) {
        logger.trace("set blocks bloom: height {}", blocksBloom.fromBlock());

        this.blocksBloomCache.put(blocksBloom.fromBlock(), blocksBloom);

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

    public void flush() {
        this.dataSource.flush();
    }

    public void close() {
        this.dataSource.close();
    }
}
