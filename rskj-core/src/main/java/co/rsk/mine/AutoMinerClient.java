/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.util.HexUtils;

/**
 * MinerClient for automine setting
 */
public class AutoMinerClient implements MinerClient {
    private static final Logger logger = LoggerFactory.getLogger("minerClient");

    private final MinerServer minerServer;

    private volatile boolean stop = false;
    private volatile boolean isMining = false;

    public AutoMinerClient(MinerServer minerServer) {
        this.minerServer = minerServer;
    }

    @Override
    public void start() {
        isMining = true;
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

        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger target = new BigInteger(1, HexUtils.stringHexToByteArray(work.getTarget()));
        findNonce(bitcoinMergedMiningBlock, target);

        logger.info("Mined block: {}", work.getBlockHashForMergedMining());
        minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        return true;
    }

    @Override
    public void stop() {
        stop = true;
        isMining = false;
    }

    /**
     * Find a valid nonce for bitcoinMergedMiningBlock, that satisfies the given target difficulty.
     */
    private void findNonce(
            BtcBlock bitcoinMergedMiningBlock,
            BigInteger target) {
        long nextNonceToUse = 0;
        bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);

        while (true) {
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
