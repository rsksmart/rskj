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
package org.ethereum.jsontestsuite;

import org.ethereum.util.ByteUtil;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class Env {

    private final byte[] currentCoinbase;
    private final byte[] currentDifficulty;
    private final byte[] currentGasLimit;
    private final byte[] currentMinimumGasPrice;
    private final byte[] currentNumber;
    private final byte[] currentTimestamp;
    private final byte[] previousHash;

    public Env(byte[] currentCoinbase, byte[] currentDifficulty, byte[]
            currentGasLimit, byte[] currentMinimumGasPrice,
            byte[] currentNumber, byte[] currentTimestamp,
            byte[] previousHash) {
        this.currentCoinbase = currentCoinbase;
        this.currentDifficulty = currentDifficulty;
        this.currentGasLimit = currentGasLimit;
        this.currentMinimumGasPrice = currentMinimumGasPrice;
        this.currentNumber = currentNumber;
        this.currentTimestamp = currentTimestamp;
        this.previousHash = previousHash;
    }

    public byte[] getCurrentCoinbase() {
        return currentCoinbase;
    }

    public byte[] getCurrentDifficulty() {
        return currentDifficulty;
    }

    public byte[] getCurrentGasLimit() {
        return currentGasLimit;
    }

    public byte[] getCurrentMinimumGasPrice() {
        return currentMinimumGasPrice;
    }

    public byte[] getCurrentNumber() {
        return currentNumber;
    }

    public byte[] getCurrentTimestamp() {
        return currentTimestamp;
    }

    public byte[] getPreviousHash() {
        return previousHash;
    }

    @Override
    public String toString() {
        return "Env{" +
                "currentCoinbase=" + ByteUtil.toHexString(currentCoinbase) +
                ", currentDifficulty=" + ByteUtil.toHexString(currentDifficulty) +
                ", currentGasLimit=" + ByteUtil.toHexString(currentGasLimit) +
                ", currentMinimumGasPrice=" + ByteUtil.toHexString(currentMinimumGasPrice) +
                ", currentNumber=" + ByteUtil.toHexString(currentNumber) +
                ", currentTimestamp=" + ByteUtil.toHexString(currentTimestamp) +
                ", previousHash=" + ByteUtil.toHexString(previousHash) +
                '}';
    }
}
