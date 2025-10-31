/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR ANY PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.util.HexUtils;
import co.rsk.util.DifficultyUtils;
import co.rsk.mine.MinerUtils;

/**
 * MinerClient for timed mining with exponential distribution
 * Creates blocks at intervals following an exponential distribution with configurable median
 */
public class TimedMinerClient implements MinerClient {
    private static final Logger logger = LoggerFactory.getLogger("minerClient");

    private final MinerServer minerServer;
    private final Duration medianBlockTime;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final boolean skipPowValidation;
    private final long baseMedianMillis;
    private volatile double difficultyScale = 1.0; // currentDifficulty / baselineDifficulty
    private BigInteger baselineDifficulty = null;

    private volatile boolean stop = false;
    private volatile boolean isMining = false;

    public TimedMinerClient(MinerServer minerServer, Duration medianBlockTime, boolean skipPowValidation) {
        this.minerServer = minerServer;
        this.medianBlockTime = medianBlockTime;
        this.skipPowValidation = skipPowValidation;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimedMinerClient");
            t.setDaemon(true);
            return t;
        });
        this.random = new Random();
        this.baseMedianMillis = medianBlockTime.toMillis();
    }

    @Override
    public void start() {
        if (isMining) {
            return;
        }
        
        isMining = true;
        scheduleNextMining();
        logger.info("TimedMinerClient started with median block time: {}", medianBlockTime);
    }

    @Override
    public boolean isMining() {
        return this.isMining;
    }

    @Override
    public boolean mineBlock() {
        // if miner server was stopped for some reason, we don't mine.
        if (stop) {
            return false;
        }

        try {
            MinerWork work = minerServer.getWork();

            co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
            co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
            co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

            // Update difficulty scaling for next interval
            try {
                BigInteger target = new BigInteger(1, HexUtils.stringHexToByteArray(work.getTarget()));
                if (target.signum() > 0) {
                    BigInteger currentDifficulty = DifficultyUtils.MAX.divide(target);
                    if (baselineDifficulty == null || baselineDifficulty.signum() == 0) {
                        baselineDifficulty = currentDifficulty;
                        difficultyScale = 1.0;
                    } else {
                        difficultyScale = currentDifficulty.doubleValue() / baselineDifficulty.doubleValue();
                        if (!Double.isFinite(difficultyScale) || difficultyScale <= 0) {
                            difficultyScale = 1.0;
                        }
                    }
                }
                if (!skipPowValidation) {
                    findNonce(bitcoinMergedMiningBlock, target);
                } else {
                    bitcoinMergedMiningBlock.setNonce(0);
                }
            } catch (Exception ignored) {
                // Any nonce is acceptable when PoW validation is skipped
                bitcoinMergedMiningBlock.setNonce(0);
            }

            logger.info("Mined block: {}", work.getBlockHashForMergedMining());
            minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

            // Schedule next mining operation
            if (isMining && !stop) {
                scheduleNextMining();
            }

            return true;
        } catch (Exception e) {
            logger.error("Error mining block", e);
            // Schedule next mining operation even if this one failed
            if (isMining && !stop) {
                scheduleNextMining();
            }
            return false;
        }
    }

    @Override
    public void stop() {
        stop = true;
        isMining = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("TimedMinerClient stopped");
    }

    /**
     * Schedule the next mining operation using exponential distribution
     */
    private void scheduleNextMining() {
        if (stop || !isMining) {
            return;
        }

        // Generate exponential distribution time with the configured median
        // For exponential distribution: mean = median / ln(2)
        // Scale median by observed difficulty ratio so higher difficulty → longer intervals
        double scaledMedianMs = (baseMedianMillis * difficultyScale);
        if (!Double.isFinite(scaledMedianMs) || scaledMedianMs <= 0) {
            scaledMedianMs = baseMedianMillis;
        }
        double mean = scaledMedianMs / Math.log(2.0);
        long delayMillis = (long) (-mean * Math.log(1.0 - random.nextDouble()));
        
        // Ensure minimum delay of 100ms to prevent excessive CPU usage
        delayMillis = Math.max(delayMillis, 100);
        
        logger.debug("Scheduling next mining operation in {} ms / median is: {} ms", delayMillis, (long)scaledMedianMs);
        
        scheduler.schedule(() -> {
            if (isMining && !stop) {
                mineBlock();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Find a valid nonce for bitcoinMergedMiningBlock, that satisfies the given target difficulty.
     */
    private void findNonce(
            BtcBlock bitcoinMergedMiningBlock,
            BigInteger target) {
        long nextNonceToUse = 0;
        bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);

        while (!stop) {
            // Is our proof of work valid yet?
            BigInteger blockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();
            if (blockHashBI.compareTo(target) <= 0) {
                return;
            }
            // No, so increment the nonce and try again.
            bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);
            if (bitcoinMergedMiningBlock.getNonce() % 100000 == 0) {
                logger.debug("Solving block. Nonce: {}", bitcoinMergedMiningBlock.getNonce());
            }
        }
    }
}
