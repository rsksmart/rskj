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

package org.ethereum.listener;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calculates a 'reasonable' Gas price based on statistics of the latest transaction's Gas prices
 * <p>
 * Normally the price returned should be sufficient to execute a transaction since ~25% of the latest
 * transactions were executed at this or lower price.
 * <p>
 * Created by Anton Nashatyrev on 22.09.2015.
 */
public class GasPriceTracker extends EthereumListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger("gaspricetracker");

    private static final int TX_WINDOW_SIZE = 512;

    private static final int BLOCK_WINDOW_SIZE = 50;

    private static final double BLOCK_COMPLETION_PERCENT_FOR_FEE_MARKET_WORKING = 0.9;

    private static final double DEFAULT_GAS_PRICE_MULTIPLIER = 1.1;

    private final Double[] blockWindow = new Double[BLOCK_WINDOW_SIZE];

    private final AtomicReference<Coin> bestBlockPriceRef = new AtomicReference<>();
    private final BlockStore blockStore;
    private final double gasPriceMultiplier;

    private Coin defaultPrice = Coin.valueOf(20_000_000_000L);
    private int blockIdx = 0;

    private final GasPriceCalculator gasPriceCalculator;

    private GasPriceTracker(BlockStore blockStore, GasPriceCalculator gasPriceCalculator, Double configMultiplier) {
        this.blockStore = blockStore;
        this.gasPriceCalculator = gasPriceCalculator;
        this.gasPriceMultiplier = configMultiplier;
    }

    public static GasPriceTracker create(BlockStore blockStore, GasPriceCalculator.GasCalculatorType gasCalculatorType) {
        return create(blockStore, DEFAULT_GAS_PRICE_MULTIPLIER, gasCalculatorType);
    }

    public static GasPriceTracker create(BlockStore blockStore, Double configMultiplier, GasPriceCalculator.GasCalculatorType gasCalculatorType) {
        GasPriceCalculator gasCal;
        switch (gasCalculatorType) {
            case WEIGHTED_PERCENTILE:
                gasCal = new WeightedPercentileGasPriceCalculator();
                break;
            case PLAIN_PERCENTILE:
                gasCal = new PercentileGasPriceCalculator();
                break;
            default:
                throw new IllegalArgumentException("Unknown gas calculator type: " + gasCalculatorType);
        }
        GasPriceTracker gasPriceTracker = new GasPriceTracker(blockStore, gasCal, configMultiplier);
        gasPriceTracker.initializeWindowsFromDB();

        return gasPriceTracker;
    }

    /**
     * @deprecated Use {@link #create(BlockStore, GasPriceCalculator.GasCalculatorType)} instead.
     */
    @Deprecated
    public static GasPriceTracker create(BlockStore blockStore) {
        //Will be using the legacy gas calculator as default option
        return GasPriceTracker.create(blockStore, GasPriceCalculator.GasCalculatorType.PLAIN_PERCENTILE);
    }

    @Override
    public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
        bestBlockPriceRef.set(block.getMinimumGasPrice());
    }

    @Override
    public synchronized void onBlock(Block block, List<TransactionReceipt> receipts) {
        logger.trace("Start onBlock");

        defaultPrice = block.getMinimumGasPrice();

        trackBlockCompleteness(block);

        gasPriceCalculator.onBlock(block, receipts);
        logger.trace("End onBlock");
    }

    public synchronized Coin getGasPrice() {
        Optional<Coin> gasPriceResult = gasPriceCalculator.getGasPrice();
        if(!gasPriceResult.isPresent()) {
            return defaultPrice;
        }

        logger.debug("Gas provided by GasWindowCalc: {}", gasPriceResult.get());

        Coin bestBlockPrice = bestBlockPriceRef.get();
        if (bestBlockPrice == null) {
            logger.debug("Best block price not available, defaulting to {}", gasPriceResult.get());
            return gasPriceResult.get();
        }

        return Coin.max(gasPriceResult.get(), new Coin(new BigDecimal(bestBlockPrice.asBigInteger())
                .multiply(BigDecimal.valueOf(gasPriceMultiplier)).toBigInteger()));
    }

    public synchronized boolean isFeeMarketWorking() {
        if (blockWindow[BLOCK_WINDOW_SIZE - 1] == null) {
            logger.warn("Not enough blocks on window, default to Fee Market not working");
            return false;
        }

        double accumulatedCompleteness = Arrays.stream(blockWindow).reduce(0d, Double::sum);
        double totalBlocks = blockWindow.length;
        double avgBlockCompleteness = accumulatedCompleteness / totalBlocks;

        return avgBlockCompleteness >= BLOCK_COMPLETION_PERCENT_FOR_FEE_MARKET_WORKING;
    }

    private void initializeWindowsFromDB() {
        List<Block> blocks = getRequiredBlocksToFillWindowsFromDB();
        if (blocks.isEmpty()) {
            return;
        }

        onBestBlock(blocks.get(0), Collections.emptyList());
        blocks.forEach(b -> onBlock(b, Collections.emptyList()));
    }

    private List<Block> getRequiredBlocksToFillWindowsFromDB() {
        List<Block> blocks = new ArrayList<>();

        Optional<Block> block = Optional.ofNullable(blockStore.getBestBlock());

        int txCount = 0;
        while ((txCount < TX_WINDOW_SIZE || blocks.size() < BLOCK_WINDOW_SIZE) && block.isPresent()) {
            blocks.add(block.get());
            txCount += block.get().getTransactionsList().stream().filter(tx -> !(tx instanceof RemascTransaction)).count();
            block = block.map(Block::getParentHash).map(Keccak256::getBytes).map(blockStore::getBlockByHash);
        }

        if (txCount < TX_WINDOW_SIZE) {
            logger.warn("Not enough blocks ({}) found on DB to fill tx window", blocks.size());
        }

        if (blocks.size() < BLOCK_WINDOW_SIZE) {
            logger.warn("Not enough blocks ({}) found on DB to fill block window", blocks.size());
        }

        // to simulate processing order
        Collections.reverse(blocks);

        return blocks;
    }

    private void trackBlockCompleteness(Block block) {
        double gasUsed = block.getGasUsed();
        double gasLimit = block.getGasLimitAsInteger().doubleValue();
        double completeness = gasUsed / gasLimit;

        if (blockIdx == BLOCK_WINDOW_SIZE) {
            blockIdx = 0;
        }
        blockWindow[blockIdx++] = completeness;
    }

    public GasPriceCalculator.GasCalculatorType getGasCalculatorType() {
        return gasPriceCalculator.getType();
    }

}
