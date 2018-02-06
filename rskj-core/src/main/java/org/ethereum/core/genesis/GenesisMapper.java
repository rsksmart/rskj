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

import org.ethereum.core.Genesis;
import org.ethereum.crypto.HashUtil;
import org.ethereum.json.Utils;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

/**
 * Created by mario on 13/01/17.
 */
public class GenesisMapper {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    public Genesis mapFromJson(GenesisJson json, boolean rskFormat) {
        byte[] nonce = Utils.parseData(json.nonce);
        byte[] difficulty = Utils.parseData(json.difficulty);
        byte[] mixHash = Utils.parseData(json.mixhash);
        byte[] coinbase = Utils.parseData(json.coinbase);

        byte[] timestampBytes = Utils.parseData(json.timestamp);
        long timestamp = ByteUtil.byteArrayToLong(timestampBytes);

        byte[] parentHash = Utils.parseData(json.parentHash);
        byte[] extraData = Utils.parseData(json.extraData);

        byte[] gasLimitBytes = Utils.parseData(json.gasLimit);
        long gasLimit = ByteUtil.byteArrayToLong(gasLimitBytes);

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        byte[] minGasPrice = null;

        if (rskFormat) {
            bitcoinMergedMiningHeader = Utils.parseData(json.bitcoinMergedMiningHeader);
            bitcoinMergedMiningMerkleProof = Utils.parseData(json.bitcoinMergedMiningMerkleProof);
            bitcoinMergedMiningCoinbaseTransaction = Utils.parseData(json.bitcoinMergedMiningCoinbaseTransaction);
            minGasPrice = Utils.parseData(json.getMinimumGasPrice());
        }

        return new Genesis(parentHash, EMPTY_LIST_HASH, coinbase, Genesis.getZeroHash(),
                difficulty, 0, gasLimit, 0, timestamp, extraData,
                mixHash, nonce, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, minGasPrice);
    }
}
