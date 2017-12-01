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

package org.ethereum.rpc.Simples;


import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.EventInfoItem;
import org.ethereum.core.*;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by Ruben on 14/06/2016.
 */
public class SimpleBlockChain implements org.ethereum.core.Blockchain {
    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public ImportResult tryToConnect(Block block) {
        return null;
    }

    @Override
    public Block getBlockByNumber(long blockNr) {
        return null;
    }

    @Override
    public List<EventInfoItem> getEventsByBlockHash(byte[] hash) {
        return null;
    }

    @Override
    public List<EventInfoItem> getEventsByBlockNumber(long blockNr) {
        return null;
    }
    @Override
    public void setBestBlock(Block block) {

    }

    @Override
    public PendingState getPendingState() {
        return null;
    }

    @Override
    public BlockChainStatus getStatus() {
        return null;
    }

    @Override
    public void setStatus(Block block, BigInteger difficulty) {
        // not used
    }

    @Override
    public Block getBestBlock() {

        Block block = BlockGenerator.getInstance().getGenesisBlock();

        return block;
    }

    @Override
    public TransactionInfo getTransactionInfo(byte[] hash) {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public BigInteger getTotalDifficulty() {
        return null;
    }

    @Override
    public void setTotalDifficulty(BigInteger totalDifficulty) {

    }

    @Override
    public byte[] getBestBlockHash() {
        return new byte[0];
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return null;
    }

    @Override
    public void setExitOn(long exitOn) {

    }

    @Override
    public boolean isBlockExist(byte[] hash) {
        return false;
    }

    @Override
    public List<BlockHeader> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse) {
        return null;
    }

    @Override
    public List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes) {
        return null;
    }

    @Override
    public List<Block> getBlocksByNumber(long blockNr) {
        return null;
    }

    @Override
    public ReceiptStore getReceiptStore() { return null; }

    @Override
    public EventsStore getEventsStore() { return null; }

    @Override
    public BlockStore getBlockStore() { return null; }

    @Override
    public Repository getRepository() { return null; }

    @Override
    public void removeBlocksByNumber(long number) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long number) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasBlockInSomeBlockchain(byte[] hash) {
        return false;
    }

}
