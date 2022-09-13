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

import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class Env {

    private final byte[] currentCoinbase;
    private final byte[] currentDifficulty;
    private final byte[] currentGasLimit;
    private final byte[] currentNumber;
    private final byte[] currentTimestamp;
    private final byte[] previousHash;


    public Env(byte[] currentCoinbase, byte[] currentDifficulty, byte[]
            currentGasLimit, byte[] currentNumber, byte[]
            currentTimestamp, byte[] previousHash) {
        this.currentCoinbase = currentCoinbase;
        this.currentDifficulty = currentDifficulty;
        this.currentGasLimit = currentGasLimit;
        this.currentNumber = currentNumber;
        this.currentTimestamp = currentTimestamp;
        this.previousHash = previousHash;
    }

    /*
                e.g:
                    "currentCoinbase" : "2adc25665018aa1fe0e6bc666dac8fc2697ff9ba",
                    "currentDifficulty" : "256",
                    "currentGasLimit" : "1000000",
                    "currentNumber" : "0",
                    "currentTimestamp" : 1,
                    "previousHash" : "5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6"
          */
    public Env(JsonNode env) {

        String coinbase = env.get("currentCoinbase").asText();
        String difficulty = env.get("currentDifficulty").asText();
        String timestamp = env.get("currentTimestamp").asText();
        String number = env.get("currentNumber").asText();
        String gasLimit = org.ethereum.json.Utils.parseUnidentifiedBase(env.get("currentGasLimit").asText());
        JsonNode previousHash = env.get("previousHash");
        String prevHash = previousHash == null ? "" : previousHash.asText();

        this.currentCoinbase = Hex.decode(coinbase);
        this.currentDifficulty = BigIntegers.asUnsignedByteArray(TestCase.toBigInt(difficulty) );
        this.currentGasLimit =   BigIntegers.asUnsignedByteArray(TestCase.toBigInt(gasLimit));
        this.currentNumber = TestCase.toBigInt(number).toByteArray();
        this.currentTimestamp = TestCase.toBigInt(timestamp).toByteArray();
        this.previousHash = Hex.decode(prevHash);

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
                ", currentNumber=" + ByteUtil.toHexString(currentNumber) +
                ", currentTimestamp=" + ByteUtil.toHexString(currentTimestamp) +
                ", previousHash=" + ByteUtil.toHexString(previousHash) +
                '}';
    }
}
