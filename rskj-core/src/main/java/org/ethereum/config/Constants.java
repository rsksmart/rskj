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
    private int MAXIMUM_EXTRA_DATA_SIZE = 32;
    private int MIN_GAS_LIMIT = 3000000;
    private int GAS_LIMIT_BOUND_DIVISOR = 1024;
    private int TARGET_GAS_LIMIT = 5000000;

    private BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(131072);
    private BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(2048);
    private int EXP_DIFFICULTY_PERIOD = 100000;

    private int UNCLE_GENERATION_LIMIT = 7;
    private int UNCLE_LIST_LIMIT = 10;

    private int BEST_NUMBER_DIFF_LIMIT = 100;

    private int NEW_BLOCK_MAX_MIN_IN_THE_FUTURE = 540;

    private static final BigInteger SECP256K1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

    private static final byte[] BURN_ADDRESS = Hex.decode("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

    private static final byte CHAIN_ID = 30;

    public int getDURATION_LIMIT() {
        return 8;
    }

    public BigInteger

    getInitialNonce() {
        return BigInteger.ZERO;
    }

    public int getMAXIMUM_EXTRA_DATA_SIZE() {
        return MAXIMUM_EXTRA_DATA_SIZE;
    }

    public int getMIN_GAS_LIMIT() {
        return MIN_GAS_LIMIT;
    }

    public int getGAS_LIMIT_BOUND_DIVISOR() {
        return GAS_LIMIT_BOUND_DIVISOR;
    }

    public BigInteger getMINIMUM_DIFFICULTY() {
        return MINIMUM_DIFFICULTY;
    }

    public BigInteger getDIFFICULTY_BOUND_DIVISOR() {
        return DIFFICULTY_BOUND_DIVISOR;
    }

    public int getEXP_DIFFICULTY_PERIOD() {
        return EXP_DIFFICULTY_PERIOD;
    }

    public int getUNCLE_GENERATION_LIMIT() {
        return UNCLE_GENERATION_LIMIT;
    }

    public int getUNCLE_LIST_LIMIT() {
        return UNCLE_LIST_LIMIT;
    }

    public int getBEST_NUMBER_DIFF_LIMIT() {
        return BEST_NUMBER_DIFF_LIMIT;
    }

    public static BigInteger getSECP256K1N() {
        return SECP256K1N;
    }

    public BridgeConstants getBridgeConstants() { return BridgeTestNetConstants.getInstance(); }

    public long getTARGET_GAS_LIMIT() {
        return TARGET_GAS_LIMIT;
    }

    public int getNewBlockMaxMinInTheFuture() {
        return this.NEW_BLOCK_MAX_MIN_IN_THE_FUTURE;
    }

    public byte[] getBurnAddress() { return ByteUtils.clone(Constants.BURN_ADDRESS); }

    /**
     * EIP155: https://github.com/ethereum/EIPs/issues/155
     */
    public byte getChainId() { return Constants.CHAIN_ID; }
}
