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

import org.ethereum.core.Block;

import javax.annotation.Nonnull;


public interface MinerServer {

    void start();

    void stop();

    boolean isRunning();

    SubmitBlockResult submitBitcoinBlock(String blockHashForMergedMining, co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock);

    boolean generateFallbackBlock();

    void setFallbackMining(boolean p);

    void setAutoSwitchBetweenNormalAndFallbackMining(boolean p);

    boolean isFallbackMining();

    byte[] getCoinbaseAddress();

    MinerWork getWork();

    void buildBlockToMine(@Nonnull Block newParent, boolean createCompetitiveBlock);

    void setExtraData(byte[] extraData);

    long getCurrentTimeInSeconds();

    long increaseTime(long seconds);
}
