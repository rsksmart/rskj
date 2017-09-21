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

import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Siblings are part of the remasc contract state
 * Sibling information is added to contract state as blocks are processed and removed when no longer needed.
 * @author Oscar Guindzberg
 */
class Sibling {

    // Hash of the sibling block
    private byte[] hash;
    // Coinbase address of the sibling block
    private byte[] coinbase;
    // Fees paid by the sibling block
    private long paidFees;
    // Coinbase address of the block that included the sibling block as uncle
    private byte[] includedBlockCoinbase;
    // Height of the block that included the sibling block as uncle
    private long includedHeight;

    public Sibling(BlockHeader blockHeader, byte[]  includedBlockCoinbase, long includedHeight) {
        this.hash = blockHeader.getHash();
        this.coinbase = blockHeader.getCoinbase();
        this.paidFees = blockHeader.getPaidFees();
        this.includedBlockCoinbase = includedBlockCoinbase;
        this.includedHeight = includedHeight;
    }

    private Sibling(byte[] hash, byte[] coinbase, byte[] includedBlockCoinbase, long paidFees, long includedHeight) {
        this.hash = hash;
        this.coinbase = coinbase;
        this.paidFees = paidFees;
        this.includedBlockCoinbase = includedBlockCoinbase;
        this.includedHeight = includedHeight;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getCoinbase() {
        return coinbase;
    }

    public long getPaidFees() {
        return paidFees;
    }

    public byte[] getIncludedBlockCoinbase() {
        return includedBlockCoinbase;
    }

    public long getIncludedHeight() {
        return includedHeight;
    }

    public byte[] getEncoded() {
        byte[] rlpHash = RLP.encodeElement(this.hash);
        byte[] rlpCoinbase = RLP.encodeElement(this.coinbase);
        byte[] rlpIncludedBlockCoinbase = RLP.encodeElement(this.includedBlockCoinbase);

        byte[] rlpPaidFees = RLP.encodeBigInteger(BigInteger.valueOf(this.paidFees));
        byte[] rlpIncludedHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.includedHeight));

        return RLP.encodeList(rlpHash, rlpCoinbase, rlpIncludedBlockCoinbase, rlpPaidFees, rlpIncludedHeight);
    }

    public static Sibling create(byte[] data) {
        ArrayList<RLPElement> params = RLP.decode2(data);
        RLPList sibling = (RLPList) params.get(0);

        byte[] hash = sibling.get(0).getRLPData();
        byte[] coinbase = sibling.get(1).getRLPData();
        byte[] includedBlockCoinbase = sibling.get(2).getRLPData();

        byte[] bytesPaidFees = sibling.get(3).getRLPData();
        byte[] bytesIncludedHeight = sibling.get(4).getRLPData();

        long paidFees = bytesPaidFees == null ? 0 : BigIntegers.fromUnsignedByteArray(bytesPaidFees).longValue();
        long includedHeight = bytesIncludedHeight == null ? 0 : BigIntegers.fromUnsignedByteArray(bytesIncludedHeight).longValue();

        return new Sibling(hash, coinbase, includedBlockCoinbase, paidFees, includedHeight);
    }
}
