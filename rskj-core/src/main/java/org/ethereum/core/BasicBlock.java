/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import co.rsk.crypto.Keccak256;

import java.util.List;

public class BasicBlock {

    private final Keccak256 hash;

    private final Keccak256 parentHash;

    private final Long blockNumber;

    private final List<Transaction> transactionList;

    private final byte[] logsBloom;

    private BasicBlock(Keccak256 hash, Keccak256 parentHash, Long blockNumber, List<Transaction> transactionList, byte[] logsBloom) {
        this.hash = hash;
        this.parentHash = parentHash;
        this.blockNumber = blockNumber;
        this.transactionList = transactionList;
        this.logsBloom = logsBloom;
    }

    public static BasicBlock createFromScratch(Keccak256 hash, Keccak256 parentHash, long blockNumber, List<Transaction> transactionList, byte[] logsBloom) {
        return new BasicBlock(hash, parentHash, blockNumber, transactionList, logsBloom);
    }

    public static BasicBlock createFromBlock(Block block) {
        return new BasicBlock(block.getHash(), block.getParentHash(), block.getNumber(), block.getTransactionsList(), block.getLogBloom());
    }

    public Keccak256 getHash() {
        return hash;
    }

    public Keccak256 getParentHash() {
        return parentHash;
    }

    public Long getNumber() {
        return blockNumber;
    }

    public List<Transaction> getTransactions() {
        return transactionList;
    }

    public byte[] getLogBloom() {
        return logsBloom;
    }
}
