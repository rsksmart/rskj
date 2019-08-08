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

package co.rsk.net.messages;

import org.ethereum.core.BlockIdentifier;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.crypto.Keccak256Helper.DEFAULT_SIZE_BYTES;
import static org.ethereum.util.ByteUtil.byteArrayToInt;
import static org.ethereum.util.ByteUtil.byteArrayToLong;

/**
 * Wrapper around an Ethereum GetBlockHeaders message on the network
 *
 * @see EthMessageCodes#GET_BLOCK_HEADERS
 *
 * @author Mikhail Kalinin
 * @since 04.09.2015
 */
public class GetBlockHeadersMessage extends Message {

    /**
     * Block number from which to start sending block headers
     */
    private long blockNumber;

    /**
     * Block hash from which to start sending block headers <br>
     * Initial block can be addressed by either {@code blockNumber} or {@code blockHash}
     */
    private byte[] blockHash;

    /**
     * The maximum number of headers to be returned. <br>
     * <b>Note:</b> the peer could return fewer.
     */
    private int maxHeaders;

    /**
     * The number of skipped blocks starting from initial block. <br>
     * Direction depends on {@code reverse} param.
     */
    private int skipBlocks;

    /**
     * The direction of headers enumeration. <br>
     * <b>false</b> is for rising block numbers. <br>
     * <b>true</b> is for falling block numbers.
     */
    private boolean reverse;

    // TODO(mvanotti): We should move these vars to Message.
    private byte[] encoded;
    private boolean parsed;


    public GetBlockHeadersMessage(long blockNumber, int maxHeaders) {
        this(blockNumber, null, maxHeaders, 0, false);
    }

    public GetBlockHeadersMessage(byte[] blockHash, int maxHeaders) {
        this(0, blockHash, maxHeaders, 0, false);
    }

    public GetBlockHeadersMessage(byte[] encoded) {
        this.encoded = encoded;
        this.parsed = false;
    }

    public GetBlockHeadersMessage(long blockNumber, byte[] blockHash, int maxHeaders, int skipBlocks, boolean reverse) {
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.maxHeaders = maxHeaders;
        this.skipBlocks = skipBlocks;
        this.reverse = reverse;

        parsed = true;
        encode();
    }

    private void encode() {
        byte[] maxHeaders  = RLP.encodeInt(this.maxHeaders);
        byte[] skipBlocks = RLP.encodeInt(this.skipBlocks);
        byte[] reverse  = RLP.encodeByte((byte) (this.reverse ? 1 : 0));

        if (this.blockHash != null) {
            byte[] hash = RLP.encodeElement(this.blockHash);
            this.encoded = RLP.encodeList(hash, maxHeaders, skipBlocks, reverse);
        } else {
            byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
            this.encoded = RLP.encodeList(number, maxHeaders, skipBlocks, reverse);
        }
    }

    private void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        byte[] blockBytes = paramsList.get(0).getRLPData();

        // it might be either a hash or number
        if (blockBytes == null) {
            this.blockNumber = 0;
        } else if (blockBytes.length == DEFAULT_SIZE_BYTES) {
            this.blockHash = blockBytes;
        } else {
            this.blockNumber = byteArrayToLong(blockBytes);
        }

        byte[] maxHeaders = paramsList.get(1).getRLPData();
        this.maxHeaders = byteArrayToInt(maxHeaders);

        byte[] skipBlocks = paramsList.get(2).getRLPData();
        this.skipBlocks = byteArrayToInt(skipBlocks);

        byte[] reverse = paramsList.get(3).getRLPData();
        this.reverse = byteArrayToInt(reverse) == 1;

        parsed = true;
    }

    public long getBlockNumber() {
        if (!parsed) {
            parse();
        }

        return blockNumber;
    }

    public byte[] getBlockHash() {
        if (!parsed) {
            parse();
        }

        return blockHash;
    }

    public BlockIdentifier getBlockIdentifier() {
        if (!parsed) {
            parse();
        }

        return new BlockIdentifier(blockHash, blockNumber);
    }

    public int getMaxHeaders() {
        if (!parsed) {
            parse();
        }

        return maxHeaders;
    }

    public int getSkipBlocks() {
        if (!parsed) {
            parse();
        }

        return skipBlocks;
    }

    public boolean isReverse() {
        if (!parsed) {
            parse();
        }

        return reverse;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_BLOCK_HEADERS_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        if (encoded == null) {
            encode();
        }

        return encoded;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        
        return "[" + getMessageType() +
                " blockNumber=" + String.valueOf(blockNumber) +
                " blockHash=" + ByteUtil.toHexString(blockHash) +
                " maxHeaders=" + maxHeaders +
                " skipBlocks=" + skipBlocks +
                " reverse=" + reverse + "]";
    }
}
