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

import co.rsk.core.RskAddress;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.util.RskCustomCache;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Duration;

/**
 * Created by mario on 05/08/2016.
 */
public class HashRateCalculatorTest {

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);
    private final byte[] FAKE_GENERIC_HASH = TestUtils.randomBytes(32);
    private final byte[] OHTER_FAKE_GENERIC_HASH = TestUtils.randomBytes(32)        ;
    private final RskAddress FAKE_COINBASE = TestUtils.randomAddress();
    private final RskAddress NOT_MY_COINBASE = TestUtils.randomAddress();



    private BlockStore blockStore;
    private Block block;
    private BlockHeader blockHeader;

    @BeforeEach
    public void init() {
        blockStore = Mockito.mock(BlockStore.class);
        block = Mockito.mock(Block.class);
        blockHeader = Mockito.mock(BlockHeader.class);

        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getHash()).thenReturn(new Keccak256(FAKE_GENERIC_HASH));
        Mockito.when(blockHeader.getParentHash()).thenReturn(new Keccak256(FAKE_GENERIC_HASH))
                .thenReturn(new Keccak256(OHTER_FAKE_GENERIC_HASH))
                .thenReturn(new Keccak256(FAKE_GENERIC_HASH))
                .thenReturn(null);

        Mockito.when(blockHeader.getHash()).thenReturn(new Keccak256(FAKE_GENERIC_HASH));

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

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(TEST_DIFFICULTY);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assertions.assertEquals(new BigInteger("+2"), hashRate);
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

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(TEST_DIFFICULTY);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorNonMining(blockStore, new RskCustomCache<>(1000L));
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assertions.assertEquals(BigInteger.ZERO, hashRate);
    }

    @Test
    public void calculateNodeHashRateOldBlock() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp())
                .thenReturn(ts - 10000L);

        Mockito.when(blockHeader.getCoinbase()).thenReturn(FAKE_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(TEST_DIFFICULTY);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));

        Assertions.assertEquals(hashRate, BigInteger.ZERO);
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

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(TEST_DIFFICULTY);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNetHashRate(Duration.ofHours(1));

        Assertions.assertEquals(hashRate, new BigInteger("+4"));
    }

    @Test
    public void calculateNetHashRateOldBlock() {
        long ts = System.currentTimeMillis() / 1000L;
        Mockito.when(blockHeader.getTimestamp())
                .thenReturn(ts - 10000L);

        Mockito.when(blockHeader.getCoinbase()).thenReturn(FAKE_COINBASE);

        Mockito.when(block.getCumulativeDifficulty()).thenReturn(TEST_DIFFICULTY);

        HashRateCalculator hashRateCalculator = new HashRateCalculatorMining(blockStore, new RskCustomCache<>(1000L), FAKE_COINBASE);
        BigInteger hashRate = hashRateCalculator.calculateNetHashRate(Duration.ofHours(1));

        Assertions.assertEquals(hashRate, BigInteger.ZERO);
    }

}
