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

package org.ethereum.net.eth.message;

import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

/**
 * Wrapper for Ethereum STATUS message. <br>
 *
 * @see EthMessageCodes#STATUS
 */
public class StatusMessage extends EthMessage {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    protected byte protocolVersion;
    protected int networkId;

    /**
     * Total difficulty of the best chain as found in block header.
     */
    protected byte[] totalDifficulty;
    /**
     * The hash of the best (i.e. highest TD) known block.
     */
    protected byte[] bestHash;
    /**
     * The hash of the Genesis block
     */
    protected byte[] genesisHash;

    public StatusMessage(byte[] encoded) {
        super(encoded);
    }

    public StatusMessage(byte protocolVersion, int networkId,
                         byte[] totalDifficulty, byte[] bestHash, byte[] genesisHash) {
        this.protocolVersion = protocolVersion;
        this.networkId = networkId;
        this.totalDifficulty = totalDifficulty;
        this.bestHash = bestHash;
        this.genesisHash = genesisHash;
        this.parsed = true;
    }

    protected void parse() {
        RLPList paramsList = RLP.decodeList(encoded);

        this.protocolVersion = paramsList.get(0).getRLPData()[0];
        byte[] networkIdBytes = paramsList.get(1).getRLPData();
        this.networkId = networkIdBytes == null ? 0 : ByteUtil.byteArrayToInt(networkIdBytes);

        byte[] diff = paramsList.get(2).getRLPData();
        this.totalDifficulty = (diff == null) ? ZERO_BYTE_ARRAY : diff;
        this.bestHash = paramsList.get(3).getRLPData();
        this.genesisHash = paramsList.get(4).getRLPData();

        parsed = true;
    }

    protected void encode() {
        byte[] protocolVersion = RLP.encodeByte(this.protocolVersion);
        byte[] networkId = RLP.encodeInt(this.networkId);
        byte[] totalDifficulty = RLP.encodeElement(this.totalDifficulty);
        byte[] bestHash = RLP.encodeElement(this.bestHash);
        byte[] genesisHash = RLP.encodeElement(this.genesisHash);

        this.encoded = RLP.encodeList( protocolVersion, networkId,
                totalDifficulty, bestHash, genesisHash);
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    public byte getProtocolVersion() {
        if (!parsed) {
            parse();
        }
        return protocolVersion;
    }

    public int getNetworkId() {
        if (!parsed) {
            parse();
        }
        return networkId;
    }

    public byte[] getTotalDifficulty() {
        if (!parsed) {
            parse();
        }
        return totalDifficulty;
    }

    public BigInteger getTotalDifficultyAsBigInt() {
        return new BigInteger(1, getTotalDifficulty());
    }

    public byte[] getBestHash() {
        if (!parsed) {
            parse();
        }
        return bestHash;
    }

    public byte[] getGenesisHash() {
        if (!parsed) {
            parse();
        }
        return genesisHash;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.STATUS;
    }


    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        return "[" + this.getCommand().name() +
                " protocolVersion=" + this.protocolVersion +
                " networkId=" + this.networkId +
                " totalDifficulty=" + ByteUtil.toHexStringOrEmpty(this.totalDifficulty) +
                " bestHash=" + ByteUtil.toHexString(this.bestHash) +
                " genesisHash=" + ByteUtil.toHexString(this.genesisHash) +
                "]";
    }
}
