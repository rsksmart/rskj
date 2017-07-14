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

package org.ethereum.config;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeTestNetConstants;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

/**
 * Describes different constants specific for a blockchain
 *
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class Constants {
    private int maximumExtraDataSize = 32;
    private int minGasLimit = 3000000;
    private int gasLimitBoundDivisor = 1024;
    private int targetGasLimit = 5000000;

    private BigInteger minimumDifficulty = BigInteger.valueOf(131072);
    private BigInteger difficultyBoundDivisor = BigInteger.valueOf(2048);
    private int expDifficultyPeriod = 100000;

    private int uncleGenerationLimit = 7;
    private int uncleListLimit = 10;

    private int bestNumberDiffLimit = 100;

    private int newBlockMaxMinInTheFuture = 540;

    private static final BigInteger SECP256K1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

    private static final byte[] BURN_ADDRESS = Hex.decode("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

    private static final byte CHAIN_ID = 30;

    public int getDurationLimit() {
        return 8;
    }

    public BigInteger

    getInitialNonce() {
        return BigInteger.ZERO;
    }

    public int getMaximumExtraDataSize() {
        return maximumExtraDataSize;
    }

    public int getMinGasLimit() {
        return minGasLimit;
    }

    public int getGasLimitBoundDivisor() {
        return gasLimitBoundDivisor;
    }

    public BigInteger getMinimumDifficulty() {
        return minimumDifficulty;
    }

    public BigInteger getDifficultyBoundDivisor() {
        return difficultyBoundDivisor;
    }

    public int getExpDifficultyPeriod() {
        return expDifficultyPeriod;
    }

    public int getUncleGenerationLimit() {
        return uncleGenerationLimit;
    }

    public int getUncleListLimit() {
        return uncleListLimit;
    }

    public int getBestNumberDiffLimit() {
        return bestNumberDiffLimit;
    }

    public static BigInteger getSECP256K1N() {
        return SECP256K1N;
    }

    public BridgeConstants getBridgeConstants() { return BridgeTestNetConstants.getInstance(); }

    public long getTargetGasLimit() {
        return targetGasLimit;
    }

    public int getNewBlockMaxMinInTheFuture() {
        return this.newBlockMaxMinInTheFuture;
    }

    public byte[] getBurnAddress() { return ByteUtils.clone(Constants.BURN_ADDRESS); }

    /**
     * EIP155: https://github.com/ethereum/EIPs/issues/155
     */
    public byte getChainId() { return Constants.CHAIN_ID; }
}
