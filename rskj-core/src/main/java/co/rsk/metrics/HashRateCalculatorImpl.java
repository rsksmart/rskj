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

package co.rsk.metrics;

import co.rsk.util.AccountUtils;
import co.rsk.util.RskCustomCache;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Created by mario on 05/08/2016.
 */
public class HashRateCalculatorImpl implements HashRateCalculator {

    private BlockStore blockStore;

    private RskCustomCache<ByteArrayWrapper, BlockHeaderElement> headerCache;

    private final byte[] address;

    public HashRateCalculatorImpl(BlockStore blockStore, AccountUtils accountUtils, RskCustomCache<ByteArrayWrapper, BlockHeaderElement> headerCache) {
        this.blockStore = blockStore;
        this.headerCache = headerCache;
        this.address = accountUtils.getCoinbaseAddress();
    }

    @Override
    public BigInteger calculateNodeHashRate(Long periodLenght, TimeUnit periodUnit) {
        return calculateHashRate(b -> checkOwnership(b), periodUnit.toSeconds(periodLenght));
    }

    @Override
    public BigInteger calculateNetHashRate(Long periodLenght, TimeUnit periodUnit) {
        return calculateHashRate(b -> true, periodUnit.toSeconds(periodLenght));
    }

    private BigInteger calculateHashRate(Predicate<BlockHeaderElement> countCondition, long windowDuration) {
        if (hasBestBlock()) {
            long upto = System.currentTimeMillis() / 1000L;
            long from = upto - windowDuration;
            return this.hashRate(getHeaderElement(blockStore.getBestBlock().getHash()), countCondition, b -> checkBlockTimeRange(b, from, upto));
        }
        return BigInteger.ZERO;
    }

    private BigInteger hashRate(BlockHeaderElement elem, Predicate<BlockHeaderElement> countCondition, Predicate<BlockHeaderElement> cutCondition) {
        BigInteger hashRate = BigInteger.ZERO;
        BlockHeaderElement element = elem;

        while (element != null && cutCondition.test(element)) {
            if (countCondition.test(element))
                hashRate = hashRate.add(element.getDifficulty());

            byte[] parentHash = element.getBlockHeader().getParentHash();

            element = getHeaderElement(parentHash);
        }
        return hashRate;
    }

    private boolean checkBlockTimeRange(BlockHeaderElement element, long from, long upto) {
        long ts = element.getBlockHeader().getTimestamp();
        return ts >= from && element.getBlockHeader().getTimestamp() <= upto;
    }

    private Boolean checkOwnership(BlockHeaderElement element) {
        return Arrays.equals(element.getBlockHeader().getCoinbase(), this.address);
    }

    private Boolean hasBestBlock() {
        return blockStore.getBestBlock() != null;
    }

    private BlockHeaderElement getHeaderElement(byte[] hash) {
        BlockHeaderElement element = null;
        if (hash != null) {
            ByteArrayWrapper key = new ByteArrayWrapper(hash);
            element = this.headerCache.get(key);
            if (element == null) {
                Block block = this.blockStore.getBlockByHash(hash);
                if (block != null) {
                    element = new BlockHeaderElement(block.getHeader(), this.blockStore.getBlockByHash(hash).getCumulativeDifficulty());
                    this.headerCache.put(key, element);
                }
            }
        }
        return element;
    }

}
