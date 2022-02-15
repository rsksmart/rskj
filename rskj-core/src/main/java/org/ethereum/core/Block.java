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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import com.google.common.collect.ImmutableList;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The block in Ethereum is the collection of relevant pieces of information
 * (known as the blockheader), H, together with information corresponding to
 * the comprised transactions, R, and a set of other blockheaders U that are known
 * to have a parent equalBytes to the present block’s parent’s parent
 * (such blocks are known as uncles).
 *
 * @author Roman Mandeleil
 * @author Nick Savers
 * @since 20.05.2014
 */
public class Block {
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private BlockHeader header;

    private List<Transaction> transactionsList;

    private List<BlockHeader> uncleList;

    /* Private */
    private byte[] rlpEncoded;

    /* Indicates if this block can or cannot be changed */
    private volatile boolean sealed;

    public static Block createBlockFromHeader(BlockHeader header, boolean isRskip126Enabled) {
        return new Block(header, Collections.emptyList(), Collections.emptyList(), isRskip126Enabled, true, false);
    }

    public Block(BlockHeader header, List<Transaction> transactionsList, List<BlockHeader> uncleList, boolean isRskip126Enabled, boolean sealed) {
        this(header, transactionsList, uncleList, isRskip126Enabled, sealed, true);
    }

    private Block(BlockHeader header, List<Transaction> transactionsList, List<BlockHeader> uncleList, boolean isRskip126Enabled, boolean sealed, boolean checktxs) {
        byte[] calculatedRoot = BlockHashesHelper.getTxTrieRoot(transactionsList, isRskip126Enabled);

        if (checktxs && !Arrays.areEqual(header.getTxTrieRoot(), calculatedRoot)) {
            String message = String.format(
                    "Transactions trie root validation failed for block %d %s", header.getNumber(), header.getHash()
            );
            panicProcessor.panic("txroot", message);
            throw new IllegalArgumentException(message);
        }

        this.header = header;
        this.transactionsList = ImmutableList.copyOf(transactionsList);
        this.uncleList = ImmutableList.copyOf(uncleList);
        this.sealed = sealed;
    }

    public void seal() {
        this.sealed = true;
        this.header.seal();
    }

    public boolean isSealed() {
        return this.sealed;
    }

