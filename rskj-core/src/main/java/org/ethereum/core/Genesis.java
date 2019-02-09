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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;

import java.util.*;

/**
 * The genesis block is the first block in the chain and has fixed values according to
 * the protocol specification. The genesis block is 13 items, and is specified thus:
 * <p>
 * ( zerohash_256 , SHA3 RLP () , zerohash_160 , stateRoot, 0, 2^22 , 0, 0, 1000000, 0, 0, 0, SHA3 (42) , (), () )
 * <p>
 * - Where zerohash_256 refers to the parent hash, a 256-bit hash which is all zeroes;
 * - zerohash_160 refers to the coinbase address, a 160-bit hash which is all zeroes;
 * - 2^22 refers to the difficulty;
 * - 0 refers to the timestamp (the Unix epoch);
 * - the transaction trie root and extradata are both 0, being equivalent to the empty byte array.
 * - The sequences of both uncles and transactions are empty and represented by ().
 * - SHA3 (42) refers to the SHA3 hash of a byte array of length one whose first and only byte is of value 42.
 * - SHA3 RLP () value refers to the hash of the uncle lists in RLP, both empty lists.
 * <p>
 * See Yellow Paper: http://www.gavwood.com/Paper.pdf (Appendix I. Genesis Block)
 */
public class Genesis extends Block {

    private final Map<RskAddress, AccountState> initialAccounts;
    private final Map<RskAddress, byte[]> initialCodes;
    private final Map<RskAddress, Map<DataWord, byte[]>> initialStorages;

    private static final byte[] ZERO_HASH_2048 = new byte[256];
    protected static final long NUMBER = 0;

    public Genesis(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                   byte[] difficulty, long number, long gasLimit,
                   long gasUsed, long timestamp,
                   byte[] extraData,
                   byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                   byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] minimumGasPrice,
                   Map<RskAddress, AccountState> initialAccounts,
                   Map<RskAddress, byte[]> initialCodes,
                   Map<RskAddress, Map<DataWord, byte[]>> initialStorages){
        super(
                new BlockHeader(parentHash, unclesHash, coinbase, logsBloom, difficulty,
                        number, ByteUtil.longToBytesNoLeadZeroes(gasLimit), gasUsed, timestamp, extraData,
                        bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                        bitcoinMergedMiningCoinbaseTransaction, minimumGasPrice, 0) {

                    @Override
                    protected byte[] encodeBlockDifficulty(BlockDifficulty ignored) {
                        return RLP.encodeElement(difficulty);
                    }
                });
        if (!initialAccounts.keySet().containsAll(initialCodes.keySet())) {
            throw new IllegalArgumentException("Code must have an associated account");
        }
        if (!initialAccounts.keySet().containsAll(initialStorages.keySet())) {
            throw new IllegalArgumentException("Storage must have an associated account");
        }
        this.initialAccounts = Collections.unmodifiableMap(initialAccounts);
        this.initialCodes = Collections.unmodifiableMap(initialCodes);
        this.initialStorages = Collections.unmodifiableMap(initialStorages);
        setTransactionsList(Collections.emptyList());
    }

    public static byte[] getZeroHash(){
        return Arrays.copyOf(ZERO_HASH_2048, ZERO_HASH_2048.length);
    }

    /**
     * WORKAROUND.
     * This is overrode because the Genesis' parent hash is an empty byte array,
     * which isn't a valid Keccak256 hash.
     * For encoding purposes, the empty byte array is used instead.
     */
    @Override
    public Keccak256 getParentHash() {
        return Keccak256.ZERO_HASH;
    }

    public Map<RskAddress, AccountState> getAccounts() {
        return initialAccounts;
    }

    public Map<RskAddress, byte[]> getCodes() {
        return initialCodes;
    }

    public Map<RskAddress, Map<DataWord, byte[]>> getStorages() {
        return initialStorages;
    }
}
