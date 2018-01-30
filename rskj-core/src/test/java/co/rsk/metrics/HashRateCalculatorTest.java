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

import co.rsk.core.commons.Keccak256;
import co.rsk.peg.PegTestUtils;
import co.rsk.core.commons.RskAddress;
import co.rsk.util.RskCustomCache;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Duration;

/**
 * Created by mario on 05/08/2016.
 */
public class HashRateCalculatorTest {

    private final Keccak256 FAKE_GENERIC_HASH = PegTestUtils.createHash3();
    private final Keccak256 OHTER_FAKE_GENERIC_HASH = PegTestUtils.createHash3();
    private final RskAddress FAKE_COINBASE = TestUtils.randomAddress();
    private final RskAddress NOT_MY_COINBASE = TestUtils.randomAddress();

    private BlockStore blockStore;
    private Block block;
    private BlockHeader blockHeader;

    @Before
    public void init() {
        blockStore = Mockito.mock(BlockStore.class);
        block = Mockito.mock(Block.class);
        blockHeader = Mockito.mock(BlockHeader.class);

        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getHash()).thenReturn(FAKE_GENERIC_HASH);
        Mockito.when(blockHeader.getParentHash())
                .thenReturn(FAKE_GENERIC_HASH)
                .thenReturn(OHTER_FAKE_GENERIC_HASH)
                .thenReturn(FAKE_GENERIC_HASH)
                .thenReturn(null);

        Mockito.when(blockStore.getBlockByHash(Mockito.any())).thenReturn(block)
                .thenReturn(block).thenReturn(block).thenReturn(null);

        Mockito.when(blockStore.getBestBlock()).thenReturn(block);
        Mockito.when(blockStore.getBlockByHash(Mockito.any())).thenReturn(block);
    }

    @Test
    public void calculateNodeHashRate() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp()).thenReturn(ts);

        Mockito.when(blockHeader.getCoinbase())
                .thenReturn(NOT_MY_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(NOT_MY_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assert.assertEquals(new BigInteger("+2"), hashRate);
    }

    @Test
    public void calculateNodeHashRateWithMiningDisabled() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp()).thenReturn(ts);

        Mockito.when(blockHeader.getCoinbase())
                .thenReturn(NOT_MY_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(NOT_MY_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorNonMining(blockStore, new RskCustomCache<>(1000L));
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assert.assertEquals(BigInteger.ZERO, hashRate);
    }

    @Test
    public void calculateNodeHashRateOldBlock() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp())
                .thenReturn(ts - 10000L);

        Mockito.when(blockHeader.getCoinbase()).thenReturn(FAKE_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assert.assertEquals(hashRate, BigInteger.ZERO);
    }

    @Test
    public void calculateNetHashRate() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp()).thenReturn(ts);

        Mockito.when(blockHeader.getCoinbase())
                .thenReturn(NOT_MY_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(FAKE_COINBASE)
                .thenReturn(NOT_MY_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNetHashRate(Duration.ofHours(1));

        Assert.assertEquals(hashRate, new BigInteger("+4"));
    }

    @Test
    public void calculateNetHashRateOldBlock() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp())
                .thenReturn(ts - 10000L);

        Mockito.when(blockHeader.getCoinbase()).thenReturn(FAKE_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNetHashRate(Duration.ofHours(1));

        Assert.assertEquals(hashRate, BigInteger.ZERO);
    }

}
