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

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Sha3Hash;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;

import java.math.BigInteger;
import java.util.List;

public interface Blockchain {
    long getSize();

    ImportResult tryToConnect(Block block);

    Block getBlockByNumber(long blockNr);

    void setBestBlock(Block block);

    void setStatus(Block block, BigInteger totalDifficulty);

    BlockChainStatus getStatus();

    PendingState getPendingState();

    Block getBestBlock();

    TransactionInfo getTransactionInfo(byte[] hash);

    void close();

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    Sha3Hash getBestBlockHash();

    Block getBlockByHash(Sha3Hash hash);

    void setExitOn(long exitOn);

    boolean isBlockExist(Sha3Hash hash);

    List<BlockHeader> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<Sha3Hash> hashes);

    List<Block> getBlocksByNumber(long blockNr);

    void removeBlocksByNumber(long blockNr);

    ReceiptStore getReceiptStore();

    BlockStore getBlockStore();

    Repository getRepository();

    List<BlockInformation> getBlocksInformationByNumber(long number);

    boolean hasBlockInSomeBlockchain(Sha3Hash hash);
}
