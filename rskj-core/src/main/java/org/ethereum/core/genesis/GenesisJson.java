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

package org.ethereum.core.genesis;


import java.util.Map;

public class GenesisJson {

    String mixhash;
    String coinbase;
    String timestamp;
    String parentHash;
    String extraData;
    String gasLimit;
    String nonce;
    String difficulty;
    String bitcoinMergedMiningHeader;
    String bitcoinMergedMiningMerkleProof;
    String bitcoinMergedMiningCoinbaseTransaction;
    String minimumGasPrice;

    Map<String, AllocatedAccount> alloc;

    public GenesisJson() {
    }


    public String getMixhash() {
        return mixhash;
    }

    public void setMixhash(String mixhash) {
        this.mixhash = mixhash;
    }

    public String getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(String coinbase) {
        this.coinbase = coinbase;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getParentHash() {
        return parentHash;
    }

    public void setParentHash(String parentHash) {
        this.parentHash = parentHash;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public String getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(String gasLimit) {
        this.gasLimit = gasLimit;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Map<String, AllocatedAccount> getAlloc() {
        return alloc;
    }

    public void setAlloc(Map<String, AllocatedAccount> alloc) {
        this.alloc = alloc;
    }

    public String getBitcoinMergedMiningHeader() {return bitcoinMergedMiningHeader;}

    public void setBitcoinMergedMiningHeader(String bitcoinMergedMiningHeader) {this.bitcoinMergedMiningHeader = bitcoinMergedMiningHeader;}

    public String getBitcoinMergedMiningMerkleProof() {return bitcoinMergedMiningMerkleProof;}

    public void setBitcoinMergedMiningMerkleProof(String bitcoinMergedMiningMerkleProof) {this.bitcoinMergedMiningMerkleProof = bitcoinMergedMiningMerkleProof;}

    public String getBitcoinMergedMiningCoinbaseTransaction() {return bitcoinMergedMiningCoinbaseTransaction;}

    public void setBitcoinMergedMiningCoinbaseTransaction(String bitcoinMergedMiningCoinbaseTransaction) {this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;}

    public String getMinimumGasPrice() {
        return minimumGasPrice;
    }

}
