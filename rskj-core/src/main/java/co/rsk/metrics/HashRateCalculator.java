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

import co.rsk.crypto.Keccak256;
import co.rsk.util.RskCustomCache;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

public abstract class HashRateCalculator {

    private final BlockStore blockStore;
    private final RskCustomCache<Keccak256, BlockHeaderElement> headerCache;

    public HashRateCalculator(BlockStore blockStore, RskCustomCache<Keccak256, BlockHeaderElement> headerCache) {
        this.blockStore = blockStore;
        this.headerCache = headerCache;
    }

    public void start() {
        headerCache.start();
    }

    public void stop() {
        headerCache.stop();
    }

    public abstract BigInteger calculateNodeHashRate(Duration duration);

    public BigInteger calculateNetHashRate(Duration period) {
        return calculateHashRate(b -> true, period);
    }

    protected BigInteger calculateHashRate(Predicate<BlockHeaderElement> countCondition, Duration period) {
        if (hasBestBlock()) {
            Instant upto = Clock.systemUTC().instant();
            Instant from = upto.minus(period);
            return this.hashRate(getHeaderElement(blockStore.getBestBlock().getHash()), countCondition, b -> checkBlockTimeRange(b, from, upto));
        }
        return BigInteger.ZERO;
    }

    private BigInteger hashRate(BlockHeaderElement elem, Predicate<BlockHeaderElement> countCondition, Predicate<BlockHeaderElement> cutCondition) {
        BigInteger hashRate = BigInteger.ZERO;
        BlockHeaderElement element = elem;

        while (element != null && cutCondition.test(element)) {
            if (countCondition.test(element)) {
                hashRate = hashRate.add(element.getDifficulty().asBigInteger());
            }

            Keccak256 parentHash = element.getBlockHeader().getParentHash();

            element = getHeaderElement(parentHash);
        }
        return hashRate;
    }

    private boolean checkBlockTimeRange(BlockHeaderElement element, Instant from, Instant upto) {
        Instant ts = Instant.ofEpochSecond(element.getBlockHeader().getTimestamp());
        return !ts.isBefore(from) && !ts.isAfter(upto);
    }

    private Boolean hasBestBlock() {
        return blockStore.getBestBlock() != null;
    }

    private BlockHeaderElement getHeaderElement(Keccak256 hash) {
        BlockHeaderElement element = null;
        if (hash != null) {
            element = this.headerCache.get(hash);
            if (element == null) {
                Block block = this.blockStore.getBlockByHash(hash.getBytes());
                if (block != null) {
                    element = new BlockHeaderElement(block.getHeader(), this.blockStore.getBlockByHash(hash.getBytes()).getCumulativeDifficulty());
                    this.headerCache.put(hash, element);
                }
            }
        }
        return element;
    }
}
