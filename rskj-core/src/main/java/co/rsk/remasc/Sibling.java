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

package co.rsk.remasc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;

/**
 * Siblings are part of the remasc contract state
 * Sibling information is added to contract state as blocks are processed and removed when no longer needed.
 * @author Oscar Guindzberg
 */
public class Sibling {

    // Hash of the sibling block
    private final byte[] hash;
    // Coinbase address of the sibling block
    private final RskAddress coinbase;
    // Fees paid by the sibling block
    private final Coin paidFees;
    // Coinbase address of the block that included the sibling block as uncle
    private final RskAddress includedBlockCoinbase;
    // Height of the block that included the sibling block as uncle
    private final long includedHeight;
    // Number of uncles
    private final int uncleCount;


    public Sibling(BlockHeader blockHeader, RskAddress includedBlockCoinbase, long includedHeight){
        this(blockHeader.getHash().getBytes(),
                blockHeader.getCoinbase(),
                includedBlockCoinbase,
                blockHeader.getPaidFees(),
                includedHeight,
                blockHeader.getUncleCount());
    }

    private Sibling(byte[] hash, RskAddress coinbase, RskAddress includedBlockCoinbase, Coin paidFees, long includedHeight, int uncleCount) {
        this.hash = hash;
        this.coinbase = coinbase;
        this.paidFees = paidFees;
        this.includedBlockCoinbase = includedBlockCoinbase;
        this.includedHeight = includedHeight;
        this.uncleCount = uncleCount;
    }

    public byte[] getHash() {
        return hash;
    }

    public RskAddress getCoinbase() {
        return coinbase;
    }

    public Coin getPaidFees() {
        return paidFees;
    }

    public RskAddress getIncludedBlockCoinbase() {
        return includedBlockCoinbase;
    }

    public long getIncludedHeight() {
        return includedHeight;
    }

    public int getUncleCount() { return uncleCount; }

    public byte[] getEncoded() {
        byte[] rlpHash = RLP.encodeElement(this.hash);
        byte[] rlpCoinbase = RLP.encodeRskAddress(this.coinbase);
        byte[] rlpIncludedBlockCoinbase = RLP.encodeRskAddress(this.includedBlockCoinbase);

        byte[] rlpPaidFees = RLP.encodeCoin(this.paidFees);
        byte[] rlpIncludedHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.includedHeight));
        byte[] rlpUncleCount = RLP.encodeBigInteger(BigInteger.valueOf((this.uncleCount)));

        return RLP.encodeList(rlpHash, rlpCoinbase, rlpIncludedBlockCoinbase, rlpPaidFees, rlpIncludedHeight, rlpUncleCount);
    }

    public static Sibling create(byte[] data) {
        RLPList sibling = RLP.decodeList(data);

        byte[] hash = sibling.get(0).getRLPData();
        RskAddress coinbase = RLP.parseRskAddress(sibling.get(1).getRLPData());
        RskAddress includedBlockCoinbase = RLP.parseRskAddress(sibling.get(2).getRLPData());

        Coin paidFees = RLP.parseCoin(sibling.get(3).getRLPData());
        byte[] bytesIncludedHeight = sibling.get(4).getRLPData();

        RLPElement uncleCountElement = sibling.get(5);
        byte[] bytesUncleCount = uncleCountElement != null? uncleCountElement.getRLPData():null;

        long includedHeight = bytesIncludedHeight == null ? 0 : BigIntegers.fromUnsignedByteArray(bytesIncludedHeight).longValue();
        int uncleCount = bytesUncleCount == null ? 0 : BigIntegers.fromUnsignedByteArray(bytesUncleCount).intValue();

        return new Sibling(hash, coinbase, includedBlockCoinbase, paidFees, includedHeight, uncleCount);
    }
}