    // TODO(mc) remove this method and create a new ExecutedBlock class or similar
    public void setTransactionsList(@Nonnull List<Transaction> transactionsList) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter transaction list");
        }

        this.transactionsList = Collections.unmodifiableList(transactionsList);
        rlpEncoded = null;
    }

    public BlockHeader getHeader() {
        return this.header;
    }

    public Keccak256 getHash() {
        return this.header.getHash();
    }

    public Keccak256 getParentHash() {
        return this.header.getParentHash();
    }

    public byte[] getUnclesHash() {
        return this.header.getUnclesHash();
    }

    public RskAddress getCoinbase() {
        return this.header.getCoinbase();
    }

    public byte[] getStateRoot() {
        return this.header.getStateRoot();
    }

    public void setStateRoot(byte[] stateRoot) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter state root");
        }

        this.header.setStateRoot(stateRoot);
    }

    public byte[] getTxTrieRoot() {
        return this.header.getTxTrieRoot();
    }

    public byte[] getReceiptsRoot() {
        return this.header.getReceiptsRoot();
    }

    public byte[] getLogBloom() {
        return this.header.getLogsBloom();
    }

    public BlockDifficulty getDifficulty() {
        return this.header.getDifficulty();
    }

    public Coin getFeesPaidToMiner() {
        return this.header.getPaidFees();
    }

    public BlockDifficulty getCumulativeDifficulty() {
        BlockDifficulty calcDifficulty = this.header.getDifficulty();
        for (BlockHeader uncle : uncleList) {
            calcDifficulty = calcDifficulty.add(uncle.getDifficulty());
        }
        return calcDifficulty;
    }

    public long getTimestamp() {
        return this.header.getTimestamp();
    }

    public long getNumber() {
        return this.header.getNumber();
    }

    public byte[] getGasLimit() {
        return this.header.getGasLimit();
    }

    public long getGasUsed() {
        return this.header.getGasUsed();
    }

    public byte[] getExtraData() {
        return this.header.getExtraData();
    }

    public List<Transaction> getTransactionsList() {
        return this.transactionsList;
    }

    public List<BlockHeader> getUncleList() {
        return this.uncleList;
    }

    public Coin getMinimumGasPrice() {
        return this.header.getMinimumGasPrice();
    }

    // [parent_hash, uncles_hash, coinbase, state_root, tx_trie_root,
    // difficulty, number, minGasPrice, gasLimit, gasUsed, timestamp,
    // extradata, nonce]

    @Override
    public String toString() {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.append(ByteUtil.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(this.getHash()).append("\n");
        toStringBuff.append(header.toString());

        if (!getUncleList().isEmpty()) {
            toStringBuff.append("Uncles [\n");
            for (BlockHeader uncle : getUncleList()) {
                toStringBuff.append(uncle.toString());
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Uncles []\n");
        }
        if (!getTransactionsList().isEmpty()) {
            toStringBuff.append("Txs [\n");
            for (Transaction tx : getTransactionsList()) {
                toStringBuff.append(tx);
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Txs []\n");
        }
        toStringBuff.append("]");

        return toStringBuff.toString();
    }

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(Block block) {
        return this.header.isParentOf(block.getHeader());
    }

    public boolean isGenesis() {
        return this.header.isGenesis();
    }

    public boolean isEqual(Block block) {
        return this.getHash().equals(block.getHash());
    }

    public boolean fastEquals(Block block) {
        return block != null && this.getHash().equals(block.getHash());
    }

    private byte[] getTransactionsEncoded() {
        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (Transaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    private byte[] getUnclesEncoded() {
        byte[][] unclesEncoded = new byte[uncleList.size()][];
        int i = 0;
        for (BlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getFullEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] header = this.header.getFullEncoded();

            List<byte[]> block = getBodyElements();
            block.add(0, header);
            byte[][] elements = block.toArray(new byte[block.size()][]);

            this.rlpEncoded = RLP.encodeList(elements);
        }

        return rlpEncoded;
    }

    private List<byte[]> getBodyElements() {
        byte[] transactions = getTransactionsEncoded();
        byte[] uncles = getUnclesEncoded();

        List<byte[]> body = new ArrayList<>();
        body.add(transactions);
        body.add(uncles);

        return body;
    }

    public String getPrintableHash() {
        return header.getPrintableHash();
    }

    private String getParentPrintableHash() {
        return header.getParentPrintableHash();
    }

    public String getPrintableHashForMergedMining() {
        return this.header.getPrintableHashForMergedMining();
    }

    public byte[] getHashForMergedMining() {
        return this.header.getHashForMergedMining();
    }

    public String getShortDescr() {
        return "#" + getNumber() + " (" + getPrintableHash() + " <~ "
                + getParentPrintableHash() + ") Txs:" + getTransactionsList().size() +
                ", Unc: " + getUncleList().size();
    }

    public String getHashJsonString() {
        return getHash().toJsonString();
    }

    public String getParentHashJsonString() {
        return getParentHash().toJsonString();
    }

    public byte[] getBitcoinMergedMiningHeader() {
        return this.header.getBitcoinMergedMiningHeader();
    }

    public void setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining header");
        }

        this.header.setBitcoinMergedMiningHeader(bitcoinMergedMiningHeader);
        rlpEncoded = null;
    }

    public byte[] getBitcoinMergedMiningMerkleProof() {
        return this.header.getBitcoinMergedMiningMerkleProof();
    }

    public void setBitcoinMergedMiningMerkleProof(byte[] bitcoinMergedMiningMerkleProof) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining Merkle proof");
        }

        this.header.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleProof);
        rlpEncoded = null;
    }

    public byte[] getBitcoinMergedMiningCoinbaseTransaction() {
        return this.header.getBitcoinMergedMiningCoinbaseTransaction();
    }

    public void setBitcoinMergedMiningCoinbaseTransaction(byte[] bitcoinMergedMiningCoinbaseTransaction) {
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining coinbase transaction");
        }

        this.header.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
        rlpEncoded = null;
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public void flushRLP() {
        this.rlpEncoded = null;
    }
}
