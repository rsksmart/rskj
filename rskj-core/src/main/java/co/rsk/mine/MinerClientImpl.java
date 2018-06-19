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

package co.rsk.mine;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Rsk;
import co.rsk.panic.PanicProcessor;
import org.ethereum.config.blockchain.DevNetConfig;
import org.ethereum.config.blockchain.regtest.RegTestConfig;
import org.ethereum.rpc.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MinerClient mines new blocks.
 * In fact it just performs the proof-of-work needed to find a valid block and uses
 * uses MinerServer to build blocks to mine and publish blocks once a valid nonce was found.
 * @author Oscar Guindzberg
 */

@Component("MinerClient")
public class MinerClientImpl implements MinerClient {
    private long nextNonceToUse = 0;

    private static final Logger logger = LoggerFactory.getLogger("minerClient");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final long DELAY_BETWEEN_GETWORK_REFRESH_MS = 1000;

    private final Rsk rsk;
    private final MinerServer minerServer;
    private final RskSystemProperties config;

    private volatile boolean stop = false;

    private volatile boolean isMining = false;

    private volatile boolean newBestBlockArrivedFromAnotherNode = false;

    private volatile MinerWork work;
    private Timer aTimer;

    @Autowired
    public MinerClientImpl(Rsk rsk, MinerServer minerServer, RskSystemProperties config) {
        this.rsk = rsk;
        this.minerServer = minerServer;
        this.config = config;
    }

    public void mine() {
        aTimer = new Timer("Refresh work for mining");
        aTimer.schedule(createRefreshWork(), 0, DELAY_BETWEEN_GETWORK_REFRESH_MS);

        Thread doWorkThread = this.createDoWorkThread();
        doWorkThread.start();
    }

    public RefreshWork createRefreshWork() {
        return new RefreshWork();
    }

    public Thread createDoWorkThread() {
        return new Thread() {
            @Override
            public void run() {
                isMining = true;

                while (!stop) {
                    doWork();
                }

                isMining = false;
            }
        };
    }

    public boolean isMining() {
        return this.isMining;
    }

    public void doWork() {
        try {
            if (mineBlock()) {
                if (config.getBlockchainConfig() instanceof RegTestConfig) {
                    Thread.sleep(1000);
                }
                else if (config.getBlockchainConfig() instanceof DevNetConfig) {
                    Thread.sleep(20000);
                }
            }
        } catch (Exception e) {
            logger.error("Error on mining", e);
            panicProcessor.panic("mine", e.getMessage());
        }
    }

    @Override
    public boolean mineBlock() {
        if (this.rsk != null) {
            if (this.rsk.hasBetterBlockToSync()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted mining sleep", ex);
                }
                return false;
            }

            if (this.rsk.isPlayingBlocks()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted mining sleep", ex);
                }
                return false;
            }
        }

        newBestBlockArrivedFromAnotherNode = false;
        work = minerServer.getWork();

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger target = new BigInteger(1, TypeConverter.stringHexToByteArray(work.getTarget()));
        boolean foundNonce = findNonce(bitcoinMergedMiningBlock, target);

        if (newBestBlockArrivedFromAnotherNode) {
            logger.info("Interrupted mining because another best block arrived");
        }

        if (stop) {
            logger.info("Interrupted mining because MinerClient was stopped");
        }

        if (foundNonce) {
            logger.info("Mined block: " + work.getBlockHashForMergedMining());
            minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        }

        return foundNonce;
    }

    @Override
    public boolean fallbackMineBlock() {
        // This is not used now. In the future this method could allow
        // a HSM to provide the signature for an RSK block here.

        if (this.rsk != null) {
            if (this.rsk.hasBetterBlockToSync()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted mining sleep", ex);
                }
                return false;
            }

            if (this.rsk.isPlayingBlocks()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted mining sleep", ex);
                }
                return false;
            }
        }
        if (stop) {
            logger.info("Interrupted mining because MinerClient was stopped");
        }

        return minerServer.generateFallbackBlock();

    }
    /**
     * findNonce will try to find a valid nonce for bitcoinMergedMiningBlock, that satisfies the given target difficulty.
     *
     * @param bitcoinMergedMiningBlock bitcoinBlock to find nonce for. This block's nonce will be modified.
     * @param target                   target difficulty. Block's hash should be lower than this number.
     * @return true if a nonce was found, false otherwise.
     * @remarks This method will return if the stop or newBetBlockArrivedFromAnotherNode intance variables are set to true.
     */
    private boolean findNonce(@Nonnull final co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock,
                              @Nonnull final BigInteger target) {
        bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);

        while (!stop && !newBestBlockArrivedFromAnotherNode) {
            // Is our proof of work valid yet?
            BigInteger blockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();
            if (blockHashBI.compareTo(target) <= 0) {
                return true;
            }
            // No, so increment the nonce and try again.
            bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);
            if (bitcoinMergedMiningBlock.getNonce() % 100000 == 0) {
                logger.debug("Solving block. Nonce: " + bitcoinMergedMiningBlock.getNonce());
            }
        }

        return false; // couldn't find a valid nonce
    }

    public void stop() {
        stop = true;

        if (aTimer!=null) {
            aTimer.cancel();
        }
    }

    /**
     * RefreshWork asks the minerServer for new work.
     */
    public class RefreshWork extends TimerTask {
        @Override
        public void run() {
            MinerWork receivedWork = minerServer.getWork();
            MinerWork previousWork = work;
            if (previousWork != null && receivedWork != null &&
                    !receivedWork.getBlockHashForMergedMining().equals(previousWork.getBlockHashForMergedMining())) {
                newBestBlockArrivedFromAnotherNode = true;
                logger.debug("There is a new best block : " + receivedWork.getBlockHashForMergedMining());
            }
        }
    }
}
