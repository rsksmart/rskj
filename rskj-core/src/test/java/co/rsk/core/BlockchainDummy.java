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

package co.rsk.core;

import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.*;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;

import java.math.BigInteger;
import java.util.List;

public class BlockchainDummy implements Blockchain {

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
    public void setBestBlock(Block block) {

    }

    @Override
    public Block getBestBlock() {
        return null;
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
    public TransactionInfo getTransactionInfo(Keccak256 hash) {
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
    public Keccak256 getBestBlockHash() {
        return new Keccak256(new byte[0]);
    }

    @Override
    public Block getBlockByHash(Keccak256 hash) {
        return null;
    }

    @Override
    public void setExitOn(long exitOn) {

    }

    @Override
    public boolean isBlockExist(Keccak256 hash) {
        return false;
    }

    @Override
    public List<BlockHeader> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse) {
        return null;
    }

    @Override
    public List<byte[]> getListOfBodiesByHashes(List<Keccak256> hashes) {
        return null;
    }

    @Override
    public List<Block> getBlocksByNumber(long blockNr) {
        return null;
    }

    @Override
    public ReceiptStore getReceiptStore() { return null; }

    @Override
    public Repository getRepository() { return null; }

    @Override
    public BlockStore getBlockStore() { return null; }

    @Override
    public void removeBlocksByNumber(long blockNumber) {
        // unused
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long number) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasBlockInSomeBlockchain(Keccak256 hash) {
        return false;
    }

}
